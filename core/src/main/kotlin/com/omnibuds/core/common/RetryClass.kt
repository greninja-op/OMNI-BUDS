package com.omnibuds.core.common

/**
 * How an operation may be repeated after it fails.
 *
 * Derived from the error category rather than chosen at the call site, so that a
 * side-effecting command can never be retried by accident
 * (docs/phases/phase-0/specs.md section 4).
 */
enum class RetryClass {
    /** Idempotent reads and discovery may be retried under a bounded policy. */
    SAFE_TO_RETRY,

    /** A side-effecting operation whose effect is unknown: re-read state first. */
    RETRY_AFTER_REREAD,

    /** Never repeated automatically. Re-sending could compound an unknown effect. */
    NEVER_RETRY,
}
