package com.omnibuds.core.quality

/**
 * The normalized audio negotiation lifecycle.
 *
 * Phase 13 (OB-P13-REQ-003): nine states with deterministic transitions
 * ([NegotiationTransitions]). This models what OmniBuds can *observe* about
 * negotiation — the platform does not expose actual negotiation protocol
 * events, so states are derived from observable transport/codec transitions,
 * never from invented protocol insight.
 *
 * - [UNKNOWN]: nothing is known about this device's negotiation.
 * - [IDLE]: device known, no audio activity.
 * - [PREPARING]: audio device available, negotiation not yet observed.
 * - [NEGOTIATING]: codec capabilities being reported / negotiation in flight.
 * - [NEGOTIATED]: a codec reached the NEGOTIATED rung (not yet ACTIVE).
 * - [ACTIVE]: a codec is ACTIVE and the route is live.
 * - [FAILED]: negotiation failed (explicit failure evidence).
 * - [DISCONNECTED]: the device disconnected; terminal for the session.
 * - [STALE]: the last known state is no longer current.
 */
enum class NegotiationState {
    UNKNOWN,
    IDLE,
    PREPARING,
    NEGOTIATING,
    NEGOTIATED,
    ACTIVE,
    FAILED,
    DISCONNECTED,
    STALE,
}

/**
 * The legal negotiation transitions.
 *
 * Phase 13 (OB-P13-REQ-003): transitions are deterministic. Anything not in
 * this table is rejected (the engine keeps the current state and records a
 * diagnostic) rather than silently applied.
 */
object NegotiationTransitions {

    private val legal: Map<NegotiationState, Set<NegotiationState>> = mapOf(
        NegotiationState.UNKNOWN to setOf(
            NegotiationState.IDLE,
            NegotiationState.DISCONNECTED,
        ),
        NegotiationState.IDLE to setOf(
            NegotiationState.PREPARING,
            NegotiationState.DISCONNECTED,
            NegotiationState.STALE,
        ),
        NegotiationState.PREPARING to setOf(
            NegotiationState.NEGOTIATING,
            NegotiationState.FAILED,
            NegotiationState.DISCONNECTED,
            NegotiationState.STALE,
            NegotiationState.IDLE,
        ),
        NegotiationState.NEGOTIATING to setOf(
            NegotiationState.NEGOTIATED,
            NegotiationState.FAILED,
            NegotiationState.DISCONNECTED,
            NegotiationState.STALE,
        ),
        NegotiationState.NEGOTIATED to setOf(
            NegotiationState.ACTIVE,
            NegotiationState.NEGOTIATING,
            NegotiationState.FAILED,
            NegotiationState.DISCONNECTED,
            NegotiationState.STALE,
        ),
        NegotiationState.ACTIVE to setOf(
            NegotiationState.NEGOTIATING,
            NegotiationState.STALE,
            NegotiationState.DISCONNECTED,
            NegotiationState.IDLE,
        ),
        NegotiationState.FAILED to setOf(
            NegotiationState.PREPARING,
            NegotiationState.IDLE,
            NegotiationState.DISCONNECTED,
            NegotiationState.STALE,
        ),
        NegotiationState.STALE to setOf(
            NegotiationState.PREPARING,
            NegotiationState.IDLE,
            NegotiationState.DISCONNECTED,
            NegotiationState.UNKNOWN,
        ),
        NegotiationState.DISCONNECTED to setOf(
            NegotiationState.IDLE,
            NegotiationState.UNKNOWN,
        ),
    )

    /** True if the engine may move from [from] to [to]. */
    fun isLegal(from: NegotiationState, to: NegotiationState): Boolean {
        if (from == to) return true
        return legal[from]?.contains(to) == true
    }
}
