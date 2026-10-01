package com.omnibuds.core.platform

/**
 * One reported adapter state, with how it was obtained.
 *
 * The distinction matters for honesty downstream. A value from [PLATFORM_READ] is a snapshot taken
 * when the app asked; [PLATFORM_EVENT] means the platform announced a transition; [INITIAL_READ]
 * and [INITIAL_EVENT] mark the first value of a new observation so a consumer can tell "this is
 * where we started" from "this changed just now". [DEDUPED] is never emitted - it exists so the
 * observer's duplicate suppression is expressible and testable rather than invisible.
 */
enum class ObservationKind {
    INITIAL_READ,
    INITIAL_EVENT,
    PLATFORM_READ,
    PLATFORM_EVENT,
    DEDUPED,
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
