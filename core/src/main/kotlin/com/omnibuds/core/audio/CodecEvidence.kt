package com.omnibuds.core.audio

/**
 * Why OmniBuds believes a codec claim.
 *
 * Evidence is a first-class part of every codec record: the source, the
 * confidence, when it was observed, and an optional human-readable detail.
 * Records without evidence are records without provenance, and provenance is
 * what separates "LDAC is active" from "LDAC exists in an enum" (Phase 11
 * RULE 1, RULE 2).
 */
data class CodecEvidence(
    /** Where the claim came from. */
    val source: CodecEvidenceSource,
    /** How strongly the claim is evidenced. */
    val confidence: EvidenceConfidence,
    /** Epoch millis when the claim was observed, or null when never observed. */
    val observedAtMillis: Long? = null,
    /** Optional detail, e.g. "BluetoothA2dp.getSupportedCodecTypes()". Never a secret. */
    val detail: String? = null,
) {
    companion object {
        /** The evidence of a claim nobody has made yet. */
        fun unknown(): CodecEvidence =
            CodecEvidence(CodecEvidenceSource.UNKNOWN, EvidenceConfidence.UNKNOWN)
    }
}
