package com.omnibuds.core.sdk.validation

/**
 * Validation diagnostic severity.
 */
enum class ValidationSeverity {
    ERROR,
    WARNING,
}

/**
 * An individual diagnostic finding from static package validation.
 */
data class ValidationFinding(
    val severity: ValidationSeverity,
    val ruleId: String,
    val message: String,
    val location: String = "",
)

/**
 * Machine-readable report of community integration package validation.
 */
data class PackageValidationReport(
    val isValid: Boolean,
    val findings: List<ValidationFinding>,
    val scannedIntegrationId: String,
    val summary: String,
) {
    val errorCount: Int get() = findings.count { it.severity == ValidationSeverity.ERROR }
    val warningCount: Int get() = findings.count { it.severity == ValidationSeverity.WARNING }
}
