package com.omnibuds.core.audio

/**
 * How strongly a codec claim is evidenced.
 *
 * Ordered weakest to strongest. Confidence must never increase during
 * normalization: a claim that enters as [INFERRED] leaves as [INFERRED] no
 * matter how many layers it passes through (Phase 11 §38).
 *
 * A database entry saying "Model X supports LDAC" is [INFERRED]; a runtime
 * codec report from the platform is [OBSERVED]; a claim confirmed by two
 * independent sources is [VERIFIED].
 */
enum class EvidenceConfidence {
    /** No confidence information; the default. */
    UNKNOWN,

    /**
     * Derived without direct observation: database lookup, device-name heuristic,
     * or platform-default assumption. Never presented as an observed fact.
     */
    INFERRED,

    /**
     * Read from a live source at runtime (platform API, profile proxy).
     * Stronger than inference, weaker than independent verification.
     */
    OBSERVED,

    /**
     * Confirmed by two independent sources, or by a read-back that matches the
     * claim. The strongest level Phase 11 assigns.
     */
    VERIFIED,
}
