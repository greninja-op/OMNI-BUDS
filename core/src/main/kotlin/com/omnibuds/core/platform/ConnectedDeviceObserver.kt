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
 */
data class ConnectedDeviceSnapshot(
    /** Lifecycle only. A failure reason never appears here (ADR-P3-005). */
    val stage: ObservationStage,

    /** Every record the platform has reported and not forgotten, connected or not. */
    val records: List<DeviceObservation>,

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

    /** The most recent refused reports, newest last and deliberately bounded. */
    val refusedTransitions: List<RefusedDeviceTransition>,

    /** When this projection was last restated, or null when the clock gave nothing. */
    val observedAtEpochMillis: Long?,
) {
    /** The devices the platform both would report and reports as connected right now. */
    val connectedDevices: List<DeviceObservation>
        get() = records.filter { it.isReportedConnected }

    /** True only when every profile in the union answered a round that completed, so an empty [records]
     * means an empty room and nothing else. */
    val isUnionComplete: Boolean
        get() = profileSupportConsulted && restsOnCompletedRound && unansweredProfiles.isEmpty()

    companion object {
        /**
         * A projection that has never been read.
         *
         * It starts with every enumerated profile in [unansweredProfiles] because before the platform
         * has answered, nothing has: a projection that looked complete before the first round would
         * let a consumer read the opening state as "no devices are connected".
         */
        fun notStarted(enumerated: List<ObservedProfile>): ConnectedDeviceSnapshot = ConnectedDeviceSnapshot(
            stage = ObservationStage.NOT_STARTED,
            records = emptyList(),
            unansweredProfiles = enumerated.toSet(),
            profileSupportConsulted = false,
            restsOnCompletedRound = false,
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
 *    about the headset, so nothing here says a headset was powered off.
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

            val first = reconcileSnapshot(source.snapshot())
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
     * Re-read the union and restate the projection, without taking the observer slot.
     *
     * Safe while an observation is running, and prompt section 14's "refresh" made typed: the result is
     * the round itself, so a re-read that was refused reads as a refusal rather than as a successful
     * observation of zero devices.
     */
    suspend fun refresh(): ObservationRound<DeviceObservation> = reconcileSnapshot(source.snapshot())

    /**
     * Reads one snapshot round into the projection and returns what it entitles a consumer to believe.
     *
     * A [ObservationRound.Failure] or [ObservationRound.Cancelled] is forwarded unchanged and changes
     * nothing in the projection: what the platform last said stays until something with authority
     * replaces it, and a round that did not answer has no authority (ADR-P3-005).
     *
     * The one input that is rewritten rather than forwarded is an *empty* success from a union in which
     * no profile answered. That combination is not "nobody is connected", it is "the enumeration saw
     * nothing because it could see nothing", and ADR-P3-008's rule that an unbindable profile is never
     * an empty device list means the engine cannot pass it on as one.
     */
    private suspend fun reconcileSnapshot(
        round: ObservationRound<DeviceObservation>,
    ): ObservationRound<DeviceObservation> {
        if (round !is ObservationRound.Success) return round
        // Decided inside the lock, because the question "could this round see anything?" is answered by
        // the same profile bookkeeping that the fold is about to change.
        val outcome = projectionLock.withLock {
            val current = _snapshot.value
            if (round.devices.isEmpty() && current.couldSeeNothing()) {
                SnapshotOutcome(current, emptyUnionRefusal())
            } else {
                val folded = current.withSnapshotReported(round.devices)
                _snapshot.value = folded.snapshot
                SnapshotOutcome(folded.snapshot, null)
            }
        }
        val refusal = outcome.refusal
        if (refusal != null) return ObservationRound.Failure(refusal)
        return ObservationRound.Success(devices = outcome.snapshot.records, stage = outcome.snapshot.stage)
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
     */
    private suspend fun reconcileAdapterSwitch(
        state: BluetoothAdapterState,
    ): ObservationRound<DeviceObservation>? = when {
        state.isUsable() -> reconcileSnapshot(source.snapshot())

        state.isProvablyDisabled() || state == BluetoothAdapterState.UNAVAILABLE -> {
            val next = touchProjection { current ->
                current.copy(
                    records = current.records.map { record ->
                        record.copy(
                            link = DeviceConnectionState.UNKNOWN,
                            availability = DeviceAvailability.UNAVAILABLE,
                            observedProfiles = emptySet(),
                        )
                    },
                    observedAtEpochMillis = time.nowEpochMillis(),
                )
            }
            ObservationRound.Failure(
                OmniBudsError(
                    category = if (state.isProvablyDisabled()) {
                        OmniBudsErrorCategory.BLUETOOTH_DISABLED
                    } else {
                        OmniBudsErrorCategory.ADAPTER_UNAVAILABLE
                    },
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

    /** A snapshot round's result: the projection it produced, or the reason it earned none. */
    private class SnapshotOutcome(val snapshot: ConnectedDeviceSnapshot, val refusal: OmniBudsError?)
}

/** One thing the platform said, and which of the two streams it came from. */
private sealed interface Arrival {
    data class Announcement(val event: DeviceConnectionEvent) : Arrival
    data class AdapterSwitch(val state: BluetoothAdapterState) : Arrival
}
