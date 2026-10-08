package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec

/**
 * The configurable fields of a [CodecConfiguration].
 *
 * Phase 12 (OB-P12-REQ-018 rule 5, OB-P12-REQ-020): not every codec supports
 * every field. [CodecFieldSupport] declares the per-codec field set; the
 * engine rejects configurations that set unsupported fields before any
 * platform/vendor mechanism is touched.
 */
enum class CodecConfigField {
    QUALITY_MODE,
    BITRATE,
    SAMPLE_RATE,
    BIT_DEPTH,
    CHANNEL_MODE,
    ADAPTIVE_MODE,
}

/**
 * Which configuration fields each codec supports.
 *
 * This is the *theoretical* field set. DEVICE_OBSERVED constraints override
 * it where they differ (OB-P12-REQ-019): a resolver may narrow the set based
 * on what the device actually exposes, but never widen it without evidence.
 *
 * Honest defaults: only LDAC exposes a configurable field (quality mode)
 * through any known mechanism. Every other codec's set is empty — not because
 * the codecs lack parameters in theory, but because no legitimate mechanism
 * exposes them.
 */
object CodecFieldSupport {

    private val ldacFields: Set<CodecConfigField> = setOf(
        CodecConfigField.QUALITY_MODE,
    )

    fun forCodec(codec: Codec): Set<CodecConfigField> = when (codec) {
        Codec.LDAC -> ldacFields
        Codec.SBC,
        Codec.AAC,
        Codec.APTX,
        Codec.APTX_HD,
        Codec.APTX_ADAPTIVE,
        Codec.APTX_LOSSLESS,
        Codec.LC3,
        Codec.OPUS,
        Codec.UNKNOWN,
        -> emptySet()
    }
}
