package com.omnibuds.android.compat

/**
 * Permission states, as distinguishable on the platform.
 *
 * Phase 32: denied ≠ unavailable ≠ restricted ≠ unknown. A denial is a
 * user decision; unavailable means the API level has no such permission;
 * restricted means the platform policy blocks it; unknown means the
 * state could not be determined reliably.
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    UNAVAILABLE,
    RESTRICTED,
    UNKNOWN,
}

/**
 * The decision for a protected operation given permission state.
 */
sealed interface PermissionDecision {
    /** Proceed with the operation. */
    data object Allow : PermissionDecision

    /** Refuse; [reason] explains why. Never crash, never bypass. */
    data class Refuse(val reason: String) : PermissionDecision

    /** Defer; the state is unknown and must be rechecked. */
    data class Defer(val reason: String) : PermissionDecision
}

/**
 * Pure permission-policy decisions.
 *
 * Phase 32: no crash on missing permission, no silent bypass, no
 * prompt loops (the policy decides; prompting is a UI concern).
 * A grant never proves a Bluetooth operation will succeed.
 */
object PermissionPolicy {

    /**
     * Decide whether a protected operation may proceed.
     */
    fun decide(
        state: PermissionState,
        operation: String,
    ): PermissionDecision = when (state) {
        PermissionState.GRANTED -> PermissionDecision.Allow
        PermissionState.DENIED ->
            PermissionDecision.Refuse("$operation refused: permission denied by the user")
        PermissionState.UNAVAILABLE ->
            PermissionDecision.Refuse("$operation refused: permission unavailable on this API level")
        PermissionState.RESTRICTED ->
            PermissionDecision.Refuse("$operation refused: permission restricted by platform policy")
        PermissionState.UNKNOWN ->
            PermissionDecision.Defer("$operation deferred: permission state unknown; recheck required")
    }

    /**
     * Classify a SecurityException from a platform call.
     */
    fun classifySecurityException(operation: String): PermissionDecision =
        PermissionDecision.Refuse("$operation refused: platform threw SecurityException")
}
