package com.omnibuds.core.validation

/**
 * A single statically-defined validation rule.
 *
 * Phase 14 (OB-P14-REQ-013): rules are plain Kotlin — no scripting engine,
 * no executable configuration. Each rule declares everything the docs and
 * tests need. Rules are pure: [evaluate] takes the input bundle and returns
 * a result, with no I/O and no side effects.
 */
interface ValidationRule {

    /** Stable identifier, e.g. "P14-TRANSPORT-001". */
    val ruleId: String

    /** Human-readable description of the invariant. */
    val description: String

    val category: ValidationCategory

    /** Transports/profiles this rule applies to; empty = all. */
    val applicableTransports: Set<String> get() = emptySet()

    /** Documentation reference, e.g. "docs/phases/phase-14/design.md#transport". */
    val docReference: String get() = "docs/phases/phase-14/design.md"

    /**
     * Evaluate the rule against one coherent observation bundle.
     * Must not throw for missing evidence — return INCONCLUSIVE or
     * NOT_OBSERVABLE with a reason instead.
     */
    fun evaluate(input: ValidationInput): ValidationResult
}
