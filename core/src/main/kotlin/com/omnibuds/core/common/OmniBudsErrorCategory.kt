package com.omnibuds.core.common

/**
 * The canonical failure categories for OmniBuds operations.
 *
 * This is the superset required by ADR-P0-012 plus the three categories the Phase 1
 * prompt adds. A category names what is known to be wrong; it never carries a
 * fabricated value, and an unread measurement stays unknown instead of becoming a
 * zero (ADR-P0-016).
 */
enum class OmniBudsErrorCategory(
    /** Whether and how the failed operation may be attempted again. */
    val retryClass: RetryClass,
    /**
     * Whether the device's true state is now uncertain, meaning capability and
     * state records must be re-discovered before another write is permitted.
     */
    val invalidatesSession: Boolean,
) {
    BLUETOOTH_DISABLED(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    PERMISSION_DENIED(RetryClass.NEVER_RETRY, invalidatesSession = false),
    DEVICE_DISCONNECTED(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    TRANSPORT_UNAVAILABLE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    GATT_FAILURE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    RFCOMM_FAILURE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    PROTOCOL_MISMATCH(RetryClass.NEVER_RETRY, invalidatesSession = true),
    UNSUPPORTED_FEATURE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    READ_FAILED(RetryClass.SAFE_TO_RETRY, invalidatesSession = false),
    WRITE_REJECTED(RetryClass.NEVER_RETRY, invalidatesSession = true),
    VERIFICATION_FAILED(RetryClass.NEVER_RETRY, invalidatesSession = true),
    TIMEOUT(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    FIRMWARE_MISMATCH(RetryClass.NEVER_RETRY, invalidatesSession = false),
    CODEC_UNAVAILABLE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    UNKNOWN_DEVICE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    INVALID_STATE(RetryClass.NEVER_RETRY, invalidatesSession = false),
}
