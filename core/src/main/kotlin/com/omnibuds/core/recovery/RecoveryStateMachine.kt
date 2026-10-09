package com.omnibuds.core.recovery

/**
 * Recovery lifecycle states.
 *
 * Phase 34: integrates with the device-session lifecycle rather than
 * replacing it. A recovery state never reports a device READY — that
 * remains the session engine's job.
 */
enum class RecoveryState {
    IDLE,
    FAILURE_CLASSIFIED,
    RECOVERY_PLANNED,
    WAITING_FOR_PRECONDITION,
    RETRY_SCHEDULED,
    RECONNECTING,
    SESSION_RECREATING,
    STATE_RECONCILIATION,
    RECOVERY_SUCCEEDED,
    RECOVERY_EXHAUSTED,
    RECOVERY_ABORTED,
    RECOVERY_CANCELLED,
}

/**
 * Explicit recovery state machine with guarded transitions.
 */
class RecoveryStateMachine {

    private var state: RecoveryState = RecoveryState.IDLE

    val current: RecoveryState get() = state

    private val transitions: Map<RecoveryState, Set<RecoveryState>> = mapOf(
        RecoveryState.IDLE to setOf(
            RecoveryState.FAILURE_CLASSIFIED,
        ),
        RecoveryState.FAILURE_CLASSIFIED to setOf(
            RecoveryState.RECOVERY_PLANNED,
            RecoveryState.RECOVERY_ABORTED,
        ),
        RecoveryState.RECOVERY_PLANNED to setOf(
            RecoveryState.WAITING_FOR_PRECONDITION,
            RecoveryState.RETRY_SCHEDULED,
            RecoveryState.RECONNECTING,
            RecoveryState.SESSION_RECREATING,
            RecoveryState.STATE_RECONCILIATION,
            RecoveryState.RECOVERY_ABORTED,
            RecoveryState.RECOVERY_CANCELLED,
        ),
        RecoveryState.WAITING_FOR_PRECONDITION to setOf(
            RecoveryState.RETRY_SCHEDULED,
            RecoveryState.RECONNECTING,
            RecoveryState.RECOVERY_ABORTED,
            RecoveryState.RECOVERY_CANCELLED,
            RecoveryState.RECOVERY_EXHAUSTED,
        ),
        RecoveryState.RETRY_SCHEDULED to setOf(
            RecoveryState.FAILURE_CLASSIFIED,
            RecoveryState.RECOVERY_SUCCEEDED,
            RecoveryState.RECOVERY_EXHAUSTED,
            RecoveryState.RECOVERY_CANCELLED,
        ),
        RecoveryState.RECONNECTING to setOf(
            RecoveryState.FAILURE_CLASSIFIED,
            RecoveryState.RECOVERY_SUCCEEDED,
            RecoveryState.RECOVERY_EXHAUSTED,
            RecoveryState.RECOVERY_CANCELLED,
        ),
        RecoveryState.SESSION_RECREATING to setOf(
            RecoveryState.FAILURE_CLASSIFIED,
            RecoveryState.RECOVERY_SUCCEEDED,
            RecoveryState.RECOVERY_EXHAUSTED,
            RecoveryState.RECOVERY_CANCELLED,
        ),
        RecoveryState.STATE_RECONCILIATION to setOf(
            RecoveryState.RECOVERY_PLANNED,
            RecoveryState.RECOVERY_SUCCEEDED,
            RecoveryState.RECOVERY_ABORTED,
            RecoveryState.RECOVERY_CANCELLED,
        ),
        // Terminal states: only back to IDLE for a new episode.
        RecoveryState.RECOVERY_SUCCEEDED to setOf(RecoveryState.IDLE),
        RecoveryState.RECOVERY_EXHAUSTED to setOf(RecoveryState.IDLE),
        RecoveryState.RECOVERY_ABORTED to setOf(RecoveryState.IDLE),
        RecoveryState.RECOVERY_CANCELLED to setOf(RecoveryState.IDLE),
    )

    /**
     * Attempt a transition. Returns true when legal and applied.
     */
    fun transition(to: RecoveryState): Boolean {
        val allowed = transitions[state].orEmpty()
        if (to !in allowed) return false
        state = to
        return true
    }

    /** Reset to IDLE; only legal from a terminal state. */
    fun reset(): Boolean = transition(RecoveryState.IDLE)
}
