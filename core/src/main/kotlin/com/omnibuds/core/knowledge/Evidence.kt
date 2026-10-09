package com.omnibuds.core.knowledge

import com.omnibuds.core.state.VerificationLevel

/**
 * Evidence types.
 */
enum class EvidenceType {
    /** Official vendor documentation. */
    VENDOR_DOCUMENTATION,

    /** Community or third-party protocol research. */
    PROTOCOL_RESEARCH,

    /** Sanitized capture from the Protocol Laboratory. */
    SANITIZED_CAPTURE,

    /** Synthetic fixture — never real-device evidence. */
    SYNTHETIC_FIXTURE,

    /** Direct observation on real hardware. */
    HARDWARE_OBSERVATION,

    /** Inference from other evidence — lowest reliability. */
    INFERENCE,
}

/**
 * Where evidence came from.
 */
enum class SourceType {
    VENDOR_DOCUMENTATION,
    PROTOCOL_RESEARCH,
    SANITIZED_CAPTURE,
    SYNTHETIC_FIXTURE,
    HARDWARE_OBSERVATION,
    COMMUNITY_REPORT,
    INTERNAL_ANALYSIS,
}

/**
 * A provenance source.
 *
 * Phase 22 (OB-P22-REQ-015): source type, reference, collection details,
 * license constraints, transformation history. Synthetic is never labeled
 * as a capture.
 */
data class Source(
    val id: SourceId,
    val type: SourceType,
    /** Reference: URL, document title, or internal identifier. */
    val reference: String,
    val collectedAtMillis: Long? = null,
    /** License or redistribution constraints. */
    val licenseConstraints: String? = null,
    /** History of transformations applied to the source material. */
    val transformationHistory: List<String> = emptyList(),
    val metadata: RecordMetadata,
)

/**
 * A supporting evidence record.
 *
 * Phase 22 (OB-P22-REQ-004/006): raw captures are not stored here —
 * reference the Protocol Laboratory artifact store instead.
 */
data class EvidenceRecord(
    val id: EvidenceId,
    /** The claim this evidence supports or contradicts. */
    val claimId: ClaimId,
    val type: EvidenceType,
    val sourceId: SourceId,
    val sourceDateMillis: Long? = null,
    val collectedAtMillis: Long? = null,
    val applicableModelId: DeviceModelId? = null,
    val applicableFirmwareId: FirmwareProfileId? = null,
    val protocolId: ProtocolId? = null,
    /** How to reproduce, when applicable. */
    val reproducibility: String? = null,
    /** Whether sensitive material was sanitized. */
    val sanitized: Boolean = false,
    /** Assessor's reliability judgment. */
    val reliability: String,
    val limitations: List<String> = emptyList(),
    /** Whether this evidence supports or contradicts the claim. */
    val stance: EvidenceStance,
    val verification: VerificationLevel,
    /** Integrity metadata, e.g. checksum of the referenced artifact. */
    val integrity: String? = null,
    val metadata: RecordMetadata,
) {
    init {
        // Synthetic fixtures can never establish hardware verification.
        if (type == EvidenceType.SYNTHETIC_FIXTURE) {
            require(
                verification != VerificationLevel.HARDWARE_VERIFIED &&
                    verification != VerificationLevel.PERSISTENCE_VERIFIED,
            ) { "synthetic fixtures cannot establish hardware verification" }
        }
    }
}

/** Whether evidence supports or contradicts a claim. */
enum class EvidenceStance {
    SUPPORTS,
    CONTRADICTS,
}

/**
 * A protocol claim, independent from its evidence.
 *
 * Phase 22 (OB-P22-REQ-005/029): observed facts, inferred interpretations,
 * and verified conclusions are distinct. Contradictory evidence is preserved.
 */
data class Claim(
    val id: ClaimId,
    /** What the claim is about, e.g. a model or protocol ID. */
    val subject: String,
    /** The predicate, e.g. "supports", "implements". */
    val predicate: String,
    /** The object or typed value. */
    val objectValue: String,
    /** Scope, e.g. firmware range or "all firmware". */
    val scope: String,
    val supportingEvidenceIds: List<EvidenceId> = emptyList(),
    val contradictingEvidenceIds: List<EvidenceId> = emptyList(),
    val confidence: ClaimConfidence,
    val status: ClaimStatus,
    /** Reviewer or review-process metadata. */
    val review: String? = null,
    val metadata: RecordMetadata,
)

/** Confidence in a claim. */
enum class ClaimConfidence {
    SPECULATIVE,
    LOW,
    MEDIUM,
    HIGH,
    VERIFIED,
}

/** Claim status. */
enum class ClaimStatus {
    /** A hypothesis under investigation. */
    HYPOTHESIS,

    /** Under active review. */
    UNDER_REVIEW,

    /** Accepted based on evidence. */
    ACCEPTED,

    /** Rejected based on contradicting evidence. */
    REJECTED,

    /** Withdrawn by the claimant. */
    WITHDRAWN,
}
