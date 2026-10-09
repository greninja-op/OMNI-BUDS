package com.omnibuds.core.sdk.api

import com.omnibuds.core.state.VerificationLevel

/**
 * Contributor provenance and evidence metadata for a community integration.
 *
 * Enforces transparency of authorship, schema versions, and defensible evidence.
 * Integrations cannot self-promote evidence or verification levels.
 */
data class CommunityEvidenceRecord(
    val contributorId: String,
    val integrationId: String,
    val integrationVersion: String,
    val sdkVersion: SdkVersion,
    val protocolVersion: String,
    val supportedModels: Set<String>,
    val verifiedFirmware: Set<String>?,
    val evidenceSource: String,
    val declaredVerificationLevel: VerificationLevel,
    val knownLimitations: List<String>,
    val testFixtureReferences: List<String>,
    val lastReviewedDate: String,
) {
    init {
        require(contributorId.isNotBlank()) { "contributorId must not be blank" }
        require(integrationId.isNotBlank()) { "integrationId must not be blank" }
        require(integrationVersion.isNotBlank()) { "integrationVersion must not be blank" }
        require(protocolVersion.isNotBlank()) { "protocolVersion must not be blank" }
        require(evidenceSource.isNotBlank()) { "evidenceSource must not be blank" }
        require(lastReviewedDate.isNotBlank()) { "lastReviewedDate must not be blank" }
        // Community contributions must not declare HARDWARE_VERIFIED or PERSISTENCE_VERIFIED
        // without host verification. Maximum self-declared verification level is LAB_TESTED.
        require(declaredVerificationLevel <= VerificationLevel.LAB_TESTED) {
            "Community integrations cannot self-declare $declaredVerificationLevel; maximum is LAB_TESTED"
        }
    }
}
