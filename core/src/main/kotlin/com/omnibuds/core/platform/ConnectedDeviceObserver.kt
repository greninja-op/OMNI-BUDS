package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Why the observer declined to apply a report, kept as a record rather than as a dropped line.
 *
 * Prompt section 11's instruction is to handle awkward events idempotently and not to swallow them, and
 * there are two ways to swallow a report: apply it anyway, or ignore it. A refusal says which platform
 * claim was refused, what the projection kept instead, and why - the "why" for
 * [RefusalKind.IMPOSSIBLE_TRANSITION] coming from [DeviceConnectionStateTransitions.refusalReason]
 * rather than from a paraphrase that could disagree with the table.
 */
enum class RefusalKind {
    /** The move is one the platform cannot report, so the state the observer held is still held. */
    IMPOSSIBLE_TRANSITION,

    /**
     * The report named no device.
     *
     * Nothing was retained and nothing was applied, because there was no record to attribute it to -
     * and merging it with the other unkeyed reports would be prompt section 12's prohibited action, so
     * the refusal is the only honest output available.
     */
    UNATTRIBUTABLE_REPORT,
}

/**
 * One refused report, as a fact about the observation.
 *
 * [retained] is null when there was no record to retain, and [attempted] is null when the refused
 * report carried no link state at all. Both are stated as null rather than as a plausible state, so a
 * consumer cannot read an absence as a value (ADR-P0-016).
 */
data class RefusedDeviceTransition(
    val key: DeviceObservationKey,
    val kind: RefusalKind,
    val retained: DeviceConnectionState?,
    val attempted: DeviceConnectionState?,
    val reason: String,
    val refusedAtEpochMillis: Long?,
)

/**
 * What the observer currently holds about which devices exist and how they are linked.
 *
 * This is the projection prompt section 14 sketches, and ADR-P3-004 is the reason it is not called a
 * repository: it stores nothing, persists nothing, keeps nothing across a process death and owns no
 * cache. It is the shape of the answer the platform last gave, plus the two caveats without which that
 * answer would overclaim - [unansweredProfiles], because a union over silent profiles is not a census,
 * and [refusedTransitions], because a report the engine could not apply is information rather than
 * nothing.
 *
 * It holds prompt section 10's two collections as two answers, not as one list with a flag: [records]
 * and [connectedDevices] are collection A, what the link mechanisms report, and [pairedDevices] is
 * collection B, what the bond list named. They are different types, they arrive from different reads,
 * they are gated on different operations, and one round's refusal cannot empty the other's answer. A
 * device in both is one device reported twice about two different facts, and [DeviceObservationKey] is
 * the only thing that says they are the same device (ADR-P3-010).
 */
data class ConnectedDeviceSnapshot(
    /** Lifecycle only. A failure reason never appears here (ADR-P3-005). */
    val stage: ObservationStage,

    /** Every record the platform has reported and not forgotten, connected or not. */
    val records: List<DeviceObservation>,

    /**
     * Collection B: the devices the platform's bond list named in the last paired round that answered.
     *
     * These may be disconnected and are never presented as active connections - the records carry no link
     * field to present them with (prompt section 6, ADR-P3-001). A paired-only device is not in [records]
     * either, so nothing has to remember to filter one out of [connectedDevices].
     *
     * Restated by each paired round and never accumulated: this is the phone's live bond list rather than
     * a history, and prompt sections 10.C and 16 forbid this phase keeping a copy of it.
     */
    val pairedDevices: List<BondedDeviceObservation>,

    /**
     * Profiles in the maintained union that could not answer.
     *
     * Their silence is not evidence of absence, which is what lets "no device is connected" be said
     * honestly rather than by accident (ADR-P3-008).
     */
    val unansweredProfiles: Set<ObservedProfile>,

    /** Whether [unansweredProfiles] is the platform's answer or still the observer's opening default. */
    val profileSupportConsulted: Boolean,

    /**
     * Whether the records here came out of a union that completed.
     *
     * Announcements can populate a projection before any round has finished, and a refusal leaves it as
     * it found it, so this is not the same question as [profileSupportConsulted] - and without it, a
     * projection that has only ever been refused would report itself as a completed census of nobody.
     */
    val restsOnCompletedRound: Boolean,

    /**
     * Whether [pairedDevices] came out of a paired round that answered.
     *
     * The paired question and the connected question have separate standings and separate answers, so this
     * is not derivable from [restsOnCompletedRound]: a projection can hold a completed link census and a
     * refused bond list in the same instant, and the empty [pairedDevices] that goes with the second is
     * not a finding about anybody's pairings (ADR-P3-009). A later refusal does not withdraw it either, for
     * the same reason [restsOnCompletedRound] survives one: a refusal says nothing about where the entries
     * came from, and freshness is what [pairedRoundRefusal] and [isPairedCensus] are for.
     */
    val restsOnCompletedPairedRound: Boolean,

    /**
     * Why the paired collection is not currently a census, or null when nothing has refused it.
     *
     * Recorded rather than thrown, because a refusal of the bond list says nothing about the link
     * projection beside it and must not be published as though the whole observation had failed.
     * [ObservationRound.Cancelled] is deliberately not recorded here: a called-off read is not a finding,
     * and letting it clear a real refusal would lose the reason (ADR-P2-018).
     */
    val pairedRoundRefusal: OmniBudsError?,

    /** The most recent refused reports, newest last and deliberately bounded. */
    val refusedTransitions: List<RefusedDeviceTransition>,

    /** When this projection was last restated, or null when the clock gave nothing. */
    val observedAtEpochMillis: Long?,
) {
    /**
     * The devices the platform both would report and reports as connected right now.
     *
     * Collection A, and the only answer to "what is connected". A paired-only device cannot appear here
     * because it is not in [records] at all: [BondedDeviceObservation] has no link field to become one
     * with, so the fold that would have to invent a link for it does not exist (ADR-P3-017).
     */
    val connectedDevices: List<DeviceObservation>
        get() = records.filter { it.isReportedConnected }

    /** True only when every profile in the union answered a round that completed, so an empty [records]
     * means an empty room and nothing else. */
    val isUnionComplete: Boolean
        get() = profileSupportConsulted && restsOnCompletedRound && unansweredProfiles.isEmpty()

    /**
     * True only when the paired list is a census of the bond list rather than an unanswered question, so
     * an empty [pairedDevices] means a phone with no pairings and nothing else.
     */
    val isPairedCensus: Boolean
        get() = restsOnCompletedPairedRound && pairedRoundRefusal == null

    companion object {
        /**
         * A projection that has never been read.
         *
         * It starts with every enumerated profile in [unansweredProfiles] because before the platform
         * has answered, nothing has: a projection that looked complete before the first round would
         * let a consumer read the opening state as "no devices are connected". The paired collection
         * starts empty for the mirror-image reason: a bond list nobody has been allowed to read must not
         * read as a phone with no pairings.
         */
        fun notStarted(enumerated: List<ObservedProfile>): ConnectedDeviceSnapshot = ConnectedDeviceSnapshot(
            stage = ObservationStage.NOT_STARTED,
            records = emptyList(),
            pairedDevices = emptyList(),
            unansweredProfiles = enumerated.toSet(),
            profileSupportConsulted = false,
            restsOnCompletedRound = false,
            restsOnCompletedPairedRound = false,
            pairedRoundRefusal = null,
            refusedTransitions = emptyList(),
            observedAtEpochMillis = null,
        )
    }
}

/**
 * Reconciles snapshots and announcements about connected devices into one projection of devices.
 *
 * The engine lives in `:core` for the reason [AdapterStateObserver] does (ADR-P3-003): every hard case
 * in prompt sections 9 through 12 is a decision about ordering, identity and refusal, and a decision
 * that needs a radio to test is one nobody will re-check. What it does, in the order the requirements
 * arrive in:
 *
 *  - **Registers, then reads, then drains.** The platform has no list call and gives no promise that an
 *    announcement arrives after the snapshot that duplicates it, so the announcement stream is opened
 *    *before* the union is read and everything it delivers during the read is buffered and applied
 *    afterwards (ADR-P3-008). A transition that landed while the snapshot was in flight therefore
 *    survives instead of being overwritten by the staler read.
 *  - **Joins on the key and only on the key.** [joinableWith] is the sole attribution rule, which is
 *    what keeps two earbuds that ship the same label two devices (prompt section 12) and what keeps two
 *    reports that carry no key from being welded into one.
 *  - **Merges profiles, never devices.** Several profiles reporting one address produce one record that
 *    names all of them, so a drop on one service cannot disconnect a device another still holds.
 *  - **Refuses instead of inventing.** A transition the table forbids is recorded with the table's own
 *    reason and the previous state is kept; a report with no key is recorded and not applied.
 *  - **Invalidates without overclaiming.** When the local adapter stops reporting itself, every record
 *    becomes [DeviceAvailability.UNAVAILABLE] with its link back to [DeviceConnectionState.UNKNOWN].
 *    That is the whole of what prompt section 11 permits: the phone has evidence about itself and none
 *    about the headset, so nothing here says a headset was powered off. The paired collection is marked
 *    stale by the same edge rather than emptied, because a bond list the phone will not read is not a
 *    phone with no pairings.
 *  - **Keeps the two collections apart.** Each round reads the link union and the bond list as two
 *    questions with two standings, and folds them under one lock so a consumer never sees a projection
 *    where half the answer came from a round that was refused. The paired fold credits the bond axis of
 *    records the link mechanisms already named - [DeviceObservation.creditingBond] and nothing with a
 *    link field in it - and introduces no record of its own (prompt section 10, ADR-P3-017).
 *  - **One observer, one teardown.** The slot mutex is Phase 2's mechanism reused rather than
 *    reinvented, and every registration this observation made - announcements and adapter stream alike -
 *    is released in a `finally` that also runs on the cancellation path.
 *
 * What it does not do: it never starts or stops an adapter, never asks for a permission, never connects,
 * pairs, scans or persists, and never turns a platform report into a session
 * [com.omnibuds.core.state.ConnectionState] - that translation is Phase 4's, and prompt section 21
 * places it after this boundary.
 */
class ConnectedDeviceObserver(
    private val source: ConnectedDeviceSource,
    private val adapterStates: AdapterStateSource,
    private val enumeratedProfiles: List<ObservedProfile> = ObservedProfile.enumerationUnion,
    private val time: TimeProvider = NoTimeProvider,
) {
    private val slot = Mutex()
    private val projectionLock = Mutex()

    private val _snapshot = MutableStateFlow(ConnectedDeviceSnapshot.notStarted(enumeratedProfiles))

    /** The current projection, as state rather than as a record of everything that ever happened. */
    val snapshot: StateFlow<ConnectedDeviceSnapshot> = _snapshot.asStateFlow()

    /** Whether an observation currently holds the slot. Intended for diagnostics and tests. */
    val isObserving: Boolean
        get() = slot.isLocked

    /** Lifecycle only, and the only question a consumer may ask that is not about a device. */
    val stage: ObservationStage
        get() = _snapshot.value.stage

    /**
     * The structured reason the most recent teardown failed, or null when it completed cleanly.
     *
     * Held on the observer rather than emitted, for the reason [AdapterStateObserver] holds it there:
     * by the time a caller can look, the stream may already be closed by the very cancellation that
     * made teardown matter.
     */
    var teardownProblem: OmniBudsError? = null
        private set

    /**
     * A cold flow of reconciliation rounds.
     *
     * Each element is the observer's answer to one round: the whole current projection inside a
     * [ObservationRound.Success] - so that an empty list means "read, and nobody is reported" and means
     * nothing else - or the reason the round did not answer, carrying no list to misread. An
     * announcement that changes nothing publishes nothing, which is the same consecutive-repeat
     * suppression the adapter observer applies for the same reason: the platform will produce duplicate
     * announcements, and re-running settled work for no news is a defect a UI would have to defend
     * against.
     */
    fun observe(): Flow<ObservationRound<DeviceObservation>> = channelFlow {
        if (!slot.tryLock()) {
            send(
                ObservationRound.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = OPERATION_ID,
                        detail = "a connected-device observation is already active",
                    ),
                ),
            )
            return@channelFlow
        }

        val arrivals = Channel<Arrival>(Channel.UNLIMITED)
        val registrations = mutableListOf<PlatformRegistration>()
        var announcementPump: Job? = null
        var adapterPump: Job? = null
        var closer: Job? = null
        teardownProblem = null

        try {
            setStage(ObservationStage.OBSERVING)

            // Registrations first, reads second: an announcement that arrives during the union has to
            // land in `arrivals` rather than in the gap between one call and the next.
            val announcements = when (val opened = source.openConnectionEvents()) {
                is OperationOutcome.Success -> opened.value
                is OperationOutcome.Failure -> {
                    send(ObservationRound.Failure(opened.error))
                    return@channelFlow
                }

                OperationOutcome.Cancelled -> {
                    send(ObservationRound.Cancelled)
                    return@channelFlow
                }
            }
            registrations += announcements.registration
            announcementPump = launch {
                announcements.events.collect { arrivals.send(Arrival.Announcement(it)) }
            }

            val adapter = when (val opened = adapterStates.openStateChanges()) {
                is OperationOutcome.Success -> opened.value
                is OperationOutcome.Failure -> {
                    send(ObservationRound.Failure(opened.error))
                    return@channelFlow
                }

                OperationOutcome.Cancelled -> {
                    send(ObservationRound.Cancelled)
                    return@channelFlow
                }
            }
            registrations += adapter.registration
            adapterPump = launch {
                adapter.states.collect { arrivals.send(Arrival.AdapterSwitch(it)) }
            }
            // The read loop below ends when the arrival channel closes, and this is the only thing that
            // closes it: doing it from either pump would cut the other stream off mid-announcement,
            // which is a lost transition with a tidy shutdown on top.
            closer = launch {
                announcementPump.join()
                adapterPump.join()
                arrivals.close()
            }

            val support = when (val report = source.profileSupport()) {
                is OperationOutcome.Success -> report.value
                is OperationOutcome.Failure -> {
                    send(ObservationRound.Failure(report.error))
                    return@channelFlow
                }

                OperationOutcome.Cancelled -> {
                    send(ObservationRound.Cancelled)
                    return@channelFlow
                }
            }
            updateProfileSupport(support)

            val first = readRound()
            send(first)
            if (first is ObservationRound.Cancelled) {
                // Called off before anything was established, so there is no projection left to update.
                // Mid-stream a cancellation is different and ends nothing: one read being called off
                // leaves the observation intact, and cancelling the observation is what cancels it
                // (ADR-P2-018 keeps the case a case rather than a fault).
                return@channelFlow
            }

            for (arrival in arrivals) {
                val round = when (arrival) {
                    is Arrival.Announcement -> reconcileAnnouncement(arrival.event)
                    is Arrival.AdapterSwitch -> reconcileAdapterSwitch(arrival.state)
                }
                if (round != null) send(round)
            }
        } finally {
            announcementPump?.cancel()
            adapterPump?.cancel()
            closer?.cancel()
            // NonCancellable because this is the path cancellation actually takes, and a suspending
            // teardown that is cut off half-way would leave a bound profile service and a registered
            // receiver alive with no owner left who could release them.
            withContext(NonCancellable) {
                teardownProblem = disposeAll(registrations)
                setStage(ObservationStage.STOPPED)
            }
            if (slot.isLocked) slot.unlock()
        }
    }

    /**
     * Re-read both collections and restate the projection, without taking the observer slot.
     *
     * Safe while an observation is running, and prompt section 14's "refresh" made typed: the result is
     * the round itself, so a re-read that was refused reads as a refusal rather than as a successful
     * observation of zero devices. The device round's answer is what the returned round reports, because
     * that is the question section 14 asks; a paired refusal is recorded on the projection beside it
     * rather than relabelled as a failure of the link observation (ADR-P3-017).
     */
    suspend fun refresh(): ObservationRound<DeviceObservation> = readRound()

    /**
     * One round of reading: the link union first, then the bond list, then one fold of both.
     *
     * Two source calls with two standings, and the ordering is the source's business rather than this
     * engine's (ADR-P3-009). Both land before anything is published so that a consumer cannot receive a
     * projection whose link half and pairings half came from rounds with different authority.
     */
    private suspend fun readRound(): ObservationRound<DeviceObservation> =
        reconcileRound(source.snapshot(), source.bondedDevices())

    /**
     * Reads one round's two answers into the projection and returns what the device answer entitles a
     * consumer to believe.
     *
     * A [ObservationRound.Failure] or [ObservationRound.Cancelled] from the device round is forwarded
     * unchanged and changes nothing in the link half of the projection: what the platform last said stays
     * until something with authority replaces it, and a round that did not answer has no authority
     * (ADR-P3-005). The paired round folds independently for the same reason in the other direction - a
     * refused bond list cannot withdraw a link the phone reported, and a completed one cannot invent.
     *
     * The one input that is rewritten rather than forwarded is an *empty* success from a union in which
     * no profile answered. That combination is not "nobody is connected", it is "the enumeration saw
     * nothing because it could see nothing", and ADR-P3-008's rule that an unbindable profile is never
     * an empty device list means the engine cannot pass it on as one.
     */
    private suspend fun reconcileRound(
        connected: ObservationRound<DeviceObservation>,
        paired: ObservationRound<BondedDeviceObservation>,
    ): ObservationRound<DeviceObservation> {
        val outcome = projectionLock.withLock {
            val fold = _snapshot.value.foldingDevices(connected)
            val settled = fold.snapshot.foldingPaired(paired)
            _snapshot.value = settled
            RoundOutcome(settled, fold)
        }
        val fold = outcome.fold
        val refusal = fold.refusal
        if (refusal != null) return ObservationRound.Failure(refusal)
        if (fold.cancelled) return ObservationRound.Cancelled
        return ObservationRound.Success(devices = outcome.snapshot.records, stage = outcome.snapshot.stage)
    }

    /** Folds one device round into a projection, leaving the paired collection exactly as it found it. */
    private fun ConnectedDeviceSnapshot.foldingDevices(
        round: ObservationRound<DeviceObservation>,
    ): DeviceFold = when (round) {
        is ObservationRound.Failure -> DeviceFold(snapshot = this, refusal = round.error, cancelled = false)
        ObservationRound.Cancelled -> DeviceFold(snapshot = this, refusal = null, cancelled = true)
        is ObservationRound.Success -> if (round.devices.isEmpty() && couldSeeNothing()) {
            DeviceFold(snapshot = this, refusal = emptyUnionRefusal(), cancelled = false)
        } else {
            DeviceFold(snapshot = withSnapshotReported(round.devices).snapshot, refusal = null, cancelled = false)
        }
    }

    /**
     * Folds one paired round into the projection, and only into the half of it the bond list can speak to.
     *
     * Three rules, each one the same hazard wearing different clothes:
     *  - A round that did not answer restates nothing but its own reason. A refusal of the bond list is a
     *    report about this app's view, so it is recorded; but it cannot make a paired device unpaired and
     *    it cannot make an empty collection into a census, which is exactly the substitution ADR-P3-009
     *    exists to forbid. A cancellation is not even that: it is nobody saying anything, and it is
     *    deliberately not allowed to clear a real refusal.
     *  - A round that answered *replaces* the collection. The bond list is the phone's live record, so a
     *    device the phone has since unbonded leaves the projection when the next read says so, and nothing
     *    accumulates here: prompt sections 10.C and 16 refuse a saved-device store, and an engine that
     *    unioned every bond list it ever read would be one wearing a projection's name (ADR-P3-015 rule 6,
     *    applied to the paired half).
     *  - The bond axis is credited onto records the link mechanisms already named, and no record is
     *    introduced from this side. A device the bond list named and no profile reported is therefore
     *    absent from the link projection with its link unread rather than claimed - prompt section 6's
     *    "a paired device is not necessarily connected" and ADR-P0-016's "absence is not a negative" both
     *    hold, and neither is held by a predicate a consumer has to remember.
     */
    private fun ConnectedDeviceSnapshot.foldingPaired(
        round: ObservationRound<BondedDeviceObservation>,
    ): ConnectedDeviceSnapshot = when (round) {
        is ObservationRound.Failure -> copy(pairedRoundRefusal = round.error)
        ObservationRound.Cancelled -> this
        is ObservationRound.Success -> {
            val census = pairedCensusOf(round.devices)
            copy(
                records = creditingBondAxis(records, census),
                pairedDevices = census,
                restsOnCompletedPairedRound = true,
                pairedRoundRefusal = null,
                observedAtEpochMillis = time.nowEpochMillis(),
            )
        }
    }

    /**
     * One address, one paired record.
     *
     * The bond list is a set of device objects and two objects can carry one address, so the same
     * join-and-merge rule the link union uses applies here too; records the platform gave no key for stay
     * separate and come after the keyed ones, because merging two nameless reports welds two devices
     * (ADR-P3-010, ADR-P3-015 rule 6).
     */
    private fun pairedCensusOf(reported: List<BondedDeviceObservation>): List<BondedDeviceObservation> {
        val keyed = LinkedHashMap<DeviceObservationKey, BondedDeviceObservation>()
        val unkeyed = mutableListOf<BondedDeviceObservation>()
        for (record in reported) {
            if (!record.isAttributable) {
                unkeyed += record
            } else {
                keyed[record.key] = keyed[record.key]?.mergedWith(record) ?: record
            }
        }
        return keyed.values.toList() + unkeyed
    }

    /**
     * Credits every joinable projection record with the paired report's bond axis, and touches nothing
     * else on it.
     *
     * [DeviceObservation.creditingBond] is the only operation this fold has available, which is the
     * mechanism rather than the metaphor: there is no field on a paired record that could reach a link,
     * a profile set or an availability. A projection record whose own bond read was already the more
     * positive report comes back as the same value, so a paired round that confirms what the profiles
     * already said moves nothing.
     */
    private fun creditingBondAxis(
        records: List<DeviceObservation>,
        census: List<BondedDeviceObservation>,
    ): List<DeviceObservation> = records.map { record ->
        census.firstOrNull { paired -> paired.key.joinableWith(record.key) }
            ?.let { paired -> DeviceObservation.creditingBond(record, paired.bond) }
            ?: record
    }

    /**
     * A Success carrying no device, from a union where every profile declined to answer.
     *
     * RESOURCE_UNAVAILABLE rather than UNSUPPORTED_OPERATION, and the retry class is the reason: the
     * operation *is* supported here - a later phase will enumerate this same union successfully - so
     * marking it NEVER_RETRY would tell a caller to give up on a stack that may simply not have been
     * ready. What failed was holding on to the resources the enumeration needed, which is exactly what
     * this category already means for a registration that could not be made (ADR-P3-015).
     */
    private fun emptyUnionRefusal(): OmniBudsError = OmniBudsError(
        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
        operationId = OPERATION_ID,
        detail = "no profile in the enumeration union could answer, so an empty device list " +
            "is a statement about the observation and not about any device",
    )

    /** Applies one announcement, or returns null when it changed nothing worth republishing. */
    private suspend fun reconcileAnnouncement(
        event: DeviceConnectionEvent,
    ): ObservationRound<DeviceObservation>? {
        val result = updateProjection { current ->
            when (event) {
                is DeviceConnectionEvent.LinkChanged -> current.withLinkEvent(event)
                is DeviceConnectionEvent.BondChanged -> current.withBondEvent(event)
            }
        }
        if (!result.changed) return null
        return ObservationRound.Success(devices = result.snapshot.records, stage = result.snapshot.stage)
    }

    /**
     * Reacts to the local adapter changing, the only input here that invalidates a projection without
     * saying anything about a device.
     *
     * An adapter that has stopped reporting itself makes every device record unobservable. Records are
     * marked rather than deleted and their link returns to [DeviceConnectionState.UNKNOWN], not to
     * [DeviceConnectionState.DISCONNECTED], and their timestamps stay where they were: the round that
     * follows is a [ObservationRound.Failure] carrying [OmniBudsErrorCategory.BLUETOOTH_DISABLED], which
     * is what lets prompt section 11 be satisfied rather than approximated - the projection says the
     * phone went quiet, and refuses to say anything at all about the headset's power.
     *
     * An adapter coming back re-reads the union, which is the re-snapshot the research's
     * snapshot-on-start, event-driven-delta architecture asks for. An adapter mid-transition is treated
     * as neither case, because it reports nothing about devices either way.
     *
     * The paired collection is withdrawn from rather than emptied by the same edge. Prompt section 11
     * speaks of clearing the *active-connected* projection, and a pairing is not an active connection: the
     * entries the phone last named stay, and the claim that they are current goes, because the API that
     * reads them answers an empty set for any adapter that is not on.
     */
    private suspend fun reconcileAdapterSwitch(
        state: BluetoothAdapterState,
    ): ObservationRound<DeviceObservation>? = when {
        state.isUsable() -> readRound()

        state.isProvablyDisabled() || state == BluetoothAdapterState.UNAVAILABLE -> {
            val category = if (state.isProvablyDisabled()) {
                OmniBudsErrorCategory.BLUETOOTH_DISABLED
            } else {
                OmniBudsErrorCategory.ADAPTER_UNAVAILABLE
            }
            val next = touchProjection { current ->
                current.copy(
                    records = current.records.map { record ->
                        record.copy(
                            link = DeviceConnectionState.UNKNOWN,
                            availability = DeviceAvailability.UNAVAILABLE,
                            observedProfiles = emptySet(),
                        )
                    },
                    // The paired collection keeps its entries and loses its census claim, which is the
                    // only reading the platform supports: the bond list is a phone-side record that
                    // survives the radio going quiet, but it is *unread* while it does, and the API that
                    // answers it reports an empty set for every handset in that state. Emptying the
                    // collection here would be prompt section 10.B's distinction inverted by force.
                    pairedRoundRefusal = OmniBudsError(
                        category = category,
                        operationId = OPERATION_ID,
                        detail = "the local adapter stopped reporting itself, so the paired list became " +
                            "unreadable; the entries kept here are what the phone last named and are not " +
                            "a current census of its pairings",
                    ),
                    observedAtEpochMillis = time.nowEpochMillis(),
                )
            }
            ObservationRound.Failure(
                OmniBudsError(
                    category = category,
                    operationId = OPERATION_ID,
                    detail = "the local adapter stopped reporting itself, so ${next.records.size} device " +
                        "records became unobservable; this is not evidence about the power state of any " +
                        "remote device",
                ),
            )
        }

        else -> null
    }

    private suspend fun updateProfileSupport(report: ProfileSupportReport) {
        touchProjection { current ->
            current.copy(
                unansweredProfiles = report.unansweredWithin(enumeratedProfiles),
                profileSupportConsulted = true,
            )
        }
    }

    private suspend fun setStage(stage: ObservationStage) {
        touchProjection { current -> current.copy(stage = stage) }
    }

    /** One atomic restatement of the projection, so a concurrent refresh cannot lose a field to a stale copy. */
    private suspend fun touchProjection(
        transform: (ConnectedDeviceSnapshot) -> ConnectedDeviceSnapshot,
    ): ConnectedDeviceSnapshot = projectionLock.withLock {
        val next = transform(_snapshot.value)
        _snapshot.value = next
        next
    }

    /** As [touchProjection], for the folds that also have to say whether anything moved. */
    private suspend fun updateProjection(
        transform: (ConnectedDeviceSnapshot) -> Reconciliation,
    ): Reconciliation = projectionLock.withLock {
        val next = transform(_snapshot.value)
        _snapshot.value = next.snapshot
        next
    }

    /**
     * Releases every registration this observation made.
     *
     * Every registration is attempted even after one of them throws, because a teardown that stops at
     * the first failure converts one leak into an unknown number of them. The reported problem is the
     * first cause seen plus the count of registrations that did not release, since "one of two leaked"
     * and "both leaked" are different sizes of bug.
     */
    private suspend fun disposeAll(registrations: List<PlatformRegistration>): OmniBudsError? {
        var released = 0
        var failures = 0
        var firstCause: String? = null
        for (registration in registrations) {
            val cause = runCatching { registration.dispose() }.exceptionOrNull()
            if (cause == null) {
                released += 1
            } else {
                failures += 1
                if (firstCause == null) firstCause = cause::class.simpleName
            }
        }
        return if (failures == 0) {
            null
        } else {
            OmniBudsError(
                category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                operationId = OPERATION_ID,
                detail = "connected-device registration teardown reported $firstCause; $failures of " +
                    "${registrations.size} registrations did not release, $released did",
            )
        }
    }

    /** Whether an empty union from this projection would be a finding about devices. */
    private fun ConnectedDeviceSnapshot.couldSeeNothing(): Boolean =
        profileSupportConsulted &&
            enumeratedProfiles.isNotEmpty() &&
            enumeratedProfiles.all { it in unansweredProfiles }

    /**
     * Folds one snapshot union into the projection.
     *
     * Three rules do the work, and each of them is a way of getting the device count wrong:
     *
     *  - Reports of one address from several profiles become one record naming every profile that
     *    listed it (prompt section 12). The link is the fresh report's, except where a profile that did
     *    not answer had claimed a live link, because a service that stayed silent cannot withdraw a
     *    link it never restated.
     *  - A record the union did not list loses only the profiles that actually answered. If that leaves
     *    it with no live claim it is disconnected; if some profile is still unaccounted for the record
     *    keeps what it said, because the union was incomplete and absence proves nothing
     *    ([ConnectedDeviceSnapshot.unansweredProfiles]).
     *  - Records the platform never gave a key for cannot be carried across rounds at all: there is
     *    nothing to recognise them by, so each round's unkeyed reports replace the previous ones instead
     *    of accumulating a shadow population - and they are never merged with each other.
     */
    private fun ConnectedDeviceSnapshot.withSnapshotReported(
        reported: List<DeviceObservation>,
    ): Reconciliation {
        val answering = enumeratedProfiles.toSet() - unansweredProfiles
        val incoming = LinkedHashMap<DeviceObservationKey, DeviceObservation>()
        val unkeyed = mutableListOf<DeviceObservation>()
        for (report in reported) {
            if (!report.isAttributable) {
                unkeyed += report
            } else {
                incoming[report.key] = incoming[report.key]?.mergedWith(report) ?: report
            }
        }

        val next = mutableListOf<DeviceObservation>()
        var refusals = refusedTransitions
        for (record in records) {
            if (!record.isAttributable) continue
            val (kept, refusal) = record.reconciledWithReport(incoming[record.key], answering)
            next += kept
            if (refusal != null) refusals = (refusals + refusal).takeLast(REFUSAL_WINDOW)
        }
        for (report in incoming.values) {
            if (next.none { it.key.joinableWith(report.key) }) next += report
        }
        next += unkeyed

        val moved = next != records
        return Reconciliation(
            snapshot = copy(
                records = next,
                restsOnCompletedRound = true,
                refusedTransitions = refusals,
                observedAtEpochMillis = time.nowEpochMillis(),
            ),
            changed = moved,
        )
    }

    /**
     * One existing record against what this round said about it, with the aggregate link recomputed,
     * observability re-established and an impossibility reported rather than adopted.
     *
     * The availability restatement is deliberate and is the counterpart to the adapter rule: a round that
     * completed is the platform having enumerated the space, so a record carried through it is observable
     * again even when it was marked unread by a radio that has since come back. A round that did not
     * answer never reaches here, and the refusal state survives it.
     */
    private fun DeviceObservation.reconciledWithReport(
        report: DeviceObservation?,
        answering: Set<ObservedProfile>,
    ): Pair<DeviceObservation, RefusedDeviceTransition?> {
        val carriedProfiles = observedProfiles - answering
        val candidate = if (report == null) {
            copy(
                link = if (carriedProfiles.isEmpty()) DeviceConnectionState.DISCONNECTED else link,
                observedProfiles = carriedProfiles,
                availability = DeviceAvailability.AVAILABLE,
            )
        } else {
            val liveElsewhere = carriedProfiles.isNotEmpty() && link.isConnected()
            report.copy(
                displayName = if (report.hasReportedName) report.displayName else displayName,
                link = if (liveElsewhere && !report.link.isConnected()) {
                    DeviceConnectionState.CONNECTED
                } else {
                    report.link
                },
                bond = if (report.bond.isKnown()) report.bond else bond,
                availability = DeviceAvailability.AVAILABLE,
                observedProfiles = carriedProfiles + report.observedProfiles,
                observedAtEpochMillis = latestOf(observedAtEpochMillis, report.observedAtEpochMillis),
            )
        }
        val reason = DeviceConnectionStateTransitions.refusalReason(link, candidate.link)
            ?: return candidate to null
        return this to RefusedDeviceTransition(
            key = candidate.key,
            kind = RefusalKind.IMPOSSIBLE_TRANSITION,
            retained = link,
            attempted = candidate.link,
            reason = "$reason, and the report arrived in a snapshot",
            refusedAtEpochMillis = time.nowEpochMillis(),
        )
    }

    /**
     * Applies a link announcement.
     *
     * The aggregate is derived from the profile set rather than taken from the event, which is the only
     * way to keep a device connected when one of its two services drops (prompt section 12) while a
     * link-layer disconnect - the announcement that names no service at all - still ends every profile's
     * claim, because a service connection rides the link it was opened over.
     */
    private fun ConnectedDeviceSnapshot.withLinkEvent(event: DeviceConnectionEvent.LinkChanged): Reconciliation {
        if (event.key.isUnattributable()) {
            return refusal(event.key, RefusalKind.UNATTRIBUTABLE_REPORT, attempted = event.link)
        }
        val existing = records.firstOrNull { it.key.joinableWith(event.key) }
        val reported = event.profile
        val heldProfiles = existing?.observedProfiles ?: emptySet()
        val profiles = when {
            event.link.isConnected() && reported != null -> heldProfiles + reported
            reported != null -> heldProfiles - reported
            event.link.isProvablyDisconnected() -> emptySet()
            else -> heldProfiles
        }
        val aggregate = if (profiles.isNotEmpty()) DeviceConnectionState.CONNECTED else event.link
        val held = existing?.link ?: DeviceConnectionState.UNKNOWN
        DeviceConnectionStateTransitions.refusalReason(held, aggregate)?.let { reason ->
            return refusal(
                key = event.key,
                kind = RefusalKind.IMPOSSIBLE_TRANSITION,
                retained = held,
                attempted = aggregate,
                reason = reason,
            )
        }

        val updated = if (existing == null) {
            DeviceObservation.reported(
                key = event.key,
                link = aggregate,
                bond = DeviceBondState.UNKNOWN,
                availability = DeviceAvailability.AVAILABLE,
                observedProfiles = profiles,
                arrival = ObservationArrival.EVENT,
                observedAtEpochMillis = event.observedAtEpochMillis,
            )
        } else {
            existing.copy(
                link = aggregate,
                availability = DeviceAvailability.AVAILABLE,
                observedProfiles = profiles,
                arrival = ObservationArrival.EVENT,
                observedAtEpochMillis = latestOf(existing.observedAtEpochMillis, event.observedAtEpochMillis),
            )
        }
        // A duplicate announcement republishes nothing, not even a newer timestamp: a projection whose
        // clock advances while no fact changed would tell a consumer a round happened when nothing did.
        if (existing != null && updated saysSameAs existing) return Reconciliation(this, changed = false)
        return Reconciliation(replacing(existing, updated), changed = true)
    }

    /** Applies a pairing announcement, which says nothing about the link and must not appear to. */
    private fun ConnectedDeviceSnapshot.withBondEvent(event: DeviceConnectionEvent.BondChanged): Reconciliation {
        if (event.key.isUnattributable()) {
            return refusal(event.key, RefusalKind.UNATTRIBUTABLE_REPORT)
        }
        val existing = records.firstOrNull { it.key.joinableWith(event.key) }
        val updated = if (existing == null) {
            DeviceObservation.reported(
                key = event.key,
                link = DeviceConnectionState.UNKNOWN,
                bond = event.bond,
                availability = DeviceAvailability.AVAILABLE,
                arrival = ObservationArrival.EVENT,
                observedAtEpochMillis = event.observedAtEpochMillis,
            )
        } else {
            existing.copy(
                bond = if (event.bond.isKnown()) event.bond else existing.bond,
                arrival = ObservationArrival.EVENT,
                observedAtEpochMillis = latestOf(existing.observedAtEpochMillis, event.observedAtEpochMillis),
            )
        }
        if (existing != null && updated saysSameAs existing) return Reconciliation(this, changed = false)
        return Reconciliation(replacing(existing, updated), changed = true)
    }

    /** Adds [updated] in place of [existing], keeping the record order stable so a list can be diffed. */
    private fun ConnectedDeviceSnapshot.replacing(
        existing: DeviceObservation?,
        updated: DeviceObservation,
    ): ConnectedDeviceSnapshot {
        val records = if (existing == null) {
            this.records + updated
        } else {
            this.records.map { record -> if (record.key.joinableWith(existing.key)) updated else record }
        }
        return copy(records = records, observedAtEpochMillis = time.nowEpochMillis())
    }

    private fun ConnectedDeviceSnapshot.refusal(
        key: DeviceObservationKey,
        kind: RefusalKind,
        retained: DeviceConnectionState? = null,
        attempted: DeviceConnectionState? = null,
        reason: String = "the platform named no device, so the report could not be attributed to one",
    ): Reconciliation {
        // Bounded, because a projection that kept every awkward event it ever saw would be the device
        // history prompt section 10 forbids, wearing a diagnostic's costume.
        val window = (refusedTransitions + RefusedDeviceTransition(
            key = key,
            kind = kind,
            retained = retained,
            attempted = attempted,
            reason = reason,
            refusedAtEpochMillis = time.nowEpochMillis(),
        )).takeLast(REFUSAL_WINDOW)
        return Reconciliation(copy(refusedTransitions = window), changed = false)
    }

    /** Two reports that say the same thing about a device, ignoring when and how each was said. */
    private infix fun DeviceObservation.saysSameAs(other: DeviceObservation): Boolean =
        key == other.key &&
            displayName == other.displayName &&
            link == other.link &&
            bond == other.bond &&
            availability == other.availability &&
            observedProfiles == other.observedProfiles

    private fun latestOf(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private companion object {
        const val OPERATION_ID = "device-observer.observe"
        const val REFUSAL_WINDOW = 8
    }

    /** A projection after one fold, and whether the fold moved it. */
    private class Reconciliation(val snapshot: ConnectedDeviceSnapshot, val changed: Boolean)

    /**
     * One device round folded into a projection: what the projection became, and the reason the round
     * gave for not changing it.
     *
     * Three cases rather than two because "it refused" and "it was called off" are different answers and
     * only the first is a finding (ADR-P2-018); neither may be published as a successful observation, so
     * the fold that produced no change still has to say which of the two it was.
     */
    private class DeviceFold(
        val snapshot: ConnectedDeviceSnapshot,
        val refusal: OmniBudsError?,
        val cancelled: Boolean,
    )

    /** A round's published result: the projection both folds produced, and how the device round answered. */
    private class RoundOutcome(val snapshot: ConnectedDeviceSnapshot, val fold: DeviceFold)
}

/** One thing the platform said, and which of the two streams it came from. */
private sealed interface Arrival {
    data class Announcement(val event: DeviceConnectionEvent) : Arrival
    data class AdapterSwitch(val state: BluetoothAdapterState) : Arrival
}
