package com.omnibuds.core.vendor

import com.omnibuds.core.state.VerificationLevel

/**
 * Evidence backing a vendor integration's claims.
 *
 * Phase 41: every integration must expose its evidence. A claim
 * without evidence stays a claim; the integration cannot be
 * promoted beyond what its evidence supports.
 */
data class VendorEvidence(
    /** The integration this evidence supports. */
    val integrationId: String,
    /** Human-readable source description, e.g. "bosectl (MIT)". */
    val source: String,
    /** What the source covers, e.g. "QC35 RFCOMM framing". */
    val scope: String,
    /** The highest evidence level this source establishes. */
    val level: VerificationLevel,
    /** ISO-8601 date the evidence was reviewed, or null. */
    val reviewedDate: String?,
) {
    init {
        require(integrationId.isNotBlank()) { "integrationId must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(scope.isNotBlank()) { "scope must not be blank" }
        // Evidence can never claim more than lab-tested without hardware.
        require(level != VerificationLevel.HARDWARE_VERIFIED &&
            level != VerificationLevel.PERSISTENCE_VERIFIED ||
            reviewedDate != null) {
            "hardware-level evidence requires a review date"
        }
    }
}

/**
 * The effective evidence level of an integration: the highest level
 * across its evidence records, or INFERRED when there is none.
 */
object VendorEvidenceAssessor {

    fun effectiveLevel(evidence: List<VendorEvidence>): VerificationLevel {
        if (evidence.isEmpty()) return VerificationLevel.INFERRED
        return evidence.maxOf { it.level }
    }

    /**
     * True when the integration may claim hardware-verified behavior.
     * Requires at least one hardware-verified record with a review date.
     */
    fun isHardwareVerified(evidence: List<VendorEvidence>): Boolean =
        evidence.any {
            it.level == VerificationLevel.HARDWARE_VERIFIED &&
                it.reviewedDate != null
        }
}
