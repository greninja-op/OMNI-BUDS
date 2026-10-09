package com.omnibuds.core.validation

/**
 * Deterministic aggregation of rule results into an overall status.
 *
 * Phase 14 (OB-P14-REQ-015): the policy, in precedence order:
 *
 * 1. Any INVALID with severity ERROR/CRITICAL → overall INVALID.
 *    A hard, proven invariant violation can never yield VALID.
 * 2. Any CONFLICT → overall CONFLICT (conflicts stay visible).
 * 3. Any STALE among required evidence → overall STALE, unless a fresher
 *    result already determined INVALID/CONFLICT.
 * 4. Any INVALID with severity WARNING → overall INCONCLUSIVE
 *    (a warning-level contradiction is uncertainty, not proof).
 * 5. All VALID → overall VALID.
 * 6. Mix of VALID + INCONCLUSIVE/NOT_OBSERVABLE → overall INCONCLUSIVE.
 *    Uncertainty is retained, never rounded up.
 * 7. Missing optional information never produces INVALID (enforced by rules
 *    returning INCONCLUSIVE/INFO, never INVALID, for optional gaps).
 *
 * Pure function. Same input → same output, always.
 */
object ValidationAggregator {

    fun aggregate(results: List<ValidationResult>): ValidationStatus {
        require(results.isNotEmpty()) { "cannot aggregate an empty result set" }
        val byStatus = results.groupBy { it.status }

        // 1. Hard violations.
        if (byStatus[ValidationStatus.INVALID].orEmpty().any {
                it.severity == ValidationSeverity.ERROR ||
                    it.severity == ValidationSeverity.CRITICAL
            }
        ) {
            return ValidationStatus.INVALID
        }
        // 2. Conflicts stay visible.
        if (!byStatus[ValidationStatus.CONFLICT].isNullOrEmpty()) {
            return ValidationStatus.CONFLICT
        }
        // 3. Stale required evidence.
        if (!byStatus[ValidationStatus.STALE].isNullOrEmpty()) {
            return ValidationStatus.STALE
        }
        // 4. Warning-level contradictions are uncertainty.
        if (!byStatus[ValidationStatus.INVALID].isNullOrEmpty()) {
            return ValidationStatus.INCONCLUSIVE
        }
        // 5. Clean pass.
        if (results.all { it.status == ValidationStatus.VALID }) {
            return ValidationStatus.VALID
        }
        // 6. Valid + unknown → uncertainty retained.
        return ValidationStatus.INCONCLUSIVE
    }
}
