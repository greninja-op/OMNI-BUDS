package com.omnibuds.core.session

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.device.DeviceSession
import com.omnibuds.core.platform.ConnectedDeviceSnapshot
import com.omnibuds.core.platform.DeviceBondState
import com.omnibuds.core.platform.DeviceConnectionState
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationStage
import com.omnibuds.core.platform.isObservable
import com.omnibuds.core.platform.NoTimeProvider
import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.state.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one owner of OmniBuds's device-session state.
 *
 * It reads Phase 3's projection and nothing else: no source, no adapter, no receiver, no
 * permission. Phase 3 built the seam so that everything hard about turning platform reports
 * into state could live here and be tested without a radio, and prompt section 8's rule that
 * this engine must not create a second observer is what makes that seam one-directional -
 * `session` is layer 3 and `platform` is layer 1, so the import that would let the engine
 * reach the phone does not exist (ADR-P4-001).
 *
 * **What it guarantees.**
 *
 *  - **One authoritative value.** [snapshot] is a `StateFlow` of the whole
 *    [DeviceSessionSnapshot]; [DeviceSessionSnapshot.activeSessions] and the other views are
 *    derived at read time, so prompt section 12's three flows are one owner with three
 *    readings rather than three places to be wrong (ADR-P4-001's consequence).
 *  - **Moves only where the table allows.** Every state change goes through
 *    [TrackedDeviceSession.moving] into [DeviceState.attemptConnection], so a refused move is
 *    a returned failure recorded in [DeviceSessionSnapshot.refusedMoves] and never a value
 *    that exists. The engine cannot reach `IDENTIFYING`, `CAPABILITY_DISCOVERY`, `READY` or
 *    `CONTROL_SESSION` at all (ADR-P4-003), which is how "a session is not a claim about a
 *    device" is enforced rather than merely asserted.
 *  - **Absence is only evidence when the union answered.** A device missing from a complete
 *    union becomes disconnected; a device missing from a refused, partial or unconfirmed
 *    round is left exactly as it was (ADR-P3-015 rule 4, ADR-P3-009), and the reason the list
 *    is empty travels with it in [SessionObservationStatus] (ADR-P4-007).
 *  - **Duplicates are impossible by construction.** One session per joinable key, because the
 *    key is the only join (ADR-P3-010); a second profile reporting the same device updates
 *    the session it already has, and two devices sharing a name stay two sessions because a
 *    name participates in no identity decision here (prompt section 12).
 *  - **Nothing is retained past the session.** Terminated sessions leave the list, ambiguous
 *    sessions live for the one projection that could not attribute them, and no event or
 *    snapshot is stored anywhere (SEC-ID-005, prompt section 10.C).
 *
 * **What it does not do.** It never pairs, connects, scans, opens a transport, reads a battery,
 * asks a permission, starts a coroutine scope of its own, or calls
 * [com.omnibuds.core.platform.ConnectedDeviceObserver.refresh] - the last of those is a
 * decision with a reason and an open risk, not an oversight (ADR-P4-011). `DeviceState`'s
 * copy-bypass limitation also stands: this engine is the only writer by construction, not by
 * compiler, and confining mutation to one owner remains Phase 24's obligation (ADR-P4-001).
 *
 * **Concurrency.** [lock] covers the whole reconcile-and-publish step, so no consumer ever sees
 * half a round applied, and event order matches state order because both are produced inside it.
 * [consume] is written so that the cancellation path - the one that actually runs when a caller
 * goes away - still publishes a final value, using the same `NonCancellable` teardown Phase 3
 * needed for the same reason (ADR-P4-009).
 */
class DeviceSessionEngine(
    private val time: TimeProvider = NoTimeProvider,
    private val sessionIdPrefix: String = DEFAULT_SESSION_PREFIX,
) {

    private val lock = Mutex()
    private var sequence: Long = 0L
    private var tracked: List<TrackedDeviceSession> = emptyList()
    private var revision: Long = 0L
    private var refusals: List<RefusedSessionMove> = emptyList()

    private val _snapshot = MutableStateFlow(DeviceSessionSnapshot.notStarted())

    /** The authoritative session state. Read-only for every consumer, by rule 5.7. */
    val snapshot: StateFlow<DeviceSessionSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableSharedFlow<DeviceSessionEvent>(
        // Declared rather than defaulted, because Phase 0 specs rule 5.5 requires every flow to
        // state its buffering and its overflow: no replay (the StateFlow is the truth, ADR-P4-008),
        // a bounded window for live collectors, and DROP_OLDEST so a lagging consumer loses an old
        // edge instead of stalling the engine that produces the new ones.
        replay = EVENT_REPLAY,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Edges in the state, for consumers that react rather than read. Never a record of anything. */
    val events: SharedFlow<DeviceSessionEvent> = _events.asSharedFlow()

    /**
     * Applies every projection the observation publishes, until the caller stops.
     *
     * Runs in the caller's job - the engine owns no scope, launches nothing and picks no dispatcher,
     * which is rule 5.1's requirement and the reason there is nothing here to leak. When the flow
     * completes the sessions are ended with [SessionTermination.OBSERVATION_STOPPED]; when the caller
     * is cancelled they are ended with [SessionTermination.ENGINE_CANCELLED]. Both publish an empty
     * authoritative list with the reason attached, which is prompt section 11's rule that a stopped
     * observation must not leave devices looking active.
     */
    suspend fun consume(observation: Flow<ConnectedDeviceSnapshot>) {
        try {
            observation.collect { projection -> apply(projection) }
        } catch (cancellation: CancellationException) {
            terminateAll(SessionTermination.ENGINE_CANCELLED)
            throw cancellation
        }
        terminateAll(SessionTermination.OBSERVATION_STOPPED)
    }

    /**
     * Applies one projection and publishes the result.
     *
     * Public because it is the deterministic seam every rule in this class is tested through: a
     * caller that would rather push snapshots than hand over a flow gets the same behaviour, and
     * a test gets a per-round boundary with no scheduler in it.
     */
    suspend fun apply(projection: ConnectedDeviceSnapshot) {
        lock.withLock {
            val at = time.nowEpochMillis()
            val status = SessionObservationStatus.of(projection)
            val published = mutableListOf<DeviceSessionEvent>()

            if (status is SessionObservationStatus.Refused) {
                published += DeviceSessionEvent.SessionObservationFailed(status.error, at)
            }

            val carried = expireAmbiguousSessions(published, at)
            val keyedReports = LinkedHashMap<DeviceObservationKey, DeviceObservation>()
            val unkeyedReports = mutableListOf<DeviceObservation>()
            for (record in projection.records) {
                if (record.isAttributable) {
                    // Two reports of one key inside a single projection are merged with Phase 3's own
                    // rule rather than decided by which arrived last: mergedWith ranks each axis, so a
                    // CONNECTED and a DISCONNECTED report of one device in the same round cannot make a
                    // session by round-trip order (ADR-P4-004, ADR-P3-010).
                    keyedReports[record.key] = keyedReports[record.key]?.mergedWith(record) ?: record
                } else {
                    unkeyedReports += record
                }
            }

            val next = mutableListOf<TrackedDeviceSession>()
            for (session in carried) {
                val report = keyedReports[session.key]
                val settled = if (report != null) {
                    applyReport(session, report, published, at)
                } else {
                    applyAbsence(session, status, published, at)
                }
                if (settled != null) next += settled
            }

            for ((key, report) in keyedReports) {
                if (carried.any { session -> session.key == key }) continue
                createSession(key, SessionIdentityBasis.KEYED, report, published, at)?.let { fresh ->
                    next += fresh
                }
            }
            for (report in unkeyedReports) {
                createSession(
                    key = DeviceObservationKey.NotReported,
                    basis = SessionIdentityBasis.AMBIGUOUS,
                    report = report,
                    published = published,
                    at = at,
                )?.let { fresh -> next += fresh }
            }

            tracked = next
            revision += 1
            // The engine's own stage follows the observation it is reading: a projection that has
            // stopped leaves nothing to run, and claiming RUNNING while the observer is finished
            // would be the stale-active-status defect prompt section 11 names.
            val stage = if (projection.stage == ObservationStage.STOPPED) {
                SessionEngineStage.STOPPED
            } else {
                SessionEngineStage.RUNNING
            }
            publish(stage, status, published, at)
        }
    }

    /**
     * Ends every session this engine holds, publishes an empty list, and says why.
     *
     * The published value is [SessionObservationStatus.Stopped], so "no sessions" and "nobody is
     * looking" remain different sentences even here, where both are true (ADR-P4-007).
     */
    suspend fun stop(reason: SessionTermination = SessionTermination.OBSERVATION_STOPPED) {
        terminateAll(reason)
    }

    private suspend fun terminateAll(reason: SessionTermination) {
        withContext(NonCancellable) {
            lock.withLock {
                val at = time.nowEpochMillis()
                val published = tracked.map { session ->
                    DeviceSessionEvent.SessionEnded(session.sessionId, reason, at)
                }
                tracked = emptyList()
                revision += 1
                publish(SessionEngineStage.STOPPED, SessionObservationStatus.Stopped, published, at)
            }
        }
    }

    /**
     * Drops the sessions a previous round created from reports it could not attribute.
     *
     * An unkeyed session cannot be matched to anything, including its own predecessor, so carrying
     * it forward would silently weld two devices the platform never named (ADR-P3-010, prompt
     * section 8). They are ended here, with the reason that says ambiguity rather than absence, and
     * any device still present is re-created below from this round's report.
     */
    private fun expireAmbiguousSessions(
        published: MutableList<DeviceSessionEvent>,
        at: Long?,
    ): List<TrackedDeviceSession> {
        val carried = mutableListOf<TrackedDeviceSession>()
        for (session in tracked) {
            if (session.basis == SessionIdentityBasis.KEYED) {
                carried += session
            } else {
                published += DeviceSessionEvent.SessionEnded(
                    sessionId = session.sessionId,
                    reason = SessionTermination.AMBIGUOUS_SESSION_EXPIRED,
                    atEpochMillis = at,
                )
            }
        }
        return carried
    }

    /**
     * Folds one device report into the session that already holds its key.
     *
     * Returns the session to keep, never a new one: this path cannot mint an id, cannot change the
     * identity basis and cannot merge in a name it was not given.
     */
    private fun applyReport(
        session: TrackedDeviceSession,
        report: DeviceObservation,
        published: MutableList<DeviceSessionEvent>,
        at: Long?,
    ): TrackedDeviceSession? {
        val target = targetStateOf(report, tracked = true) ?: return session

        // A device still reported disconnected one round after we recorded it as disconnected has
        // used its grace. Same window as the absence path, so the two cannot disagree about lifetime.
        if (target == ConnectionState.DISCONNECTED && session.isAwaitingGraceExpiry) {
            published += DeviceSessionEvent.SessionEnded(
                session.sessionId,
                SessionTermination.PROVEN_DISCONNECT_PAST_GRACE,
                at,
            )
            return null
        }

        var current = session
        if (target != current.connectionState) {
            val from = current.connectionState
            when (val moved = TrackedDeviceSession.moving(current, target, at)) {
                is OperationOutcome.Success -> {
                    current = moved.value
                    when {
                        target == ConnectionState.CONNECTED &&
                            from == ConnectionState.DISCONNECTED -> {
                            current = current.resumed()
                            published += DeviceSessionEvent.SessionReconnected(current.sessionId, at)
                        }

                        target == ConnectionState.DISCONNECTED -> {
                            current = current.markedDisconnected(at)
                            published += DeviceSessionEvent.SessionDisconnected(
                                current.sessionId,
                                DisconnectEvidence.REPORTED,
                                at,
                            )
                        }

                        else -> published += DeviceSessionEvent.SessionUpdated(
                            current.sessionId,
                            from,
                            target,
                            at,
                        )
                    }
                }

                is OperationOutcome.Failure -> recordRefusal(current, from, target, moved.error.detail, at)
                OperationOutcome.Cancelled -> Unit
            }
        }

        val merged = current.session.identity.mergedWith(identityOf(report))
        if (merged != current.session.identity) {
            published += DeviceSessionEvent.SessionIdentityChanged(
                sessionId = current.sessionId,
                knownFieldCountBefore = current.session.identity.knownFieldCount,
                knownFieldCountAfter = merged.knownFieldCount,
                atEpochMillis = at,
            )
            current = current.withMergedIdentity(merged)
        }

        return current.restatedAt(report.observedAtEpochMillis ?: at)
    }

    /**
     * Folds "this projection did not mention you" into a session.
     *
     * Three answers, and the distinction between them is the whole of ADR-P4-004's last two rows.
     */
    private fun applyAbsence(
        session: TrackedDeviceSession,
        status: SessionObservationStatus,
        published: MutableList<DeviceSessionEvent>,
        at: Long?,
    ): TrackedDeviceSession? = when {
        status is SessionObservationStatus.Stopped -> {
            published += DeviceSessionEvent.SessionEnded(
                session.sessionId,
                SessionTermination.OBSERVATION_STOPPED,
                at,
            )
            null
        }

        // Absence from a census in which every profile answered is the platform's answer.
        status is SessionObservationStatus.Confirmed && status.unionComplete -> {
            if (session.isAwaitingGraceExpiry) {
                published += DeviceSessionEvent.SessionEnded(
                    session.sessionId,
                    SessionTermination.PROVEN_DISCONNECT_PAST_GRACE,
                    at,
                )
                null
            } else {
                val from = session.connectionState
                when (val moved = TrackedDeviceSession.moving(
                    session,
                    ConnectionState.DISCONNECTED,
                    at,
                )) {
                    is OperationOutcome.Success -> {
                        published += DeviceSessionEvent.SessionDisconnected(
                            session.sessionId,
                            DisconnectEvidence.ABSENT_FROM_COMPLETE_UNION,
                            at,
                        )
                        moved.value.markedDisconnected(at)
                    }

                    is OperationOutcome.Failure -> {
                        recordRefusal(session, from, ConnectionState.DISCONNECTED, moved.error.detail, at)
                        session
                    }

                    OperationOutcome.Cancelled -> session
                }
            }
        }

        // A refusal or an unanswered round withdraws nothing, including the sessions it could not see.
        else -> session
    }

    /**
     * Mints a session for a device this engine was not tracking, or declines to.
     *
     * Declining is the point of the return type: a first sighting of a device the platform reports
     * as not connected, or cannot currently be seen at all, is not a session. Starting one would be
     * reading "absent" as a device with a history, and prompt section 10.B refuses the paired-only
     * case in the same sentence.
     */
    private fun createSession(
        key: DeviceObservationKey,
        basis: SessionIdentityBasis,
        report: DeviceObservation,
        published: MutableList<DeviceSessionEvent>,
        at: Long?,
    ): TrackedDeviceSession? {
        val target = targetStateOf(report, tracked = false) ?: return null
        val identity = identityOf(report)
        val sessionId = "$sessionIdPrefix-${++sequence}"
        var fresh = TrackedDeviceSession(
            sessionId = sessionId,
            key = key,
            basis = basis,
            session = DeviceSession.temporary(
                sessionId = sessionId,
                identity = identity,
                createdAtEpochMillis = at,
            ),
            state = DeviceState.initial(sessionId = sessionId, identity = identity),
            timeline = SessionTimeline.unopened().openedAt(at),
        )

        if (target != ConnectionState.UNKNOWN) {
            when (val moved = TrackedDeviceSession.moving(fresh, target, at)) {
                is OperationOutcome.Success -> fresh = moved.value
                is OperationOutcome.Failure -> recordRefusal(
                    fresh,
                    ConnectionState.UNKNOWN,
                    target,
                    moved.error.detail,
                    at,
                )

                OperationOutcome.Cancelled -> Unit
            }
        }

        published += DeviceSessionEvent.SessionCreated(sessionId, basis, at)
        return fresh
    }

    /**
     * Records a move the transition table declined, keeping the window bounded.
     *
     * The session keeps the state it had: prompt section 15 forbids both crashing and swallowing,
     * and inventing a state to make the move legal would be the simulation this product's first
     * principle refuses. Phase 3's window of eight is reused rather than re-decided (ADR-P3-015
     * rule 8), because a list of refusals with no bound is a log buffer.
     */
    private fun recordRefusal(
        session: TrackedDeviceSession,
        held: ConnectionState,
        requested: ConnectionState,
        reason: String?,
        at: Long?,
    ) {
        refusals = (refusals + RefusedSessionMove(session.sessionId, held, requested, reason, at))
            .takeLast(REFUSAL_WINDOW)
    }

    private fun publish(
        stage: SessionEngineStage,
        status: SessionObservationStatus,
        published: List<DeviceSessionEvent>,
        at: Long?,
    ) {
        _snapshot.value = DeviceSessionSnapshot(
            stage = stage,
            sessions = tracked,
            observation = status,
            revision = revision,
            publishedAtEpochMillis = at,
            refusedMoves = refusals,
        )
        for (event in published) {
            // tryEmit, because this flow is declared with a bounded buffer and DROP_OLDEST: an engine
            // that suspended on a consumer that stopped caring would be a consumer holding the state
            // owner hostage (ADR-P4-008).
            _events.tryEmit(event)
        }
    }

    /**
     * ADR-P4-004's table, in code, in one place.
     *
     * Null means "this evidence does not open a session", which is only ever asked of an untracked
     * device. A tracked device returns the state its evidence supports, and never a stronger one:
     * the phone's own radio going quiet is `TEMPORARILY_UNAVAILABLE`, not a disconnect, because
     * prompt section 11 refuses claiming that a device powered off when the only fact is that this
     * handset stopped reporting.
     */
    private fun targetStateOf(report: DeviceObservation, tracked: Boolean): ConnectionState? {
        if (!report.availability.isObservable()) {
            return if (tracked) ConnectionState.TEMPORARILY_UNAVAILABLE else null
        }
        return when (report.link) {
            DeviceConnectionState.CONNECTED -> ConnectionState.CONNECTED
            DeviceConnectionState.CONNECTING -> ConnectionState.DISCOVERED
            DeviceConnectionState.DISCONNECTING -> ConnectionState.TEMPORARILY_UNAVAILABLE
            DeviceConnectionState.DISCONNECTED -> if (tracked) {
                ConnectionState.DISCONNECTED
            } else {
                // A first sighting of a device the platform says is not connected is not a session.
                null
            }

            DeviceConnectionState.UNKNOWN -> when {
                report.bond == DeviceBondState.BONDED -> ConnectionState.PAIRED
                tracked -> ConnectionState.UNKNOWN
                else -> null
            }
        }
    }

    /**
     * Identity from platform evidence only, which in Phase 4 means a reported name or nothing.
     *
     * Manufacturer, model and model id stay null because no Phase 3 report can fill them, and
     * `DeviceIdentity.of` demotes blank text rather than storing it (ADR-P0-016). An earbud is not
     * inferred from a name here, and a session built from a nameless device is still a session
     * (prompt section 7).
     */
    private fun identityOf(report: DeviceObservation): DeviceIdentity =
        DeviceIdentity.of(displayName = report.displayName)

    companion object {
        private const val DEFAULT_SESSION_PREFIX = "session"

        /** Bounded refusal window, matching Phase 3's projection rather than deciding it twice. */
        private const val REFUSAL_WINDOW = 8

        private const val EVENT_REPLAY = 0
        private const val EVENT_BUFFER_CAPACITY = 64
    }
}
