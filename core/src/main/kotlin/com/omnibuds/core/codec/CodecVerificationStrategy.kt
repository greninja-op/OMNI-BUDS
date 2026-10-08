package com.omnibuds.core.codec

/**
 * How the engine verifies that a codec operation actually took effect.
 *
 * Phase 12 (OB-P12-REQ-027): verification is explicit. If no verification
 * mechanism exists, the operation uses [NONE] and the result can never be
 * VERIFIED — it is capped at APPLIED_UNVERIFIED.
 */
enum class CodecVerificationStrategy {
    /** Re-observe through the platform's public codec observation APIs. */
    PLATFORM_OBSERVATION,

    /** Read back through a verified device/vendor protocol. */
    DEVICE_PROTOCOL_READBACK,

    /** Observe through the audio-device observation path. */
    AUDIO_DEVICE_OBSERVATION,

    /** Two or more of the above combined. */
    COMBINED,

    /** No verification mechanism exists. Results are never VERIFIED. */
    NONE,
}
