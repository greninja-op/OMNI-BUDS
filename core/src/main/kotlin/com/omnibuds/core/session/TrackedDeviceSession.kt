package com.omnibuds.core.session

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceSession
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.ConnectionStateTransitions

/**
 * One device session, and the association Phase 1 left for this phase to carry.
 *
 * Phase 1's `validation.md` known issue 5 records the gap in as many words: "`DeviceSession` and
 * `DeviceState` join only on `sessionId` ... Phase 4's session engine should carry the
 * association." Nothing prevented a `DeviceState` being paired with the wrong session, because no
 * type held the pair. This does: the pair, the key the pair was attributed by, the engine's own
 * timeline, and nothing else.
 *
 * **What it deliberately does not hold.** A second connection state. `DeviceState.connection` is
 * the only place where where-a-device-has-got-to lives (ADR-P4-002), which is the same rule Phase
 * 1 applied when it deleted the duplicate field from [DeviceSession] (`device/DeviceSession.kt:9-15`),
 * and the reason [connectionState] below is a read-through rather than a field. A session cannot
 * say it is connected while its state says it is not, because there is only one place to say it.
 *
 * [state] is updated only through [movedTo], and [movedTo] only through
 * [DeviceState.attemptConnection], so an illegal move is a returned failure rather than a value
 * that exists. That is not the confinement Phase 24 owns - `copy(connection = ...)` still compiles
 * for anyone holding the record, and ADR-P4-001 says so out loud - it is the audit trail that
 * makes an end-run visible in a diff.
 *
 * [sessionId] is minted by the engine and carries no device content: not an address, not a name,
 * not a hash of either (ADR-P4-005, SEC-ID-003). [key] is the typed Phase 3 key, held as the key
 * and never read out as text, so a printed session cannot leak an identifier it never had.
 */
data class TrackedDeviceSession(
    val sessionId: String,
    val key: DeviceObservationKey,
    val basis: SessionIdentityBasis,
    val session: DeviceSession,
    val state: DeviceState,
    val timeline: SessionTimeline,
) {
    init {
        require(session.sessionId == state.sessionId && session.sessionId == sessionId) {
            "a session record whose three ids disagree is the pairing defect this type exists to prevent"
        }
        require(session.identity == state.identity) {
            "identity is held once per session; a diverging copy in the state is the same defect"
        }
    }

    /** Read-through to the one authoritative connection fact. Never a copy of it. */
    val connectionState: ConnectionState
        get() = state.connection

    /** The name the platform last reported, or null. Never used to attribute anything. */
    val displayName: String?
        get() = session.identity.displayName

    /** Connected right now, on the platform's own report, and not merely seen recently. */
    val isActive: Boolean
        get() = connectionState == ConnectionState.CONNECTED

    /**
     * A device the engine is holding in its one-round grace after a proven disconnect.
     *
     * Every record in the store is live by definition - the engine drops a terminated session in the
     * same round it publishes the end, so there is no ended-record flag to consult and no field with
     * no producer pretending there is.
     */
    val isAwaitingGraceExpiry: Boolean
        get() = connectionState == ConnectionState.DISCONNECTED

    /** True only where the user explicitly kept this device - false for every session Phase 4 makes. */
    val isSaved: Boolean
        get() = session.isSaved

    /** Replaces the authoritative state, leaving identity, basis and timeline untouched. */
    fun withState(next: DeviceState): TrackedDeviceSession = copy(state = next)

    /**
     * Applies newly reported identity to both records at once.
     *
     * [DeviceState] and [DeviceSession] each carry an identity, and the invariant above demands they
     * agree, so this is the only way an engine may change either: one call, both records, no window
     * in which a published snapshot holds two different answers about which device this is
     * (ADR-P4-002). [next] must come from [DeviceIdentity.mergedWith], which fills gaps and
     * restates nothing, so an identity can grow but never be rewritten from here.
     */
    fun withMergedIdentity(next: com.omnibuds.core.device.DeviceIdentity): TrackedDeviceSession = copy(
        session = session.copy(identity = next),
        state = state.withIdentity(next),
    )

    /** Restates freshness from the round that carried this device. */
    fun restatedAt(atEpochMillis: Long?): TrackedDeviceSession =
        copy(timeline = timeline.restatedAt(atEpochMillis))

    /** Records the disconnect moment, which is what starts the grace window. */
    fun markedDisconnected(atEpochMillis: Long?): TrackedDeviceSession =
        copy(timeline = timeline.markedDisconnected(atEpochMillis))

    /** Clears the disconnect mark on a resume inside the grace (ADR-P4-006). */
    fun resumed(): TrackedDeviceSession = copy(timeline = timeline.resumed())

    companion object {
        /**
         * Moves the session's connection through the Phase 1 transition table and reports
         * the refusal rather than performing it.
         *
         * The returned failure is [DeviceState]`s` own - `INVALID_STATE` with the table's
         * reason in the detail - because the table is the authority on what a move means and
         * this layer has no business re-explaining it. Callers record the refusal; nothing in
         * this type can reach [ConnectionState.IDENTIFYING],
         * [ConnectionState.CAPABILITY_DISCOVERY], [ConnectionState.READY] or
         * [ConnectionState.CONTROL_SESSION] by accident, which is what ADR-P4-003 pins.
         */
        fun moving(
            current: TrackedDeviceSession,
            next: ConnectionState,
            atEpochMillis: Long?,
        ): OperationOutcome<TrackedDeviceSession> =
            current.state.attemptConnection(next, atEpochMillis).mapState(current)

        /** The states this engine will accept as a move target, stated for the guard to test against. */
        val reachableStates: Set<ConnectionState> = ConnectionStateTransitions
            .allowedNext(ConnectionState.UNKNOWN)
            .filter { state -> state != ConnectionState.ERROR }
            .toSet() + ConnectionState.UNKNOWN

        private fun OperationOutcome<DeviceState>.mapState(
            current: TrackedDeviceSession,
        ): OperationOutcome<TrackedDeviceSession> = when (this) {
            is OperationOutcome.Success -> OperationOutcome.Success(current.withState(value))
            is OperationOutcome.Failure -> OperationOutcome.Failure(error)
            OperationOutcome.Cancelled -> OperationOutcome.Cancelled
        }
    }
}
