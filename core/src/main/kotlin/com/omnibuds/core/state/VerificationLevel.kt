package com.omnibuds.core.state

/**
 * How strongly a protocol or capability claim is supported by evidence.
 *
 * These levels are never conflated, and a claim may not be reported above the tier
 * its evidence justifies (ADR-P0-014, master sections 27 and 54). Underscore spelling
 * is canonical.
 *
 * Declaration order is meaningful and increasing: [compareTo] expresses "at least as
 * well established as".
 */
enum class VerificationLevel {
    /** Derived from documentation or protocol inference. No execution. */
    INFERRED,

    /** Code exists that would perform it. Compiling is not evidence of working. */
    IMPLEMENTED,

    /** Exercised against a simulation, mock or recorded fixture. */
    LAB_TESTED,

    /** Exercised on real hardware, with the response read back and matched. */
    HARDWARE_VERIFIED,

    /** Survived a real disconnect and reconnect. */
    PERSISTENCE_VERIFIED,
}

fun VerificationLevel.atLeast(other: VerificationLevel): Boolean = this >= other
