package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.QualityMode
import com.omnibuds.core.device.DeviceIdentity

/**
 * An immutable point-in-time quality profile.
 *
 * Phase 13 (OB-P13-REQ-013): a snapshot of the observable audio quality facts
 * at one moment, suitable for diagnostics and bounded history. It carries no
 * negotiation state, no evidence, no freshness — for the full picture see
 * [AudioQualityState]. Immutable: safe to retain in history without aliasing
 * the live state.
 */
data class QualityProfile(
    val device: DeviceIdentity,
    val transport: AudioTransportKind,
    val codec: Codec,
    val codecState: CodecState,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val bitrate: CodecBitrate,
    val channelMode: ChannelMode,
    val qualityMode: QualityMode,
    val adaptiveState: AdaptiveState,
    val timestampMillis: Long,
) {
    init {
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
    }
}
