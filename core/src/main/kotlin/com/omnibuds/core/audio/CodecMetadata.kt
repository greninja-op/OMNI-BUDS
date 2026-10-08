package com.omnibuds.core.audio

/**
 * A codec's bitrate as the platform reports it.
 *
 * Bitrate is never invented from codec identity: LDAC does not automatically
 * mean 990 kbps unless the runtime actually exposes that configuration
 * (Phase 11 §11, RULE 4).
 */
sealed interface CodecBitrate {
    /** An exact bitrate in bits per second, as reported. */
    data class Exact(val bitsPerSecond: Long) : CodecBitrate

    /** A bounded range in bits per second, as reported. */
    data class Range(val minBitsPerSecond: Long, val maxBitsPerSecond: Long) : CodecBitrate

    /**
     * The codec varies its bitrate continuously (e.g. aptX Adaptive, LDAC
     * adaptive mode). No single number is honest.
     */
    data object Adaptive : CodecBitrate

    /** The platform did not report a bitrate. */
    data object Unknown : CodecBitrate
}

/**
 * Codec parameters as observed, not as specified.
 *
 * Every field is nullable-or-unknown: a partial read is a valid result, and a
 * missing sample rate never becomes 0, a missing bit depth never becomes 16,
 * and a missing channel mode never becomes stereo (Phase 11 §8–§12).
 *
 * Advertised capability and currently observed configuration are different
 * facts; a [CodecMetadata] records one of them, and callers must know which.
 */
data class CodecMetadata(
    /** Sample rate in Hz as reported, or null when not reported. */
    val sampleRateHz: Int? = null,
    /** Bits per sample as reported, or null when not reported. */
    val bitsPerSample: Int? = null,
    /** Channel mode as reported; [ChannelMode.UNKNOWN] when not reported. */
    val channelMode: ChannelMode = ChannelMode.UNKNOWN,
    /** Bitrate as reported; [CodecBitrate.Unknown] when not reported. */
    val bitrate: CodecBitrate = CodecBitrate.Unknown,
    /**
     * Quality/priority mode as reported (LDAC's sound-quality/balanced/
     * connection-quality/adaptive); [QualityMode.UNKNOWN] when not reported or
     * when the codec has no such parameter.
     */
    val qualityMode: QualityMode = QualityMode.UNKNOWN,
) {
    companion object {
        /** A metadata record that claims nothing. */
        fun unknown(): CodecMetadata = CodecMetadata()
    }
}
