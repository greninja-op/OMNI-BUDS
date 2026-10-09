package com.omnibuds.core.verification

/**
 * Progress stages of a verification operation.
 *
 * Phase 18 (OB-P18-REQ-002): stages track progress; they are NOT the outcome.
 * Plans skip inapplicable stages — a write-only protocol never enters
 * read-back stages.
 */
enum class VerificationStage {
    /** Created, not yet evaluated. */
    CREATED,

    /** Checking eligibility (identity, capability, protocol). */
    ELIGIBILITY_CHECK,

    /** Baseline device state being captured. */
    BASELINE_CAPTURE,

    /** Apply requested through the control interface. */
    APPLY_REQUESTED,

    /** Acknowledgement received (meaning per protocol). */
    APPLY_ACKNOWLEDGED,

    /** Immediate read-back in progress. */
    INITIAL_READ_BACK,

    /** Observing a control-session boundary. */
    SESSION_BOUNDARY_CHECK,

    /** Observing a disconnect/reconnect cycle. */
    RECONNECT_CHECK,

    /** Observing an application restart. */
    APPLICATION_RESTART_CHECK,

    /** Observing a device power cycle. */
    DEVICE_POWER_CYCLE_CHECK,

    /** Evaluating collected evidence. */
    EVALUATING_EVIDENCE,

    /** Terminal: evaluation complete. */
    COMPLETED,
}

/**
 * Terminal outcomes of verification.
 *
 * Phase 18: separate from stages. Once terminal, no transition back to
 * active states (OB-P18-REQ-028).
 */
enum class VerificationOutcome {
    /** Requested scope proven by evidence. */
    VERIFIED,

    /** A weaker scope than requested was proven. */
    PARTIALLY_VERIFIED,

    /** Evidence proves the setting did not persist. */
    NOT_VERIFIED,

    /** Evidence insufficient to decide. */
    INCONCLUSIVE,

    /** The requested scope cannot be observed with available capabilities. */
    UNSUPPORTED,

    /** Cancelled by caller or lifecycle. */
    CANCELLED,

    /** Failed due to error (not evidence of non-persistence). */
    FAILED,
}

/** True when this outcome is terminal. */
fun VerificationOutcome.isTerminal(): Boolean = true // All outcomes are terminal by definition.
