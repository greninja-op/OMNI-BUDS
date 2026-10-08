package com.omnibuds.core.audio

/**
 * A codec the Bluetooth audio ecosystem may expose.
 *
 * This is the ecosystem's vocabulary, not a claim about any device: registry
 * membership yields no state for a session (AUD-REG-006), and a codec missing from a
 * target platform stays representable because the model describes possible
 * capabilities, not the ones in reach today (Phase 1 prompt section 16).
 *
 * Two entries carry obligations that later code must respect:
 *
 * - The aptX variants are four separate entries. Supporting aptX evidences nothing
 *   about aptX HD, aptX Adaptive or aptX Lossless, so no code may collapse them into
 *   one "aptX supported" answer (AUD-REG-002, master section 19).
 * - [LC3] belongs to [CodecFamily.LE_AUDIO]. It is not an A2DP codec and must never
 *   be treated as one (AUD-XPORT-002, master section 20).
 *
 * [UNKNOWN] means "not determined". It exists so that absence of information is a
 * value rather than a chain of nulls, and so that unknown never degrades into
 * "unsupported" (AUD-TERM-001, ADR-P0-016, master section 53).
 *
 * [displayName] is the label shown to a user; [name] is the stable machine identity.
 * The two are kept apart deliberately, because a marketing label is not an identity
 * (specs section 1.3, AUD-REG-005).
 */
enum class Codec(
    /** Label for display, spellable by a human and never used as an identity. */
    val displayName: String,
    /** Transport family the codec belongs to; an audio record is incomplete without it. */
    val family: CodecFamily,
) {
    SBC("SBC", CodecFamily.CLASSIC_A2DP),
    AAC("AAC", CodecFamily.CLASSIC_A2DP),
    APTX("aptX", CodecFamily.CLASSIC_A2DP),
    APTX_HD("aptX HD", CodecFamily.CLASSIC_A2DP),
    APTX_ADAPTIVE("aptX Adaptive", CodecFamily.CLASSIC_A2DP),
    APTX_LOSSLESS("aptX Lossless", CodecFamily.CLASSIC_A2DP),
    LDAC("LDAC", CodecFamily.CLASSIC_A2DP),
    LC3("LC3", CodecFamily.LE_AUDIO),
    /**
     * Opus as reported by the platform (`SOURCE_CODEC_TYPE_OPUS`,
     * `CODEC_ID_OPUS`). Listed in the A2DP codec-config family by Android;
     * carried here so the mapping stays honest instead of dropping a real
     * platform report.
     */
    OPUS("Opus", CodecFamily.CLASSIC_A2DP),

    /** Not determined — an unread or unrecognised codec, never a positive claim. */
    UNKNOWN("Unknown", CodecFamily.UNKNOWN),
}
