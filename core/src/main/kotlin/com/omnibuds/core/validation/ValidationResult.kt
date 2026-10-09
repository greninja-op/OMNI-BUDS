package com.omnibuds.core.validation

import com.omnibuds.core.device.DeviceIdentity

/**
 * The structured result of evaluating one validation rule.
 *
 * Phase 14 (OB-P14-REQ-001): every meaningful result identifies the rule,
 * the target, the status, the severity, the evidence behind it, and why.
 * Immutable value — safe to retain in snapshots and histories.
 *
 * @param validationId stable identifier, e.g. "P14-TRANSPORT-001".
 * @param sessionGeneration the session generation the evidence belongs to;
 * null when the rule is generation-independent.
 */
data class ValidationResult(
    val validationId: String,
    val device: DeviceIdentity,
    val sessionGeneration: Long?,
    val category: ValidationCategory,
    val status: ValidationStatus,
    val severity: ValidationSeverity,
    val evidenceReferences: List<String> = emptyList(),
    val observedAtMillis: Long? = null,
    val reason: String,
    val limitations: List<String> = emptyList(),
    val suggestedAction: String? = null,
) {
    init {
        require(validationId.isNotBlank()) { "validationId must identify the rule" }
        require(reason.isNotBlank()) { "reason must explain the result" }
        // Severity/status coherence: a proven contradiction is never INFO.
        require(!(status == ValidationStatus.INVALID && severity == ValidationSeverity.INFO)) {
            "INVALID results must not be INFO severity"
        }
        require(!(status == ValidationStatus.CONFLICT && severity == ValidationSeverity.INFO)) {
            "CONFLICT results must not be INFO severity"
        }
    }
}
