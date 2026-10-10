package com.omnibuds.android.presentation.audio

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
 * Honest, evidence-backed audio and codec presentation model for Android.
 * Adapts unified core model (com.omnibuds.core.presentation.audio.UnifiedAudioModel).
 * Never fabricates active codec readings or fake selectable codec controls.
 */
data class AudioPresentationModel(
    val activeCodecName: String? = null,
    val isCodecObservable: Boolean = false,
    val isCodecSelectable: Boolean = false,
    val knownSupportedCodecs: List<CodecStatusItem> = emptyList(),
    val audioTransport: String? = null,
    val routeDescription: String? = null,
    val observableSampleRateHz: Int? = null,
    val observableBitDepth: Int? = null,
    val unavailableReason: String? = null,
) {
    val hasObservedCodec: Boolean get() = isCodecObservable && activeCodecName != null

    val talkBackDescription: String
        get() {
            val parts = mutableListOf<String>()
            routeDescription?.let { parts.add("Audio route: $it") }
            if (isCodecObservable && activeCodecName != null) {
                parts.add("Active codec: $activeCodecName")
            } else {
                parts.add("Active codec is not observable on Android public APIs")
            }
            if (!isCodecSelectable) {
                parts.add("Codec switching is not supported by public Android APIs")
            }
            return parts.joinToString(", ")
        }

    companion object {
        fun fromUnified(core: UnifiedAudioModel): AudioPresentationModel =
            AudioPresentationModel(
                activeCodecName = core.activeCodecName,
                isCodecObservable = core.isCodecObservable,
                isCodecSelectable = core.isCodecSelectable,
                knownSupportedCodecs = core.knownSupportedCodecs.map { CodecStatusItem.fromUnified(it) },
                audioTransport = core.audioTransport,
                routeDescription = core.routeDescription,
                observableSampleRateHz = core.observableSampleRateHz,
                observableBitDepth = core.observableBitDepth,
                unavailableReason = core.unavailableReason,
            )

        fun unavailable(
            reason: String = "Android operating system does not expose active Bluetooth audio codec telemetry via public APIs.",
        ): AudioPresentationModel = fromUnified(UnifiedAudioModel.unavailable(reason))

        fun fromCoreState(audioState: AudioState): AudioPresentationModel =
            fromUnified(UnifiedAudioModel.fromCoreState(audioState))
    }
}
