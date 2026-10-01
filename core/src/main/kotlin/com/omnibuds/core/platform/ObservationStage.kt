package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError

/**
 * Where the connected-device observer's lifecycle is, and nothing else.
 *
 * Prompt section 14 asks an API to distinguish "not started", "observing", "permission required",
 * "bluetooth disabled", "unsupported" and "stopped". Three of those are lifecycle and three are
 * failures, and they are kept apart on purpose (ADR-P3-005): a stage that can say `PERMISSION_DENIED`
 * is a stage that can say `OBSERVING`, and a consumer then has to guess which question to trust.
 * The failures live in `OperationOutcome` with their existing categories, which is the same
 * separation `AdapterStateObserver` already makes between a state and a reason it could not be read
 * (ADR-P0-016).
 *
 * `STOPPED` is a stage rather than a failure because a deliberately ended observation is a
 * successful thing to have done, and an engine that reports its own shutdown as an error teaches a
 * UI to show a problem that is not one.
 */
enum class ObservationStage {
    /** Created, never started. */
    NOT_STARTED,

    /** Running: a snapshot has been taken and updates are being accepted. */
    OBSERVING,

    /** Ended by request or by cancellation. A restart is legal and is not a repair. */
    STOPPED,
}

/** True only while updates are being accepted, so a restart can be told from a first start. */
fun ObservationStage.isActive(): Boolean = this == ObservationStage.OBSERVING

/**
 * The outcome of one observation round, kept separate from the stage that produced it.
 *
 * Prompt section 14's rule in type form: an empty device list may only ever arrive inside a
 * [ObservationRound.Success]. A refused permission, a dead adapter or a throwing platform call
 * produces a [Failure] with no snapshot at all, so "nobody is connected" and "we were not told"
 * cannot be confused by reading the list length - which is exactly the confusion Phase 2 recorded
 * for falsy framework returns (ADR-P2-012).
 *
 * `Cancelled` is a case rather than a category and stays one (ADR-P1-004, ADR-P2-018).
 */
sealed interface ObservationRound<out T> {
    /** The round answered. An empty [devices] here is a real, reportable fact. */
    data class Success<T>(val devices: List<T>, val stage: ObservationStage) : ObservationRound<T>

    /** The round did not answer. There is no device list to interpret. */
    data class Failure<T>(val error: OmniBudsError) : ObservationRound<T>

    /** The round was called off. Not a failure, and never a success with an empty list. */
    data object Cancelled : ObservationRound<Nothing>
}
