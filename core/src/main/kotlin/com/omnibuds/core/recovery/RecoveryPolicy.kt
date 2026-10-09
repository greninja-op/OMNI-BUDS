package com.omnibuds.core.recovery

import com.omnibuds.core.common.RetryClass

/**
 * What the recovery policy may decide.
 *
 * A decision is not permission to execute a hardware command — the
 * existing authorization and capability policy must still approve.
 */
enum class RecoveryDecision {
    /** Nothing to do. */
    NO_ACTION,

    /** Retry the failed operation (idempotent reads/discovery only). */
    RETRY_OPERATION,

    /** Retry the connection under the bounded ReconnectPolicy. */
    RETRY_CONNECTION,

    /** Tear down and recreate the session. */
    RECREATE_SESSION,

    /** Re-check permissions before any further work. */
    REVALIDATE_PERMISSIONS,

    /** Wait for the adapter; do not poll or retry. */
    WAIT_FOR_ADAPTER,

    /** Reconcile device state before proceeding. */
    RECONCILE_DEVICE_STATE,

    /** Reload persisted configuration. */
    RELOAD_PERSISTED_CONFIGURATION,

    /** Give up on this operation; report a recoverable failure. */
    ABORT_OPERATION,

    /** The user must intervene (e.g. re-pair, grant permission). */
    REQUIRE_USER_INTERVENTION,

    /** Mark the session unavailable; no automatic recovery. */
    MARK_SESSION_UNAVAILABLE,
}

/**
 * Inputs to the recovery policy. Explicit, no hidden global state.
 */
data class RecoveryContext(
    val failure: ClassifiedFailure,
    /** Attempts already made for this operation. */
    val attemptCount: Int,
    val maxAttempts: Int,
    val bluetoothEnabled: Boolean?,
    val permissionGranted: Boolean?,
    val backgroundRestricted: Boolean,
    val cancelled: Boolean,
    /** True when the session was superseded by a newer one. */
    val sessionSuperseded: Boolean,
    /** True when authorization/capability revalidation passed. */
    val authorizationValid: Boolean,
)

/**
 * Deterministic recovery policy.
 *
 * Phase 34: every rule is explicit and unit-testable. No unbounded
 * retries, no blind replays, no reconnecting while Bluetooth is off,
 * no permission retry storms.
 */
object RecoveryPolicy {

    fun decide(context: RecoveryContext): RecoveryDecision {
        val failure = context.failure

        // Cancellation always wins.
        if (context.cancelled || failure.category == FailureCategory.CANCELLED) {
            return RecoveryDecision.ABORT_OPERATION
        }

        // A superseded session must not mutate state.
        if (context.sessionSuperseded) {
            return RecoveryDecision.ABORT_OPERATION
        }

        // Ambiguous writes: never blindly replay.
        if (failure.mayHaveExecuted &&
            failure.retryClass != RetryClass.SAFE_TO_RETRY
        ) {
            return RecoveryDecision.RECONCILE_DEVICE_STATE
        }

        return when (failure.category) {
            FailureCategory.PERMISSION_DENIED ->
                RecoveryDecision.REVALIDATE_PERMISSIONS
            FailureCategory.BLUETOOTH_DISABLED,
            FailureCategory.BLUETOOTH_UNAVAILABLE,
            -> RecoveryDecision.WAIT_FOR_ADAPTER
            FailureCategory.DEVICE_DISCONNECTED,
            FailureCategory.CONNECTION_TIMEOUT,
            FailureCategory.TRANSPORT_UNAVAILABLE,
            FailureCategory.TRANSPORT_FAILURE,
            -> if (context.bluetoothEnabled == false) {
                RecoveryDecision.WAIT_FOR_ADAPTER
            } else {
                RecoveryDecision.RETRY_CONNECTION
            }
            FailureCategory.PROTOCOL_TIMEOUT,
            FailureCategory.DEVICE_BUSY,
            FailureCategory.RESOURCE_EXHAUSTED,
            -> decideRetry(context)
            FailureCategory.PROTOCOL_MALFORMED_RESPONSE,
            FailureCategory.PROTOCOL_UNSUPPORTED,
            FailureCategory.OPERATION_REJECTED,
            FailureCategory.CAPABILITY_UNAVAILABLE,
            -> RecoveryDecision.ABORT_OPERATION
            FailureCategory.OPERATION_OUTCOME_UNKNOWN,
            FailureCategory.STALE_STATE,
            -> RecoveryDecision.RECONCILE_DEVICE_STATE
            FailureCategory.PERSISTENCE_FAILURE ->
                RecoveryDecision.RELOAD_PERSISTED_CONFIGURATION
            FailureCategory.BACKGROUND_RESTRICTED ->
                RecoveryDecision.MARK_SESSION_UNAVAILABLE
            FailureCategory.INTERNAL_ERROR ->
                RecoveryDecision.ABORT_OPERATION
            FailureCategory.CANCELLED ->
                RecoveryDecision.ABORT_OPERATION
        }
    }

    private fun decideRetry(context: RecoveryContext): RecoveryDecision {
        if (!context.authorizationValid) return RecoveryDecision.ABORT_OPERATION
        if (context.backgroundRestricted) {
            return RecoveryDecision.MARK_SESSION_UNAVAILABLE
        }
        if (context.attemptCount >= context.maxAttempts) {
            return RecoveryDecision.REQUIRE_USER_INTERVENTION
        }
        return RecoveryDecision.RETRY_OPERATION
    }
}
