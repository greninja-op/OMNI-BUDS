package com.omnibuds.core.codec

/**
 * A machine-readable note about codec observation.
 *
 * Diagnostics explain uncertainty: why a codec is UNKNOWN, why a state went
 * stale, why an observation failed. They carry codes and timestamps, never
 * Bluetooth addresses, audio content, or secrets.
 */
data class CodecDiagnostic(
    /** Stable machine-readable code, e.g. "ACTIVE_CODEC_NOT_OBSERVABLE". */
    val code: String,
    /** Human-readable explanation of what happened and what it means. */
    val message: String,
    /** Epoch millis when the diagnostic was recorded. */
    val timestampMillis: Long,
)
