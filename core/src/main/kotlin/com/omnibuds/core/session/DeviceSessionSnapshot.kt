package com.omnibuds.core.session

import com.omnibuds.core.state.ConnectionState

/**
 * The one published value the session engine owns.
 *
 * Prompt section 12 sketches three flows - `sessions`, `activeSessions`, `sessionEvents`. Two
 * authoritative state flows would be two owners of one truth, which is Phase 1's section 24
 * failure arriving with a friendlier name, so this phase publishes one
 * `StateFlow<DeviceSessionSnapshot>` and derives the views from it (ADR-P4-001's consequence,
 * stated as a divergence rather than hidden in a constructor). [activeSessions] is a filter over
 * [sessions] computed at read time; nothing can update one and forget the other.
 *
 * [revision] is the engine's own monotonic counter, incremented on every published change. It is
 * not a device fact and not a clock reading: it is what lets a consumer and a test say that a
 * value it is holding is older than the engine's current one, which is how prompt section 14's
 * "an old event must not overwrite newer state" is enforced without comparing timestamps at all
 * (ADR-P4-009).
 *
 * [observation] is why an empty [sessions] list might be empty. Reading the list length without
 * reading that field is the confusion prompt section 12 forbids, and the type is shaped so that
 * doing it takes an extra deliberate step.
 */
data class DeviceSessionSnapshot(
    val stage: SessionEngineStage,
    val sessions: List<TrackedDeviceSession>,
    val observation: SessionObservationStatus,
    val revision: Long,
    val publishedAtEpochMillis: Long?,

    /**
     * Moves the transition table refused, newest last, window bounded.
     *
     * Prompt section 15 forbids swallowing an error, and a refused move is not an error the caller
     * can do anything about - it is the engine reporting that the platform's evidence asked for a
     * state the table does not allow. So it is recorded here rather than thrown or dropped, which
     * is Phase 3's rule 8 (ADR-P3-015) applied to sessions: a fact about the observation belongs
     * with the observation, and an unbounded list of them is a log buffer.
     */
    val refusedMoves: List<RefusedSessionMove> = emptyList(),
) {
    /** The sessions the platform currently reports a live link to. Derived, never stored. */
    val activeSessions: List<TrackedDeviceSession>
        get() = sessions.filter { tracked -> tracked.isActive }

    /** Sessions held in the one-round grace after a proven disconnect, still publishable. */
    val disconnectPendingSessions: List<TrackedDeviceSession>
        get() = sessions.filter { tracked -> tracked.isAwaitingGraceExpiry }

    /** Sessions the platform named but could not identify, which never match a later round. */
    val ambiguousSessions: List<TrackedDeviceSession>
        get() = sessions.filter { tracked -> tracked.basis == SessionIdentityBasis.AMBIGUOUS }

    /** True only where an empty [sessions] list is a real census rather than an unanswered question. */
    val isEmptyMeaningful: Boolean
        get() = observation.isEmptyMeaningful

    companion object {
        /** The value before anything has been applied: no sessions, no observation, revision zero. */
        fun notStarted(): DeviceSessionSnapshot = DeviceSessionSnapshot(
            stage = SessionEngineStage.NOT_STARTED,
            sessions = emptyList(),
            observation = SessionObservationStatus.Unread,
            revision = 0L,
            publishedAtEpochMillis = null,
            refusedMoves = emptyList(),
        )
    }
}

/**
 * One move the engine asked for and [ConnectionStateTransitions] declined.
 *
 * [requested] is what the observation's evidence pointed at and [held] is the state the session
 * kept, so the record says what was believed rather than merely that something failed. The
 * sessionId is the engine's own opaque label, so a printed refusal contains no device identifier
 * (ADR-P4-005).
 */
data class RefusedSessionMove(
    val sessionId: String,
    val held: ConnectionState,
    val requested: ConnectionState,
    val reason: String?,
    val atEpochMillis: Long?,
)
