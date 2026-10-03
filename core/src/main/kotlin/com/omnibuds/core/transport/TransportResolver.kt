package com.omnibuds.core.transport

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel

/**
 * Which control channels a device is a candidate for, decided without deciding to connect.
 *
 * Prompt §12 asks for a future-compatible resolution contract and is emphatic that Phase 6 must establish
 * the *contract and the safe-unknown behaviour*, not a selection policy: it "must not implement
 * manufacturer-specific transport-selection rules" and "must not automatically connect to every candidate
 * transport." [TransportNegotiation] already models offer-vs-selection with no silent fallthrough, so
 * resolution is expressed over it rather than beside it. The honest Phase 6 answer to "which transport
 * should we use?" is a [TransportResolution] that names the candidates and *selects nothing* — selection
 * belongs to the session, capability and protocol phases that own a policy and can act on it.
 */
data class TransportResolution(
    /** The candidate rows and the (here, always undecided) selection; the negotiation *is* the state. */
    val negotiation: TransportNegotiation,

    /** What kind of answer this is, kept separate from the negotiation so absence is never a verdict. */
    val outcome: ResolutionOutcome,

    /** How firmly the resolution is held; [VerificationLevel.INFERRED] is the floor, and the ceiling here. */
    val confidence: VerificationLevel,

    /** The category behind a non-positive outcome, or null when nothing was refused. */
    val reason: OmniBudsErrorCategory?,
) {
    init {
        // Phase 6 selects nothing: a resolution that names a chosen channel would be this phase opening a
        // transport it is forbidden to open, and PROTO-XPORT-007's no-fallthrough rule has nothing to
        // police if a resolution is allowed to pre-pick a winner.
        require(negotiation.selected == null) {
            "a Phase 6 resolution never selects a channel; selection is the caller's policy, and " +
                "auto-connect-to-a-candidate is exactly what prompt section 12 forbids"
        }
        when (outcome) {
            ResolutionOutcome.NO_CANDIDATE_AVAILABLE -> requireNotNull(reason) {
                "a resolution that offered candidates and refused them all must carry the category " +
                    "that says why (PROTO-XPORT-006)"
            }
            ResolutionOutcome.NO_EVIDENCE -> require(reason == null) {
                "no evidence is an absence, not a refusal; it carries no category, which is what keeps " +
                    "'nothing was asked' distinct from 'everything was asked and denied' (ADR-P0-016)"
            }
            else -> require(reason == null) {
                "a resolution that reached a verdict beyond absence must not also carry a refusal reason"
            }
        }
    }

    /** Every candidate kind the negotiation offers, in declaration order. */
    val candidates: List<TransportKind>
        get() = negotiation.candidates.map { boundary -> boundary.kind }

    /** True when the answer is "nothing is decided," which is the ordinary Phase 6 state, not a failure. */
    val isUndetermined: Boolean
        get() = negotiation.isUndetermined
}

/** The shape of a resolution answer, with absence kept distinct from an unsupported verdict. */
enum class ResolutionOutcome {
    /** Several candidates are usable and none was chosen — reported, not collapsed (ADR-P6-005). */
    CANDIDATES_AMBIGUOUS,

    /** Exactly one candidate is usable, offered to a caller that will decide whether to open it. */
    SINGLE_CANDIDATE,

    /** Candidates were offered and every one was refused. */
    NO_CANDIDATE_AVAILABLE,

    /** Nothing was offered to reason over — the pre-probe state, and not a claim about the device. */
    NO_EVIDENCE,
}

/**
 * Turns gathered evidence into candidate channels, selecting and opening nothing.
 *
 * A resolver reads facts that already exist — platform capability, Phase 3's profile observations, the
 * caller's candidate list — and never probes, scans, or connects; that is the difference between a
 * resolution and the transport it describes. Its input is transport-layer data only: `transport` is layer
 * 1 and cannot import a `device` (layer 2) type to feed a device into it (ADR-P1-003's strict-downward
 * rule, checked by `DependencyDirectionTest`), so the caller supplies the candidate rows it built from
 * whatever device evidence it holds rather than the raw identity types. This is prompt §12's "adapt to
 * actual models," applied against the layer map.
 */
interface TransportResolver {
    /**
     * Resolve a candidate list into an outcome, selecting nothing.
     *
     * [candidates] is the caller's evidence — one [TransportBoundary] per channel considered — because
     * producing them requires reads (adapter state, permission standing, profile facts) that are not a
     * resolver's to perform here. The result classifies those candidates and picks none.
     */
    fun resolve(candidates: List<TransportBoundary>): TransportResolution
}

/**
 * The only resolver Phase 6 ships: classify the candidates, select nothing.
 *
 * This is prompt §12's "safe unknown behaviour" made concrete. It never invents a channel, never orders
 * GATT before RFCOMM, and never auto-connects — it reports whether the candidates leave one, several, none
 * or an empty set, and leaves the decision to the caller. A later phase that owns a selection policy
 * supplies its own [TransportResolver]; this default is the floor that cannot fabricate support.
 */
object UndeterminedTransportResolver : TransportResolver {
    override fun resolve(candidates: List<TransportBoundary>): TransportResolution {
        val negotiation = TransportNegotiation(candidates = candidates, selected = null)
        val usable = candidates.filter { boundary -> boundary.availability.available }

        val outcome = when {
            candidates.isEmpty() -> ResolutionOutcome.NO_EVIDENCE
            usable.size == 1 -> ResolutionOutcome.SINGLE_CANDIDATE
            usable.size > 1 -> ResolutionOutcome.CANDIDATES_AMBIGUOUS
            else -> ResolutionOutcome.NO_CANDIDATE_AVAILABLE
        }

        val reason = when (outcome) {
            ResolutionOutcome.NO_CANDIDATE_AVAILABLE -> OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE
            else -> null
        }

        return TransportResolution(
            negotiation = negotiation,
            outcome = outcome,
            // Resolution reads already-tiered facts and adds none; the strongest honest claim about a
            // candidate set assembled without opening anything is INFERRED (PROTO-VERIFY-001).
            confidence = VerificationLevel.INFERRED,
            reason = reason,
        )
    }
}
