package com.omnibuds.core.platform

/**
 * One reported adapter state, with how it was obtained.
 *
 * The distinction matters for honesty downstream. A value from [PLATFORM_READ] is a snapshot taken
 * when the app asked; [PLATFORM_EVENT] means the platform announced a transition; [INITIAL_READ] and
 * [INITIAL_EVENT] mark the value a new observation started from - read, when the read answered, or
 * announced, when it did not - so a consumer can tell "this is where we started" from "this changed
 * just now". [INITIAL_EVENT] therefore never follows a successful [INITIAL_READ] (audit finding R-8).
 *
 * No member names a suppressed duplicate. Consecutive repeats are dropped before they reach a
 * consumer, so no value is ever labelled as one, and an enum entry nothing can produce is vocabulary
 * standing in for a capability (ADR-P2-007).
 */
enum class ObservationKind {
    INITIAL_READ,
    INITIAL_EVENT,
    PLATFORM_READ,
    PLATFORM_EVENT,
}

/**
 * A single adapter-state observation.
 *
 * [observedAtEpochMillis] is null when the platform supplied no clock reading, which is rendered as
 * "time unknown" rather than zero (ADR-P0-016).
 */
data class AdapterStateObservation(
    val state: BluetoothAdapterState,
    val kind: ObservationKind,
    val observedAtEpochMillis: Long?,
) {
    /** Whether this observation is enough to act on the adapter. */
    val isUsable: Boolean
        get() = state.isUsable()
}
