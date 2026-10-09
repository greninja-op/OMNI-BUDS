package com.omnibuds.core.verification

/**
 * Events fed into the verification state machine.
 *
 * Phase 18 (OB-P18-REQ-010): the framework processes outcomes; the caller
 * drives the protocol port. Events are the boundary.
 */
sealed interface VerificationEvent {

    /** Eligibility check completed. */
    data class EligibilityDetermined(
        val eligible: Boolean,
        val reason: String,
    ) : VerificationEvent

    /** Baseline device state captured (or unavailable). */
    data class BaselineCaptured(
        val baseline: com.omnibuds.core.config.ConfigurationValue?,
        val unavailableReason: String?,
    ) : VerificationEvent

    /** Apply was requested through the control interface. */
    data object ApplyRequested : VerificationEvent

    /** Acknowledgement received. */
    data class Acknowledged(
        val correlationId: String?,
    ) : VerificationEvent

    /** Command was rejected. */
    data class Rejected(
        val reason: String,
    ) : VerificationEvent

    /** Read-back returned a value. */
    data class ReadBackReceived(
        val observed: com.omnibuds.core.config.ConfigurationValue?,
        val malformed: Boolean,
        val sessionId: String?,
    ) : VerificationEvent

    /** A lifecycle boundary was observed (session end, disconnect, etc.). */
    data class LifecycleBoundaryObserved(
        val boundary: LifecycleBoundary,
    ) : VerificationEvent

    /** Post-boundary read-back received. */
    data class PostBoundaryReadBack(
        val boundary: LifecycleBoundary,
        val observed: com.omnibuds.core.config.ConfigurationValue?,
        val sessionId: String?,
    ) : VerificationEvent

    /** Operation timed out (ambiguous outcome). */
    data class TimedOut(
        val waitingFor: String,
    ) : VerificationEvent

    /** Cancelled by caller or lifecycle. */
    data object Cancelled : VerificationEvent

    /** Device disconnected during operation. */
    data class DeviceDisconnected(
        val sessionId: String?,
    ) : VerificationEvent
}

/** Lifecycle boundaries the framework can observe (never manufacture). */
enum class LifecycleBoundary {
    CONTROL_SESSION_END,
    DISCONNECT_RECONNECT,
    APPLICATION_RESTART,
    DEVICE_POWER_CYCLE,
}
