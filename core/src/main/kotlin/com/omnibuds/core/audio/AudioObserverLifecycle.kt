package com.omnibuds.core.audio

/**
 * The lifecycle of the audio transport observer.
 *
 * Observation has an explicit lifecycle because its resources do: Bluetooth
 * profile proxies and audio-device callbacks are platform registrations that
 * leak when start/stop is sloppy (OB-P10-REQ-017). The states and their legal
 * transitions are:
 *
 * ```
 * STOPPED --start--> STARTING --ready--> OBSERVING --stop--> STOPPING --done--> STOPPED
 *    ^                  |                                                |
 *    +---- start fails -+----- stop fails (still STOPPED, error reported) -+
 * ```
 *
 * - Repeated `start()` while [OBSERVING] or [STARTING] is a no-op success, not
 *   a second registration: duplicate callbacks are the leak this state machine
 *   exists to prevent.
 * - Repeated `stop()` while [STOPPED] or [STOPPING] is a no-op success.
 * - A failed start returns to [STOPPED] with the error reported; the observer
 *   never sits in a half-registered state pretending to observe.
 * - There is no FAILED state: failure is an [com.omnibuds.core.common.OperationOutcome]
 *   on the start call, not a resting state (ADR-P1-004).
 */
enum class AudioObserverLifecycle {
    STOPPED,
    STARTING,
    OBSERVING,
    STOPPING,
}

fun AudioObserverLifecycle.canStart(): Boolean =
    this == AudioObserverLifecycle.STOPPED

fun AudioObserverLifecycle.canStop(): Boolean =
    this == AudioObserverLifecycle.OBSERVING || this == AudioObserverLifecycle.STARTING
