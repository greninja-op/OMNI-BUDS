package com.omnibuds.core.validation

/**
 * The outcome of a single validation rule evaluation.
 *
 * Phase 14 (OB-P14-REQ-001, OB-P14-REQ-002): seven distinct statuses with
 * precise semantics. They are not interchangeable:
 *
 * - [VALID]: the checked invariant holds on the available evidence.
 * - [INVALID]: a hard contradiction was proven — the model is wrong.
 * - [INCONCLUSIVE]: the rule could not be evaluated (missing evidence,
 *   ambiguous sources). Not a failure, not a pass.
 * - [NOT_OBSERVABLE]: the platform cannot expose the required fact.
 *   Never a synonym for [UNSUPPORTED].
 * - [STALE]: the evidence exists but is no longer current.
 * - [CONFLICT]: sources disagree and precedence could not resolve them.
 * - [UNSUPPORTED]: the checked configuration is positively known to be
 *   unsupported (requires an actual negative reading, not a missing lookup).
 *
 * A [VALID] result never claims sound at the speaker or full-path signal
 * integrity — it claims only that the observations are internally
 * consistent (OB-P14-REQ-020).
 */
enum class ValidationStatus {
    VALID,
    INVALID,
    INCONCLUSIVE,
    NOT_OBSERVABLE,
    STALE,
    CONFLICT,
    UNSUPPORTED,
}
