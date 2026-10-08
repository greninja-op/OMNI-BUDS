package com.omnibuds.core.codec

import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.QualityMode

/**
 * A normalized codec configuration request or confirmed configuration.
 *
 * Phase 12 (OB-P12-REQ-017): every field is nullable or explicitly unknown.
 * Unknown is never defaulted to a "typical" value — a missing bitrate stays
 * [CodecBitrate.Unknown], never 0, never an identity-derived constant.
 *
 * Not every codec supports every field. Field support is validated per codec
 * before any operation is attempted (OB-P12-REQ-018):
 *
 * - LDAC: [qualityMode] available, [bitrate] often unknown.
 * - AAC: typically no configurable fields through public APIs.
 * - SBC: baseline; configuration rarely exposed.
 *
 * A configuration whose [codec] is [Codec.UNKNOWN] is never valid for a
 * control operation — you cannot configure what you cannot identify.
 */
data class CodecConfiguration(
    val codec: Codec,
    val qualityMode: QualityMode = QualityMode.UNKNOWN,
    val bitrate: CodecBitrate = CodecBitrate.Unknown,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelMode: ChannelMode = ChannelMode.UNKNOWN,
    val adaptiveMode: Boolean? = null,
) {
    init {
        require(sampleRateHz == null || sampleRateHz > 0) {
            "sampleRateHz must be unreported (null) or positive, was $sampleRateHz"
        }
        require(bitDepth == null || bitDepth > 0) {
            "bitDepth must be unreported (null) or positive, was $bitDepth"
        }
    }

    companion object {
        /** An empty configuration: codec identified, nothing requested. */
        fun empty(codec: Codec): CodecConfiguration = CodecConfiguration(codec = codec)
    }
}
