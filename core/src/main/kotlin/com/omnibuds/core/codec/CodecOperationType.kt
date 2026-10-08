package com.omnibuds.core.codec

/**
 * The kinds of codec operation the control engine can express.
 *
 * Phase 12 (OB-P12-REQ-005, OB-P12-REQ-006): selection and configuration are
 * distinct operations with distinct preconditions. [REFRESH_STATE] is a
 * read-only operation; all others are side-effecting and require a legitimate
 * mechanism before they are attempted.
 *
 * Declaring an operation type is not permission to perform it — the capability
 * resolver gates every attempt, and unsupported operations fail explicitly
 * rather than pretending to work.
 */
enum class CodecOperationType {
    /** "Use this codec instead of the current one." Requires [selectable]. */
    SELECT_CODEC,

    /** "Use this codec with these parameters." Requires [configurable]. */
    CONFIGURE_CODEC,

    /** Mark a supported codec as enabled in the platform's preference order. */
    ENABLE_CODEC,

    /** Mark a codec as disabled in the platform's preference order. */
    DISABLE_CODEC,

    /** Clear any applied configuration back to platform defaults. */
    RESET_CONFIGURATION,

    /** Re-observe codec state. Read-only; always permitted. */
    REFRESH_STATE,
}
