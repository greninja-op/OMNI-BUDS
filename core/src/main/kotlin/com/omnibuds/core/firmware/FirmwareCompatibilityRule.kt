package com.omnibuds.core.firmware

import com.omnibuds.core.state.VerificationLevel

/**
 * Outcome of evaluating a specific firmware compatibility rule.
 */
enum class FirmwareRuleOutcome {
    /** Firmware is explicitly verified and compatible. */
    COMPATIBLE,

    /** Compatible but restricted to safe/read-only subset. */
    COMPATIBLE_WITH_LIMITATIONS,

    /** Known incompatible, unstable, or unsupported firmware. */
    INCOMPATIBLE,

    /** Compatibility is not yet established or actively withdrawn. */
    UNKNOWN_OR_WITHDRAWN,
}

/**
 * An evidence-backed rule governing firmware compatibility for a specific model,
 * protocol, or capability scope.
 */
data class FirmwareCompatibilityRule(
    /** Unique rule identifier. */
    val ruleId: String,

    /** Manufacturer ID or namespace (e.g. "sony", "bose", "apple"). */
    val manufacturer: String,

    /** Exact model IDs or hardware revisions this rule applies to, or empty if family-wide. */
    val applicableModels: Set<String> = emptySet(),

    /** Firmware constraint governing applicability. */
    val firmwareConstraint: FirmwareConstraint,

    /** Protocol ID or capability scope this rule governs. Null implies all protocols for model. */
    val targetScope: String? = null,

    /** Outcome if rule matches. */
    val outcome: FirmwareRuleOutcome,

    /** Citation or evidence backing this rule (e.g. "REV-2023-SONY-04", "CVE-NONE-TESTED"). */
    val evidenceReference: String,

    /** Evidence verification level backing this claim. */
    val verificationLevel: VerificationLevel = VerificationLevel.LAB_TESTED,

    /** Documented limitations when outcome is COMPATIBLE_WITH_LIMITATIONS. */
    val limitations: List<String> = emptyList(),

    /** Machine reason or explanation. */
    val rationale: String = "",
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
        require(manufacturer.isNotBlank()) { "manufacturer must not be blank" }
        require(evidenceReference.isNotBlank()) { "evidenceReference must not be blank" }
    }

    /**
     * Checks if this rule applies to the given model, firmware version, and target scope.
     */
    fun matches(
        modelId: String?,
        firmware: FirmwareVersion,
        scope: String? = null,
    ): Boolean {
        if (applicableModels.isNotEmpty() && modelId != null && modelId !in applicableModels) {
            return false
        }
        if (targetScope != null && scope != null && targetScope != scope) {
            return false
        }
        return firmwareConstraint.isSatisfiedBy(firmware)
    }
}
