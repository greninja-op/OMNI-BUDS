package com.omnibuds.core.lifecycle

/**
 * Process/application lifecycle phases.
 *
 * Phase 28 (OB-P28-REQ-003): background execution is modeled separately
 * from device readiness. A device may be ready while the app is not
 * permitted to run continuous background work.
 */
enum class LifecyclePhase {
    /** Initial state; nothing known yet. */
    UNKNOWN,

    /** Process started; observers initializing. */
    STARTING,

    /** App in the foreground. */
    FOREGROUND,

    /** App in the background (process alive). */
    BACKGROUND,

    /** Process terminating (best-effort signal). */
    TERMINATING,
}

/**
 * Typed lifecycle events.
 */
sealed interface LifecycleEvent {
    /** Process created; [previousDeath] true when the last process died. */
    data class ProcessStarted(val previousDeath: Boolean) : LifecycleEvent

    /** App entered the foreground. */
    data object ForegroundEntered : LifecycleEvent

    /** App entered the background. */
    data object BackgroundEntered : LifecycleEvent

    /** Bluetooth adapter disabled. */
    data object AdapterDisabled : LifecycleEvent

    /** Bluetooth permission revoked. */
    data object PermissionRevoked : LifecycleEvent

    /** A device disconnected. */
    data class DeviceDisconnected(val deviceId: String) : LifecycleEvent

    /** A device reconnected. */
    data class DeviceReconnected(val deviceId: String) : LifecycleEvent

    /** Process terminating (best-effort). */
    data object ProcessTerminating : LifecycleEvent
}

/**
 * The outcome of applying a lifecycle event.
 */
sealed interface LifecycleTransition {
    /** Valid transition; [from] → [to]. */
    data class Applied(val from: LifecyclePhase, val to: LifecyclePhase) : LifecycleTransition

    /** Event ignored in the current phase (documented no-op). */
    data class Ignored(val phase: LifecyclePhase, val reason: String) : LifecycleTransition

    /** Invalid transition — rejected, never applied. */
    data class Rejected(val phase: LifecyclePhase, val reason: String) : LifecycleTransition
}

/**
 * Deterministic lifecycle state machine.
 *
 * Phase 28 (OB-P28-REQ-003): pure, fully unit-tested. Invalid transitions
 * are rejected, never silently applied.
 */
class LifecycleStateMachine {
    var phase: LifecyclePhase = LifecyclePhase.UNKNOWN
        private set

    /**
     * Apply an event. Returns the transition outcome.
     */
    fun apply(event: LifecycleEvent): LifecycleTransition {
        val from = phase
        return when (event) {
            is LifecycleEvent.ProcessStarted -> transition(
                from, LifecyclePhase.STARTING,
                allowed = from == LifecyclePhase.UNKNOWN,
                rejectReason = "process already started",
            )
            is LifecycleEvent.ForegroundEntered -> transition(
                from, LifecyclePhase.FOREGROUND,
                allowed = from == LifecyclePhase.STARTING || from == LifecyclePhase.BACKGROUND,
                rejectReason = "foreground requires starting or background",
            )
            is LifecycleEvent.BackgroundEntered -> transition(
                from, LifecyclePhase.BACKGROUND,
                allowed = from == LifecyclePhase.FOREGROUND,
                rejectReason = "background requires foreground",
            )
            is LifecycleEvent.ProcessTerminating -> transition(
                from, LifecyclePhase.TERMINATING,
                allowed = from != LifecyclePhase.TERMINATING,
                rejectReason = "already terminating",
            )
            // Device/adapter/permission events don't change the process phase;
            // they are signals for the coordinator, not phase transitions.
            is LifecycleEvent.AdapterDisabled,
            is LifecycleEvent.PermissionRevoked,
            is LifecycleEvent.DeviceDisconnected,
            is LifecycleEvent.DeviceReconnected,
                -> LifecycleTransition.Ignored(from, "signal event; no phase change")
        }
    }

    private fun transition(
        from: LifecyclePhase,
        to: LifecyclePhase,
        allowed: Boolean,
        rejectReason: String,
    ): LifecycleTransition {
        if (!allowed) return LifecycleTransition.Rejected(from, rejectReason)
        phase = to
        return LifecycleTransition.Applied(from, to)
    }

    /** Reset for tests. */
    fun reset() {
        phase = LifecyclePhase.UNKNOWN
    }
}
