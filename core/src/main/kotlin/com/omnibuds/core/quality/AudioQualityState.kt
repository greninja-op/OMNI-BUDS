package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.QualityMode
import com.omnibuds.core.device.DeviceIdentity

/**
 * The unified runtime audio quality state for one device.
 *
 * Phase 13 (OB-P13-REQ-001, OB-P13-REQ-008): every field is independently
 * observable or unknown. No field is inferred from another:
 *
 * - [negotiatedCodec] vs [activeCodec]: negotiation success ≠ media flowing
 *   (OB-P13-REQ-004).
 * - [supportedSampleRatesHz] vs [activeSampleRateHz]: capability ≠ reality
 *   (OB-P13-REQ-007).
 * - [observedBitrate] vs [supportedBitrates]: never derived from codec
 *   identity (OB-P13-REQ-009).
 * - [configuredQualityMode] vs [observedQualityMode]: configured ≠ active
 *   (OB-P13-REQ-011).
 * - [isCurrent]: false when [freshness] is not CURRENT — historical state
 *   never masquerades as current (OB-P13-REQ-014).
 * - [hasConflict]: true when sources disagreed and precedence was applied
 *   (OB-P13-REQ-020).
 *
 * Immutable value: equality-aware dedup suppresses duplicate emissions
 * (OB-P13-REQ-021, OB-P13-REQ-024).
 */
data class AudioQualityState(
    val device: DeviceIdentity,
    val transport: AudioTransportKind = AudioTransportKind.UNKNOWN,
    /** Whether the audio route is currently live for this device. */
    val routeActive: Boolean? = null,
    /** Whether the transport reports the device connected. */
    val transportConnected: Boolean? = null,
    val negotiationState: NegotiationState = NegotiationState.UNKNOWN,
    /** The codec that was negotiated (may differ from the active codec). */
    val negotiatedCodec: Codec = Codec.UNKNOWN,
    /** The codec currently carrying audio (UNKNOWN when unobservable). */
    val activeCodec: Codec = Codec.UNKNOWN,
    val codecState: CodecState = CodecState.UNKNOWN,
    // --- Sample rate (OB-P13-REQ-007) ---
    val supportedSampleRatesHz: List<Int> = emptyList(),
    val activeSampleRateHz: Int? = null,
    // --- Bit depth (OB-P13-REQ-008) ---
    val supportedBitDepths: List<Int> = emptyList(),
    val activeBitDepth: Int? = null,
    // --- Bitrate (OB-P13-REQ-009) ---
    val supportedBitrates: CodecBitrate = CodecBitrate.Unknown,
    val configuredBitrate: CodecBitrate = CodecBitrate.Unknown,
    val observedBitrate: CodecBitrate = CodecBitrate.Unknown,
    val adaptiveBitrate: Boolean? = null,
    // --- Channel mode (OB-P13-REQ-010) ---
    val channelMode: ChannelMode = ChannelMode.UNKNOWN,
    // --- Quality mode (OB-P13-REQ-011) ---
    val configuredQualityMode: QualityMode = QualityMode.UNKNOWN,
    val observedQualityMode: QualityMode = QualityMode.UNKNOWN,
    // --- Adaptive state (OB-P13-REQ-012) ---
    val adaptiveState: AdaptiveState = AdaptiveState.UNKNOWN,
    // --- Meta ---
    val evidence: CodecEvidence,
    val observability: CodecObservability = CodecObservability.UNKNOWN,
    val freshness: CodecFreshness = CodecFreshness.UNKNOWN,
    val hasConflict: Boolean = false,
    val timestampMillis: Long = 0L,
) {
    /** True only when this state may be treated as current. */
    val isCurrent: Boolean get() = freshness == CodecFreshness.CURRENT

    init {
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
        require(supportedSampleRatesHz.all { it > 0 }) {
            "supported sample rates must be positive"
        }
        require(activeSampleRateHz == null || activeSampleRateHz > 0) {
            "active sample rate must be unreported (null) or positive"
        }
    }

    /** The point-in-time quality profile for diagnostics/history. */
    fun toProfile(): QualityProfile = QualityProfile(
        device = device,
        transport = transport,
        codec = activeCodec,
        codecState = codecState,
        sampleRateHz = activeSampleRateHz,
        bitDepth = activeBitDepth,
        bitrate = observedBitrate,
        channelMode = channelMode,
        qualityMode = observedQualityMode,
        adaptiveState = adaptiveState,
        timestampMillis = timestampMillis,
    )
}
