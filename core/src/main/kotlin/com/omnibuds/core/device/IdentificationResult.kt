package com.omnibuds.core.device

/**
 * What Phase 5 concluded about a device, with the evidence and the versions that produced it.
 *
 * Seven outcomes, and prompt §8's two prohibitions are the reason each is separate: an ambiguous
 * result carries candidates rather than a winner, and an insufficient-evidence result is not an
 * unknown device wearing a different name — one says the registry has no rule that fits, the other
 * says the evidence this phase can collect cannot decide between rules that do.
 *
 * Nothing here is a capability claim. [maySupportProtocolResolution] is the only question a later
 * phase may ask of a result, and it is answered `false` for five of the seven outcomes; protocol
 * selection is Phase 7's decision about a transport it can probe, and prompt §17 forbids inferring
 * it from identification. The types also refuse to hold a manufacturer or model where the outcome
 * does not license one, which is ADR-P5-008's point: a reader cannot ask an `Ambiguous` for "the"
 * model, because there is no field to ask.
 *
 * [ruleSetVersion] and [registryVersion] travel with every outcome because a conclusion outlives its
 * registry: Phase 20's research compares identifications across versions, and an unversioned result
 * is a claim that cannot be re-derived.
 */
sealed interface IdentificationResult {
    /** How strongly the surviving evidence holds, per [IdentificationConfidence]'s written requirements. */
    val confidence: IdentificationConfidence

    /** The rule ids that fired, empty when nothing matched. */
    val matchedRuleIds: List<String>

    /** The signals the decision read, kept as references rather than restated values. */
    val evidence: List<IdentitySignal>

    val registryVersion: Int
    val ruleSetVersion: Int

    /** What this result may not be read as. Prompt §8's "known limitations" field. */
    val limitations: String

    /** True only where a result identifies anything at all — five outcomes answer false (ADR-P5-008). */
    val isIdentified: Boolean
        get() = this is Exact || this is Likely || this is ManufacturerOnly

    /**
     * Whether this result may be read as evidence toward protocol resolution. Never true for
     * ambiguity, unknown, insufficient or invalid evidence; and even when true it is evidence
     * *toward* a decision, not the decision.
     */
    val maySupportProtocolResolution: Boolean
        get() = isIdentified && confidence.maySupportProtocolResolution

    /** A rule set matched this device's evidence exactly, at the confidence the rule claimed. */
    data class Exact(
        val manufacturer: ManufacturerIdentity,
        val model: ModelIdentity,
        override val confidence: IdentificationConfidence,
        override val matchedRuleIds: List<String>,
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String,
    ) : IdentificationResult

    /** A strong match on more than one independent signal, with no contradiction. */
    data class Likely(
        val manufacturer: ManufacturerIdentity,
        val model: ModelIdentity?,
        override val confidence: IdentificationConfidence,
        override val matchedRuleIds: List<String>,
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String,
    ) : IdentificationResult

    /**
     * The manufacturer is established and the model is not — prompt §9's third example, and the most
     * outcome a name-only phase can honestly reach.
     */
    data class ManufacturerOnly(
        val manufacturer: ManufacturerIdentity,
        override val confidence: IdentificationConfidence,
        override val matchedRuleIds: List<String>,
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String,
    ) : IdentificationResult

    /**
     * More than one candidate survived, and none was chosen.
     *
     * There is no `manufacturer` field here on purpose: the type cannot answer "which one", so a
     * caller cannot take an arbitrary winner from a result that declined to pick one.
     */
    data class Ambiguous(
        val candidates: List<Candidate>,
        override val confidence: IdentificationConfidence = IdentificationConfidence.UNKNOWN,
        override val matchedRuleIds: List<String> = candidates.flatMap { candidate -> candidate.ruleIds },
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String = "several rules fit the same evidence; identification is undecided",
    ) : IdentificationResult {
        /** One surviving candidate, with the rules that kept it alive. */
        data class Candidate(
            val manufacturer: ManufacturerIdentity,
            val model: ModelIdentity?,
            val ruleIds: List<String>,
        )
    }

    /** Nothing in the registry fits, which prompt §13 insists is not a failure. */
    data class Unknown(
        override val confidence: IdentificationConfidence = IdentificationConfidence.UNKNOWN,
        override val matchedRuleIds: List<String> = emptyList(),
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String = NO_RULE_MESSAGE,
    ) : IdentificationResult

    /** Rules exist for this shape of device, and the signals collected cannot decide among them. */
    data class InsufficientEvidence(
        val missingKinds: List<IdentitySignalKind>,
        override val confidence: IdentificationConfidence = IdentificationConfidence.UNKNOWN,
        override val matchedRuleIds: List<String> = emptyList(),
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String = "the evidence this phase can collect cannot decide this device",
    ) : IdentificationResult

    /** Vendor text arrived malformed and was rejected. Distinct from absence, and never a negative. */
    data class InvalidEvidence(
        val rejectedKinds: List<IdentitySignalKind>,
        override val confidence: IdentificationConfidence = IdentificationConfidence.UNKNOWN,
        override val matchedRuleIds: List<String> = emptyList(),
        override val evidence: List<IdentitySignal>,
        override val registryVersion: Int,
        override val ruleSetVersion: Int,
        override val limitations: String = "malformed identity metadata was rejected before matching",
    ) : IdentificationResult

    companion object {
        /** The sentence reported when a registry with no rules was asked to identify a device. */
        const val NO_RULE_MESSAGE = "no identification rule exists for this evidence"
    }
}
