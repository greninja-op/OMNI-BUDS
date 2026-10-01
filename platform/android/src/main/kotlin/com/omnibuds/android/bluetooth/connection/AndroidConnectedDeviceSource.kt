package com.omnibuds.android.bluetooth.connection

import com.omnibuds.android.bluetooth.SystemTimeProvider
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.capability.TargetSdkProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.ApiAvailability
import com.omnibuds.core.platform.BluetoothOperation
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.BondedDeviceObservation
import com.omnibuds.core.platform.ConnectedDeviceEventChannel
import com.omnibuds.core.platform.ConnectedDeviceSource
import com.omnibuds.core.platform.DeviceAvailability
import com.omnibuds.core.platform.DeviceBondState
import com.omnibuds.core.platform.DeviceConnectionEvent
import com.omnibuds.core.platform.DeviceConnectionState
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationArrival
import com.omnibuds.core.platform.ObservationRound
import com.omnibuds.core.platform.ObservationStage
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.PermissionContext
import com.omnibuds.core.platform.PermissionState
import com.omnibuds.core.platform.PlatformRegistration
import com.omnibuds.core.platform.ProfileObservationSupport
import com.omnibuds.core.platform.ProfileSupportReport
import com.omnibuds.core.platform.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

/**
 * The Android implementation of [ConnectedDeviceSource], and the place Phase 3's ordering rule lives.
 *
 * ADR-P3-009 is the whole design of this class, and it is a rule about *sequence* rather than about
 * error handling: the platform answers "you are not allowed to look" and "nobody is connected" with the
 * same empty collection on every enumeration path, so no amount of catching makes the two separable
 * after the fact. The only thing that separates them is that this class asks the permission question
 * before it asks the device question, in all three entry points, and returns a refusal with no device
 * list attached when the answer is anything other than a grant.
 *
 * What that ordering buys, stated precisely:
 *  - [profileSupport] settles standing, then reports which profiles could answer at all. A profile the
 *    running OS does not expose and a profile whose binding was refused both report
 *    [ProfileObservationSupport.NOT_ANSWERABLE]; a profile still waiting for its callback reports
 *    [ProfileObservationSupport.UNKNOWN], which is what [com.omnibuds.core.platform.ProfileSupportReport]
 *    declares that silence is for. Neither is reported as a declined question about devices.
 *  - [snapshot] settles standing, then confirms there is an adapter to ask, then enumerates only the
 *    profiles that answered. A profile that has not answered is never asked, so an empty union can only
 *    ever mean "the mechanisms spoke and named nobody" - and where nothing answered, the engine's own
 *    empty-union rule (ADR-P3-015 rule 7) is what refuses the round.
 *  - [openConnectionEvents] settles standing before registering anything, because a receiver the user
 *    has not let this app hear produces exactly one symptom - silence - and a silent stream is the
 *    failure this phase refuses everywhere else.
 *  - [bondedDevices] settles standing for `device.bonded-list-inspection` on its own terms before the
 *    bond list is read, and refuses four different platform situations as four refusals rather than as an
 *    empty list of paired devices. The platform's own documentation for the read says it "will return an
 *    empty set" whenever the adapter is not on, so this is the same hazard ADR-P3-009 names, arriving from
 *    the pairing side (ADR-P3-017).
 *
 * All platform work is offloaded to [dispatcher]: `getProfileProxy`, the profile enumerations and
 * receiver registration are binder calls, and blocking the main thread on them is a defect rather than a
 * performance note (Phase 2 prompt section 5.8). Nothing here waits, retries or polls; a bind that has
 * not answered is reported as not answered, and the next round asks again
 * ([SystemConnectedDeviceHandle] states why the platform's own documentation forbids the alternative).
 *
 * A thrown platform call becomes [OmniBudsErrorCategory.PLATFORM_EXCEPTION] with the exception *class*
 * named and nothing else. The message is not quoted, because a framework exception text can carry a
 * device address, and a round that failed this way is a failure rather than an empty list (SEC-LOG-001,
 * ADR-P3-005).
 */
class AndroidConnectedDeviceSource(
    private val handle: ConnectedDeviceHandle,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val apiLevel: ApiLevelProvider,
    private val targetSdk: TargetSdkProvider,
    private val time: TimeProvider = SystemTimeProvider,
    private val enumeratedProfiles: List<ObservedProfile> = ObservedProfile.enumerationUnion,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ConnectedDeviceSource {

    override suspend fun profileSupport(): OperationOutcome<ProfileSupportReport> = withContext(dispatcher) {
        refusalFor(BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION)?.let { refusal ->
            return@withContext OperationOutcome.Failure(refusal)
        }
        runCatching {
            val level = apiLevel.apiLevel()
            val entries = enumeratedProfiles.associateWith { profile -> supportFor(profile, level) }
            OperationOutcome.Success(ProfileSupportReport(entries))
        }.getOrElse { problem ->
            OperationOutcome.Failure(platformFailure("profile-support", problem))
        }
    }

    override suspend fun snapshot(): ObservationRound<DeviceObservation> = withContext(dispatcher) {
        refusalFor(BluetoothOperation.CONNECTED_DEVICE_INSPECTION)?.let { refusal ->
            return@withContext ObservationRound.Failure(refusal)
        }
        // An adapter that is not there is not a room full of disconnected devices. `getBondedDevices` and
        // the profile enumerations both answer empty in this situation, which is precisely the confusion
        // ADR-P3-009 was written to close, so the round is refused with the category that already means
        // "the phone itself did not answer". The bond list says the same thing in code now, through
        // [bondedDevices] and [ConnectedDeviceHandle.bondedDevices]; this round keeps its own refusal
        // because the two questions still have separate answers.
        if (!handle.adapterPresent) {
            return@withContext ObservationRound.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = OPERATION_ID,
                    detail = "this phone reports no Bluetooth adapter, so no profile could be asked about any device",
                ),
            )
        }
        runCatching {
            // The stage is restated by the observer and ignored there, and this one is no claim: a
            // source cannot see whether an observation is running, only whether this round answered
            // (ADR-P3-015 rule 10).
            ObservationRound.Success(devices = unionOf(enumeratedProfiles), stage = ObservationStage.OBSERVING)
        }.getOrElse { problem ->
            ObservationRound.Failure(platformFailure("device-snapshot", problem))
        }
    }

    /**
     * Reads the phone's pairing records, gated on its own operation and refused into four shapes.
     *
     * This is the fourth entry point, and the one that makes prompt section 10.B exist as data rather than
     * as a field on a model nobody can fill. It repeats [snapshot]'s ordering rule rather than borrowing its
     * answer: standing is settled for `device.bonded-list-inspection`, which is the operation this read
     * performs, and only then is the handle touched. A grant for the connected-device inspection is not a
     * grant for this one and the resolver is asked separately, because that is the only way the plan for
     * each operation stays the thing being honoured (ADR-P3-009, ADR-P3-017).
     *
     * Three of [BondedListing]'s cases become refusals with categories the model already has, and none of
     * them becomes an empty list:
     *  - [BondedListing.AdapterAbsent] is `ADAPTER_UNAVAILABLE`, for the reason [snapshot] states at its own
     *    guard - a phone with no radio is not a phone with no pairings.
     *  - [BondedListing.AdapterNotOn] is `BLUETOOTH_DISABLED`, and this one is not caution but quotation:
     *    the shipped documentation for the call says that when the adapter state is not on, "this API will
     *    return an empty set". Reporting zero paired devices to a user whose Bluetooth is toggled off would
     *    be ADR-P3-009's confusion arriving through the door the bond list opened.
     *  - [BondedListing.ReadFailed] is `RESOURCE_UNAVAILABLE`, because the platform answered with the value
     *    its own contract reserves for "an error happened" (a null set, or a call that threw) and gave
     *    nothing to attribute it to. `RESOURCE_UNAVAILABLE` is ADR-P3-006's category for a mechanism that
     *    could not be held on to, and its retry class says re-read, which is exactly what the next round does.
     *
     * Only [BondedListing.Reported] can produce an empty list, and it is reachable only when an adapter was
     * present, was reading itself as on, and answered with a set - which is when emptiness is a census
     * rather than a symptom.
     */
    override suspend fun bondedDevices(): ObservationRound<BondedDeviceObservation> = withContext(dispatcher) {
        refusalFor(BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION)?.let { refusal ->
            return@withContext ObservationRound.Failure(refusal)
        }
        runCatching {
            when (val listing = handle.bondedDevices()) {
                is BondedListing.Reported -> ObservationRound.Success(
                    devices = listing.devices.map { report -> pairedObservationOf(report) },
                    // Restated by the observer and ignored there, for the same reason [snapshot] sets a
                    // stage it does not claim (ADR-P3-015 rule 10).
                    stage = ObservationStage.OBSERVING,
                )

                BondedListing.AdapterAbsent -> ObservationRound.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                        operationId = OPERATION_ID,
                        detail = "this phone reports no Bluetooth adapter, so there is no bond list to read; " +
                            "an absent adapter is not a phone with no pairings",
                    ),
                )

                BondedListing.AdapterNotOn -> ObservationRound.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.BLUETOOTH_DISABLED,
                        operationId = OPERATION_ID,
                        detail = "the adapter is not reporting itself on, and the bond-list read answers an " +
                            "empty set for every phone in that state, so nothing was counted",
                    ),
                )

                BondedListing.ReadFailed -> ObservationRound.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = OPERATION_ID,
                        detail = "the bond-list read returned the platform's error answer rather than a set, " +
                            "which is a failed observation and not a report of no paired devices",
                    ),
                )
            }
        }.getOrElse { problem ->
            ObservationRound.Failure(platformFailure("bonded-device-list", problem))
        }
    }

    /**
     * Opens the announcement stream and the profile bindings with it.
     *
     * The registration handed back releases both, which is the point: [ConnectedDeviceSource]'s contract
     * is that the caller can prove the teardown happened even when the stream was never collected, and a
     * bound proxy left behind by a half-opened channel is a resource this phase cannot reclaim later
     * (ADR-P3-008's consequence). The channel is unbounded rather than conflated because announcements
     * are transitions, not a latest-wins value - dropping a disconnect to keep up would lose a device's
     * link, and the volume is a handful of earbuds rather than a stream.
     */
    override suspend fun openConnectionEvents(): OperationOutcome<ConnectedDeviceEventChannel> =
        withContext(dispatcher) {
            refusalFor(BluetoothOperation.CONNECTED_DEVICE_INSPECTION)?.let { refusal ->
                return@withContext OperationOutcome.Failure(refusal)
            }
            val announcements = Channel<DeviceConnectionEvent>(Channel.UNLIMITED)
            val emission: (DeviceAnnouncement) -> Unit = { announcement ->
                announcements.trySend(eventOf(announcement))
            }
            runCatching { handle.openAnnouncements(enumeratedProfiles, emission) }
                .fold(
                    onSuccess = { registration ->
                        OperationOutcome.Success(BufferedDeviceEventChannel(registration, announcements))
                    },
                    onFailure = { problem ->
                        announcements.close()
                        OperationOutcome.Failure(platformFailure("device-events", problem))
                    },
                )
        }

    /**
     * The union this phase can lawfully claim, folded by key.
     *
     * One address named by two profiles becomes one record carrying both names, which is prompt section
     * 12's requirement and the only way a drop on one service can avoid disconnecting a device another
     * still holds. Records the platform gave no key for stay separate and are appended after the keyed
     * ones: joining them to each other would weld two unidentifiable devices into one, which is the same
     * defect arriving from the other direction (ADR-P3-010, ADR-P3-015 rule 6).
     */
    private fun unionOf(profiles: List<ObservedProfile>): List<DeviceObservation> {
        val keyed = LinkedHashMap<DeviceObservationKey, DeviceObservation>()
        val unkeyed = mutableListOf<DeviceObservation>()
        for (profile in profiles) {
            // Asked before enumerated, and a profile that has not answered is not asked. This is the
            // per-profile form of the same ordering the whole class is built on: a mechanism that could
            // not speak has no part in what an empty list means.
            if (handle.answerability(profile) != ProfileAnswerability.ANSWERABLE) continue
            val enumeration = handle.enumerate(profile)
            if (enumeration !is ProfileEnumeration.Reported) continue
            for (report in enumeration.devices) {
                val observation = observationOf(report, profile)
                val key = observation.key
                if (!key.canIdentifyAcrossObservations) {
                    unkeyed += observation
                    continue
                }
                val previous = keyed[key]
                keyed[key] = previous?.mergedWith(observation) ?: observation
            }
        }
        return keyed.values.toList() + unkeyed
    }

    private fun observationOf(report: DeviceReport, profile: ObservedProfile): DeviceObservation =
        DeviceObservation.reported(
            key = DeviceObservationKey.ofReportedAddress(report.reportedAddress),
            link = linkStateOf(report.link),
            bond = bondStateOf(report.bond),
            // The platform named this device in an enumeration that answered, which is what "would tell
            // us about it" means. A record read through a profile that declined is never produced at all,
            // so this is never a claim of presence built on a refusal.
            availability = DeviceAvailability.AVAILABLE,
            observedProfiles = setOf(profile),
            displayName = report.reportedName,
            arrival = ObservationArrival.SNAPSHOT,
            observedAtEpochMillis = time.nowEpochMillis(),
        )

    /**
     * One paired device, mapped into a record that cannot claim a link.
     *
     * The bond axis comes from the device object's own reading rather than from the list's membership, so
     * a phone mid-unbond that still names the device reports what the device says instead of what the
     * collection implies. Nothing here is invented to fill the gap the type leaves: there is no link, no
     * profile set and no availability, because the mechanism reported none of them.
     */
    private fun pairedObservationOf(report: BondedDeviceReport): BondedDeviceObservation =
        BondedDeviceObservation.reported(
            key = DeviceObservationKey.ofReportedAddress(report.reportedAddress),
            bond = bondStateOf(report.bond),
            displayName = report.reportedName,
            observedAtEpochMillis = time.nowEpochMillis(),
        )

    private fun eventOf(announcement: DeviceAnnouncement): DeviceConnectionEvent = when (announcement) {
        is DeviceAnnouncement.Link -> DeviceConnectionEvent.LinkChanged(
            key = DeviceObservationKey.ofReportedAddress(announcement.reportedAddress),
            profile = announcement.profile,
            link = linkStateOf(announcement.link),
            // The broadcast carries no timestamp of its own, so the reading is the moment the platform
            // reached us, or null when the clock gave nothing. A zero here would sort an unknown
            // announcement to the epoch and make it look like the oldest event in the projection.
            observedAtEpochMillis = time.nowEpochMillis(),
        )

        is DeviceAnnouncement.Bond -> DeviceConnectionEvent.BondChanged(
            key = DeviceObservationKey.ofReportedAddress(announcement.reportedAddress),
            bond = bondStateOf(announcement.bond),
            observedAtEpochMillis = time.nowEpochMillis(),
        )
    }

    /**
     * Whether the running OS exposes [profile], then whether the mechanism could answer for it.
     *
     * In that order, because the first question makes the second moot: asking a handset's Bluetooth app
     * for a service the platform version has no class for is a guaranteed refusal, and reporting it as
     * the platform's answer about *this* handset would overstate what was learned.
     */
    private fun supportFor(profile: ObservedProfile, level: Int): ProfileObservationSupport =
        when (profile.availabilityAt(level)) {
            ApiAvailability.UNAVAILABLE -> ProfileObservationSupport.NOT_ANSWERABLE
            ApiAvailability.UNKNOWN -> ProfileObservationSupport.UNKNOWN
            ApiAvailability.AVAILABLE -> when (handle.answerability(profile)) {
                ProfileAnswerability.ANSWERABLE -> ProfileObservationSupport.ANSWERABLE
                ProfileAnswerability.REFUSED -> ProfileObservationSupport.NOT_ANSWERABLE
                // Not yet requested, or requested and still in flight. Neither is the platform having
                // declined, and the distinction is what lets a later round answer without rewriting this
                // one (ADR-P3-015 rule 5's counterpart on the support side).
                ProfileAnswerability.NOT_REQUESTED -> ProfileObservationSupport.UNKNOWN
                ProfileAnswerability.AWAITING_CALLBACK -> ProfileObservationSupport.UNKNOWN
            }
        }

    /**
     * The refusal that precedes every platform question, or null when the operation is cleared.
     *
     * Only a grant clears it. [PermissionState.UNKNOWN], [PermissionState.NOT_REQUESTED] and
     * [PermissionState.DENIED] all refuse, and ADR-P3-009 is explicit that refusing on `UNKNOWN` is the
     * point rather than an over-caution: refusing to look is honest where reporting zero is not.
     * [PermissionState.NOT_REQUIRED] clears only when it is the whole answer for the operation, which no
     * Phase 3 operation can produce - the frozen resolver requires `BLUETOOTH_CONNECT` at target 31 and
     * above and legacy `BLUETOOTH` below it for every device-facing row - so the clause is the resolver's
     * judgement being respected rather than a licence this source grants itself.
     *
     * A permission provider that throws is treated as a refusal, not as a platform exception: with the
     * standing unknown, the ordering rule cannot be honoured, and the safe side of that ambiguity is
     * not looking.
     */
    private fun refusalFor(operation: BluetoothOperation): OmniBudsError? {
        val cleared = runCatching { isClearedFor(operation) }.getOrDefault(false)
        if (cleared) return null
        return OmniBudsError(
            category = OmniBudsErrorCategory.PERMISSION_DENIED,
            operationId = OPERATION_ID,
            detail = "permission standing for ${operation.technicalName} was not a grant, so no device " +
                "enumeration and no receiver registration was attempted; a refusal is not a report of " +
                "no connected devices",
        )
    }

    private fun isClearedFor(operation: BluetoothOperation): Boolean {
        val context = PermissionContext(
            targetSdk = targetSdk.targetSdk(),
            deviceSdk = apiLevel.apiLevel(),
        )
        val connect = permissionProvider.stateFor(operation, BluetoothPermission.BLUETOOTH_CONNECT, context)
        if (connect == PermissionState.GRANTED) return true
        val legacy = permissionProvider.stateFor(operation, BluetoothPermission.BLUETOOTH, context)
        return connect == PermissionState.NOT_REQUIRED &&
            (legacy == PermissionState.GRANTED || legacy == PermissionState.NOT_REQUIRED)
    }

    private fun platformFailure(what: String, problem: Throwable): OmniBudsError = OmniBudsError(
        category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
        operationId = OPERATION_ID,
        detail = "the $what reported ${problem::class.simpleName}",
    )

    private companion object {
        const val OPERATION_ID = "android-connected-device-source"

        fun linkStateOf(raw: RawLinkState): DeviceConnectionState = when (raw) {
            RawLinkState.CONNECTED -> DeviceConnectionState.CONNECTED
            RawLinkState.CONNECTING -> DeviceConnectionState.CONNECTING
            RawLinkState.DISCONNECTING -> DeviceConnectionState.DISCONNECTING
            RawLinkState.DISCONNECTED -> DeviceConnectionState.DISCONNECTED
            // The platform's own unread value stays unread. Reading it as DISCONNECTED would turn a
            // broadcast that arrived without a state extra into an announcement that a link went down.
            RawLinkState.UNREADABLE -> DeviceConnectionState.UNKNOWN
        }

        fun bondStateOf(raw: RawBondState): DeviceBondState = when (raw) {
            RawBondState.BONDED -> DeviceBondState.BONDED
            RawBondState.BONDING -> DeviceBondState.BONDING
            RawBondState.NONE -> DeviceBondState.NONE
            RawBondState.UNREADABLE -> DeviceBondState.UNKNOWN
        }
    }
}

/**
 * A live announcement stream whose disposal also ends the stream.
 *
 * The registration handed to the caller is [ClosingRegistration] rather than the handle's own, so a
 * caller that tears the registration down cannot leave a collector suspended on a receiver and a profile
 * service that have both already gone away.
 */
private class BufferedDeviceEventChannel(
    registration: PlatformRegistration,
    private val announcements: Channel<DeviceConnectionEvent>,
) : ConnectedDeviceEventChannel {
    override val registration: PlatformRegistration = ClosingRegistration(registration, announcements)

    override val events: Flow<DeviceConnectionEvent> = announcements.receiveAsFlow()
}

/**
 * Disposes the platform registration and closes the stream it feeds.
 *
 * Safe to call twice, which the observer relies on because it disposes in a `finally` and a cancellation
 * path may have disposed already. The registration's own problem is allowed to propagate - a teardown
 * that failed has to surface, since [com.omnibuds.core.platform.ConnectedDeviceObserver] records it as
 * `teardownProblem` - and the channel is closed *before* that, because a stream left open on a half
 * released registration is a collector waiting on a receiver nobody can answer.
 */
private class ClosingRegistration(
    private val registration: PlatformRegistration,
    private val announcements: Channel<DeviceConnectionEvent>,
) : PlatformRegistration {
    override val isActive: Boolean
        get() = registration.isActive

    override suspend fun dispose() {
        val problem = runCatching { registration.dispose() }.exceptionOrNull()
        announcements.close()
        problem?.let { throw it }
    }
}
