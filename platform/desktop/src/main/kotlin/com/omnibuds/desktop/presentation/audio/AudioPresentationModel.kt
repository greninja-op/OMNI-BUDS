package com.omnibuds.desktop.presentation.audio

import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.presentation.audio.UnifiedAudioModel
import com.omnibuds.core.presentation.audio.UnifiedCodecItem

/**
 * Status of a specific codec capability.
 * Backed by unified core model.
 */
data class CodecStatusItem(
    val codecName: String,
    val isSupported: Boolean,
    val isConfigurable: Boolean = false,
    val isNegotiated: Boolean = false,
    val isActive: Boolean = false,
) {
    fun toUnified(): UnifiedCodecItem = UnifiedCodecItem(
        codecName = codecName,
        isSupported = isSupported,
        isConfigurable = isConfigurable,
        isNegotiated = isNegotiated,
        isActive = isActive,
    )

    companion object {
        fun fromUnified(core: UnifiedCodecItem): CodecStatusItem =
            CodecStatusItem(
                codecName = core.codecName,
                isSupported = core.isSupported,
                isConfigurable = core.isConfigurable,
                isNegotiated = core.isNegotiated,
                isActive = core.isActive,
            )
    }
}

/**
 * Honest, evidence-backed audio and codec presentation model for desktop.
 * Adapts unified core model (com.omnibuds.core.presentation.audio.UnifiedAudioModel).
 */
data class AudioPresentationModel(
    val activeCodecName: String? = null,
    val isCodecObservable: Boolean = false,
    val knownSupportedCodecs: List<CodecStatusItem> = emptyList(),
    val audioTransport: String? = null,
    val routeDescription: String? = null,
    val observableSampleRateHz: Int? = null,
    val observableBitDepth: Int? = null,
    val unavailableReason: String? = null,
) {
    val hasObservedCodec: Boolean get() = isCodecObservable && activeCodecName != null

    companion object {
        fun fromUnified(core: UnifiedAudioModel): AudioPresentationModel =
            AudioPresentationModel(
                activeCodecName = core.activeCodecName,
                isCodecObservable = core.isCodecObservable,
                knownSupportedCodecs = core.knownSupportedCodecs.map { CodecStatusItem.fromUnified(it) },
                audioTransport = core.audioTransport,
                routeDescription = core.routeDescription,
                observableSampleRateHz = core.observableSampleRateHz,
                observableBitDepth = core.observableBitDepth,
                unavailableReason = core.unavailableReason,
            )

        fun unavailable(
            reason: String = "Host operating system does not expose active Bluetooth audio codec telemetry via public APIs.",
        ): AudioPresentationModel = fromUnified(UnifiedAudioModel.unavailable(reason))

        fun fromCoreState(audioState: AudioState): AudioPresentationModel =
            fromUnified(UnifiedAudioModel.fromCoreState(audioState))
    }
}
