package com.omnibuds.core.audio

/**
 * A snapshot of what one audio session was actually observed to be doing.
 *
 * The eight fields are the normative minimum of the quality contract (master section
 * 16, REQ-P0-011, AUD-QUAL-001), with `active` carried as a [CodecState] rather than a
 * boolean: activity is then the [CodecState.ACTIVE] rung exactly, and can never be
 * computed as `negotiated || enabled` (AUD-QUAL-004, ADR-P1-005).
 *
 * Each field is unknown independently. A partial read is a valid result and must
 * render exactly what was read, so every measurement is nullable and stays null when
 * the source did not report it — no `0`, no `-1`, and no plausible default standing
 * in for an absent reading (AUD-QUAL-002, AUD-QUAL-005, ADR-P0-016). A headset that
 * advertises Hi-Res does not license `96000`, `24` or `328` here; those would be
 * fabrications, which is the failure mode this project exists to prevent (Phase 1
 * prompt section 19, master section 16).
 *
 * The validation in [init] accepts only ranges that a real transport can report, so a
 * platform adapter cannot smuggle a sentinel value through as if it were a measurement.
 *
 * This type reports; it does not negotiate, switch or otherwise control a codec, and
 * it owns no Bluetooth or Android audio behaviour (Phase 1 prompt sections 2, 18, 51).
 */
data class AudioTransportState(
    /** Transport the observation belongs to; [AudioTransportKind.UNKNOWN] when unread, never a guessed "A2DP". */
    val transport: AudioTransportKind,
    /**
     * The codec observed for this session, or null when the source reported nothing.
     * Null and [Codec.UNKNOWN] both mean undetermined; neither means unsupported.
     */
    val codec: Codec?,
    /** Where that codec actually got to on the evidence ladder — the source of [isActive]. */
    val state: CodecState,
    /** Sample rate in hertz as reported, or null when unreported. */
    val sampleRateHz: Int?,
    /** Bits per sample as reported, or null when unreported. */
    val bitsPerSample: Int?,
    /** Bitrate in kilobits per second as reported, or null when unreported. */
    val bitrateKbps: Int?,
    /** Channel mode as reported; unknown stays [ChannelMode.UNKNOWN]. */
    val channelMode: ChannelMode,
    /** Quality or priority mode as reported; unknown stays [QualityMode.UNKNOWN]. */
    val qualityMode: QualityMode,
) {

    init {
        require(sampleRateHz == null || sampleRateHz > 0) {
            "sampleRateHz must be unreported (null) or positive, was $sampleRateHz"
        }
        require(bitsPerSample == null || bitsPerSample in MIN_BITS_PER_SAMPLE..MAX_BITS_PER_SAMPLE) {
            "bitsPerSample must be unreported (null) or in " +
                "$MIN_BITS_PER_SAMPLE..$MAX_BITS_PER_SAMPLE, was $bitsPerSample"
        }
        require(bitrateKbps == null || bitrateKbps > 0) {
            "bitrateKbps must be unreported (null) or positive, was $bitrateKbps"
        }
    }

    /**
     * Whether this codec is the one carrying the session's media audio.
     *
     * True only at [CodecState.ACTIVE], and only once the verification gate of
     * audio-governance section 11 has been passed by whoever produced this snapshot.
     */
    val isActive: Boolean
        get() = state == CodecState.ACTIVE

    /**
     * Whether every field carries an actual reading.
     *
     * Anything unobserved — a null measurement, [CodecState.UNKNOWN], an
     * [AudioTransportKind.UNKNOWN] transport, [Codec.UNKNOWN] or a null codec,
     * [ChannelMode.UNKNOWN] or [QualityMode.UNKNOWN] — makes this false. It reports
     * completeness only; it says nothing about whether the snapshot is fresh or
     * hardware-verified, which remain the source's responsibility (AUD-STATE-004,
     * AUD-QUAL-006).
     */
    val isFullyObserved: Boolean
        get() = transport != AudioTransportKind.UNKNOWN &&
            codec != null &&
            codec != Codec.UNKNOWN &&
            state != CodecState.UNKNOWN &&
            sampleRateHz != null &&
            bitsPerSample != null &&
            bitrateKbps != null &&
            channelMode != ChannelMode.UNKNOWN &&
            qualityMode != QualityMode.UNKNOWN

    companion object {
        /** Narrowest bit depth a real audio path uses, so 0 and negative sentinels are rejected. */
        const val MIN_BITS_PER_SAMPLE: Int = 8

        /** Widest bit depth representable in this model; wider readings are not yet reportable facts. */
        const val MAX_BITS_PER_SAMPLE: Int = 32

        /**
         * The state to record when nothing has been read, or the session went away:
         * every field genuinely unknown rather than plausible-looking
         * (AUD-TERM-001, AUD-QUAL-002, AUD-QUAL-006).
         *
         * Use this instead of inventing a snapshot — a default of SBC at 44.1 kHz,
         * 16 bit, stereo is a fabrication dressed as an observation.
         */
        fun unobserved(): AudioTransportState = AudioTransportState(
            transport = AudioTransportKind.UNKNOWN,
            codec = null,
            state = CodecState.UNKNOWN,
            sampleRateHz = null,
            bitsPerSample = null,
            bitrateKbps = null,
            channelMode = ChannelMode.UNKNOWN,
            qualityMode = QualityMode.UNKNOWN,
        )
    }
}
