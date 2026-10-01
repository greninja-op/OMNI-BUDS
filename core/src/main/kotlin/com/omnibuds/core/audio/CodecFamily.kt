package com.omnibuds.core.audio

/**
 * The transport family a codec is carried over.
 *
 * LE Audio is a separate transport family, not another A2DP codec: classic Bluetooth
 * runs A2DP and carries SBC, AAC, aptX and LDAC, while LE Audio carries LC3, and the
 * A2DP codec-capability semantics do not transfer to LC3 unmodified
 * (AUD-XPORT-002, AUD-XPORT-003, master section 20).
 *
 * Family is a first-class dimension of every audio record — a codec name alone never
 * identifies the state of a session (AUD-XPORT-001), and state must not bleed across
 * families (AUD-XPORT-004).
 */
enum class CodecFamily {
    /** Classic Bluetooth A2DP media transport. */
    CLASSIC_A2DP,

    /** LE Audio, isochronous transport. Not an A2DP variant (master section 20). */
    LE_AUDIO,

    /** The family has not been read, or cannot be established from here. */
    UNKNOWN,
}
