package com.omnibuds.core.lifecycle

/**
 * Bounded reconnection policy.
 *
 * Phase 28 (OB-P28-REQ-006): conservative, documented. No endless loops,
 * no background scanning. Every decision is deterministic and testable.
 */
class ReconnectPolicy(
    /** Maximum automatic attempts per device per disconnection episode. */
    val maxAttempts: Int = 3,
    /** Base backoff in milliseconds. */
    val baseBackoffMillis: Long = 5_000L,
    /** Maximum backoff in milliseconds. */
    val maxBackoffMillis: Long = 60_000L,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
        require(baseBackoffMillis > 0) { "baseBackoffMillis must be > 0" }
    }

    /**
     * Decide whether an automatic reconnect attempt is allowed.
     */
    fun shouldAttempt(context: ReconnectContext): ReconnectDecision {
        if (!context.trigger.isAutomaticEligible) {
            return ReconnectDecision.Denied("trigger not eligible for automatic reconnect")
        }
        if (context.bluetoothEnabled != true) {
            return ReconnectDecision.Denied("bluetooth disabled")
        }
        if (context.permissionGranted != true) {
            return ReconnectDecision.Denied("permission not granted")
        }
        if (context.deviceUnpaired == true) {
            return ReconnectDecision.Denied("device unpaired")
        }
        if (context.attemptCount >= maxAttempts) {
            return ReconnectDecision.Denied("max attempts ($maxAttempts) reached")
        }
        if (context.userInitiated) {
            return ReconnectDecision.Allowed(backoffMillis = 0L, reason = "user-initiated")
        }
        val backoff = minOf(
            baseBackoffMillis * (1L shl context.attemptCount.coerceAtMost(10)),
            maxBackoffMillis,
        )
        return ReconnectDecision.Allowed(backoff, "automatic attempt ${context.attemptCount + 1}")
    }
}

/**
 * The trigger for a reconnect decision.
 */
enum class ReconnectTrigger {
    /** Trustworthy platform connection event. */
    PLATFORM_EVENT,

    /** User explicitly requested reconnection. */
    USER_REQUEST,

    /** Process restarted with a previously connected device. */
    PROCESS_RESTART,

    /** Periodic timer — never eligible for automatic reconnect. */
    PERIODIC_TIMER,

    /** Unknown/unspecified — never eligible. */
    UNKNOWN,
}

/** Whether a trigger may start an automatic reconnect. */
val ReconnectTrigger.isAutomaticEligible: Boolean
    get() = this == ReconnectTrigger.PLATFORM_EVENT ||
        this == ReconnectTrigger.USER_REQUEST ||
        this == ReconnectTrigger.PROCESS_RESTART

/**
 * Context for a reconnect decision.
 */
data class ReconnectContext(
    val trigger: ReconnectTrigger,
    val attemptCount: Int,
    val bluetoothEnabled: Boolean?,
    val permissionGranted: Boolean?,
    val deviceUnpaired: Boolean?,
    val userInitiated: Boolean = false,
)

/**
 * Reconnect decisions.
 */
sealed interface ReconnectDecision {
    /** Attempt allowed; [backoffMillis] delay before the attempt. */
    data class Allowed(val backoffMillis: Long, val reason: String) : ReconnectDecision

    /** Attempt denied; [reason] explains why. */
    data class Denied(val reason: String) : ReconnectDecision
}
