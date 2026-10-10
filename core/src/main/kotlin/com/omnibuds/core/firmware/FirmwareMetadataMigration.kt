package com.omnibuds.core.firmware

import com.omnibuds.core.state.VerificationLevel

/**
 * Versioned schema for persistent firmware metadata and compatibility records.
 */
data class FirmwareMetadataSchema(
    val schemaVersion: Int,
    val rules: List<FirmwareCompatibilityRuleRecord>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1)
    }
}

/**
 * DTO for persisting firmware compatibility rules.
 */
data class FirmwareCompatibilityRuleRecord(
    val ruleId: String,
    val manufacturer: String,
    val applicableModels: List<String>,
    val constraintType: String,
    val constraintValue: String,
    val targetScope: String?,
    val outcome: String,
    val evidenceReference: String,
    val verificationLevel: String,
    val limitations: List<String>,
    val rationale: String,
)

/**
 * Migration engine for firmware compatibility records.
 */
object FirmwareMetadataMigration {

    /**
     * Converts a raw DTO record into an active [FirmwareCompatibilityRule], validating
     * constraints and rejecting corrupted/inconsistent records safely.
     */
    fun parseRecord(record: FirmwareCompatibilityRuleRecord): FirmwareCompatibilityRule? {
        if (record.ruleId.isBlank() || record.manufacturer.isBlank() || record.evidenceReference.isBlank()) {
            return null
        }

        val outcome = try {
            FirmwareRuleOutcome.valueOf(record.outcome)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val verification = try {
            VerificationLevel.valueOf(record.verificationLevel)
        } catch (e: IllegalArgumentException) {
            VerificationLevel.INFERRED
        }

        val constraint = parseConstraint(record.constraintType, record.constraintValue) ?: return null

        return FirmwareCompatibilityRule(
            ruleId = record.ruleId,
            manufacturer = record.manufacturer,
            applicableModels = record.applicableModels.toSet(),
            firmwareConstraint = constraint,
            targetScope = record.targetScope,
            outcome = outcome,
            evidenceReference = record.evidenceReference,
            verificationLevel = verification,
            limitations = record.limitations,
            rationale = record.rationale,
        )
    }

    private fun parseConstraint(type: String, value: String): FirmwareConstraint? {
        return when (type) {
            "ANY" -> FirmwareConstraint.Any
            "EXACT" -> {
                val v = FirmwareVersion.parse(value)
                if (v is FirmwareVersion.Unknown) null else FirmwareConstraint.Exact(v)
            }
            "ALLOWLIST" -> {
                val versions = value.split(",").map { FirmwareVersion.parse(it.trim()) }.filter { it !is FirmwareVersion.Unknown }.toSet()
                if (versions.isEmpty()) null else FirmwareConstraint.Allowlist(versions)
            }
            "DENYLIST" -> {
                val versions = value.split(",").map { FirmwareVersion.parse(it.trim()) }.filter { it !is FirmwareVersion.Unknown }.toSet()
                if (versions.isEmpty()) null else FirmwareConstraint.Denylist(versions)
            }
            "AT_LEAST" -> {
                val v = FirmwareVersion.parse(value)
                if (v is FirmwareVersion.Unknown) null else FirmwareConstraint.AtLeast(v)
            }
            else -> null
        }
    }
}
