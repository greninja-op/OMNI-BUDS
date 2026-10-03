package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel

/**
 * How strongly an identification is held, with the evidence each rung requires written beside it.
 *
 * Prompt §9 forbids confidence without a defined evidence requirement, and the ladder here is
 * deliberately categorical rather than numeric: a number invites averaging, and averaging two
 * signals is exactly how a weak one borrows the other's strength. ADR-P5-004's structural claim
 * lives in this file - [VERIFIED] has no construction path, because reaching it requires
 * [VerificationLevel.HARDWARE_VERIFIED] evidence about one named device, and the device session is
 * deferred by ADR-P3-014.
 *
 * [minimumEvidence] is what a rule must cite to land here at all, and
 * [independentKindsRequired] is ADR-P5-004's independence rule as arithmetic: one name is one kind
 * of evidence however many signals carry it, so `reported name` plus `user alias` plus `normalized
 * name` is still one kind and cannot reach [HIGH].
 */
enum class IdentificationConfidence(
    /** How many distinct [IdentitySignalKind] families must corroborate to land at this rung. */
    val independentKindsRequired: Int,
    /** The weakest [VerificationLevel] an identification at this rung may claim. */
    val minimumEvidence: VerificationLevel,
    /** Whether this rung is reachable from Phase 5's code. False only for [VERIFIED]. */
    val reachableInPhaseFive: Boolean,
) {
    /**
     * Established by evidence this phase cannot produce: the device confirmed a model against a
     * protocol record on real hardware. Declared for the later phases that will reach it.
     */
    VERIFIED(
        independentKindsRequired = 3,
        minimumEvidence = VerificationLevel.HARDWARE_VERIFIED,
        reachableInPhaseFive = false,
    ),

    /** Two independent observed kinds agree, and no usable signal contradicts them. */
    HIGH(
        independentKindsRequired = 2,
        minimumEvidence = VerificationLevel.IMPLEMENTED,
        reachableInPhaseFive = true,
    ),

    /** One observed kind, corroborated by nothing and contradicted by nothing. */
    MODERATE(
        independentKindsRequired = 1,
        minimumEvidence = VerificationLevel.IMPLEMENTED,
        reachableInPhaseFive = true,
    ),

    /** A lean, not a finding: an inferred or user-chosen value, or a single weak kind. */
    LOW(
        independentKindsRequired = 0,
        minimumEvidence = VerificationLevel.INFERRED,
        reachableInPhaseFive = true,
    ),

    /** Nothing usable. Not a low score - the absence of an answer, kept distinct (ADR-P0-016). */
    UNKNOWN(
        independentKindsRequired = 0,
        minimumEvidence = VerificationLevel.INFERRED,
        reachableInPhaseFive = true,
    ),
    ;

    /** True where a result at this rung may be read as evidence toward a protocol decision (Phase 7). */
    val maySupportProtocolResolution: Boolean
        get() = this == VERIFIED || this == HIGH || this == MODERATE
}
