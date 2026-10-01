package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * One device the platform announced changed, between snapshots.
 *
 * The shape is a union of two reports rather than one wide record because the two arrive from
 * different mechanisms with different payloads: a link announcement names a profile and a state, a
 * bond announcement names neither. Forcing them into one type would put a field in the model that no
 * producer can fill, which is the DEDUPED mistake ADR-P2-018 refused: vocabulary standing in for a
 * capability.
 *
 * An announcement carries no display name. The platform's name is a cache read that scanning fills,
 * and Phase 3 does not scan, so a cold cache is the normal case rather than the exception
 * (research section 5.1). A record that gained a name here would have a field whose absence looked
 * like a fact about the device.
 */
sealed interface DeviceConnectionEvent {
    /** What the platform gave us to attribute this announcement to. */
    val key: DeviceObservationKey

    /** When the announcement was made, or null when the platform supplied no reading. */
    val observedAtEpochMillis: Long?

    /**
     * A link transition, from the profile that reported it, or from no profile at all.
     *
     * [profile] is null for the link-layer announcements, which are the closest thing to a per-device
     * event the public surface offers and which name no service. That is not the same as "every
     * profile": the engine treats a link-layer disconnect as ending every reported profile, because a
     * service connection rides the link, and that is the one inference this layer makes - it is about
     * the transport hierarchy, not about the device.
     */
    data class LinkChanged(
        override val key: DeviceObservationKey,
        val profile: ObservedProfile?,
        val link: DeviceConnectionState,
        override val observedAtEpochMillis: Long?,
    ) : DeviceConnectionEvent

    /**
     * A pairing transition. It says nothing about the link, and prompt section 6 forbids the reading
     * that it does: a device can be bonded and disconnected, and a device can be connected without
     * ever having been bonded.
     */
    data class BondChanged(
        override val key: DeviceObservationKey,
        val bond: DeviceBondState,
        override val observedAtEpochMillis: Long?,
    ) : DeviceConnectionEvent
}

/** A live device-announcement stream together with the handle that owns the platform registration. */
interface ConnectedDeviceEventChannel {
    val registration: PlatformRegistration

    /**
     * Emitted when the platform announces a change about one device.
     *
     * Cold, on the same contract as [AdapterStateChangeChannel.states]: opening it registers, and
     * losing the collector must unregister. Duplicates are the observer's problem, and it treats them
     * as idempotent rather than as a defect, because broadcast delivery promises neither uniqueness
     * nor order.
     */
    val events: Flow<DeviceConnectionEvent>
}

/**
 * The platform seam the connected-device observer reads through.
 *
 * It exists so that everything hard about device observation - reconciliation, dedupe, refusal,
 * invalidation - lives in `:core` and can be tested without a radio, exactly as
 * [AdapterStateSource] does for the adapter (ADR-P3-003, architecture audit section 5).
 *
 * The interesting property is what this interface makes impossible. ADR-P3-009's finding is that the
 * platform answers "you are not allowed to look" and "nobody is connected" with the same empty
 * collection on every enumeration path, and that no amount of catching `SecurityException` fixes it,
 * because the modern platform does not throw. That cannot be enforced by a rule written in this
 * file's prose, so it is not written there: [snapshot] returns an [ObservationRound], which has no
 * representation for "a list, from a round that was not allowed to produce one". A source that has
 * not settled permission standing for the operation it is about to perform - through Phase 2's
 * resolver and provider, before enumerating and before registering - returns [ObservationRound.Failure]
 * and the engine never learns a device list it should not have asked for. Ordering is the mechanism,
 * and the type is what makes the ordering the only option.
 */
interface ConnectedDeviceSource {
    /**
     * Which profiles this platform could answer for at all.
     *
     * Asked once per observation, before the snapshot, because the answer changes what an empty
     * device list means: a union over profiles that all declined is not a report of an empty room
     * (ADR-P3-008). A refusal to answer here is a [OperationOutcome.Failure], not an empty report -
     * an empty report is a real answer about a handset with no profiles, and the two must not be the
     * same value.
     */
    suspend fun profileSupport(): OperationOutcome<ProfileSupportReport>

    /**
     * Take one reconciled snapshot of the devices the platform reports, unioned over its profile list.
     *
     * The [ObservationRound.Success.stage] a source sets here is ignored: lifecycle belongs to the
     * observer, and a source that claimed to know whether an observation was running would be
     * reporting a fact it cannot see (ADR-P3-005).
     *
     * An empty list inside a [ObservationRound.Success] means the union completed and no device is
     * reported. A source may only return that after its standing check passed, and it must not
     * substitute an empty list for a refusal it could not distinguish - which is the case
     * [profileSupport] exists to break.
     */
    suspend fun snapshot(): ObservationRound<DeviceObservation>

    /**
     * Open the live announcement stream and return its registration handle.
     *
     * The registration is handed back so the owner can prove the teardown happened even if the stream
     * was never collected to completion, and because a profile binding that outlives its observer is a
     * resource Phase 3 cannot reclaim: obtaining one is asynchronous binding with an explicit failure
     * and no error code, so the cleanup obligation is real and its idempotence is a requirement
     * (ADR-P3-008's consequence, research section 7.1).
     */
    suspend fun openConnectionEvents(): OperationOutcome<ConnectedDeviceEventChannel>

    companion object {
        /** A source that can answer nothing, for tests and for platforms with no Bluetooth at all. */
        fun unavailable(): ConnectedDeviceSource = UnavailableConnectedDeviceSource
    }
}

/**
 * A source whose every question is refused.
 *
 * Present so that a composition root with no device-facing platform mechanism can inject *something*
 * without inventing a nullable seam, and its answers are the ones the rest of the system already
 * understands: `ADAPTER_UNAVAILABLE` with a re-read retry class, never an empty device list - a
 * refusal that reported zero devices would be the exact confusion ADR-P3-009 was written to close,
 * arriving from the one place that is supposed to be honest about not looking.
 */
private object UnavailableConnectedDeviceSource : ConnectedDeviceSource {
    override suspend fun profileSupport(): OperationOutcome<ProfileSupportReport> =
        OperationOutcome.Failure(refusal())

    override suspend fun snapshot(): ObservationRound<DeviceObservation> = ObservationRound.Failure(refusal())

    override suspend fun openConnectionEvents(): OperationOutcome<ConnectedDeviceEventChannel> =
        OperationOutcome.Failure(refusal())

    private fun refusal(): OmniBudsError = OmniBudsError(
        category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
        operationId = "device-source.unavailable",
        detail = "no connected-device source was available",
    )
}
