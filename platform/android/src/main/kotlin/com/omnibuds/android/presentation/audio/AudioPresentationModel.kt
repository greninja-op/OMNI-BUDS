package com.omnibuds.android.presentation.audio

import com.omnibuds.core.globalstate.AudioState

/**
 * Status of a specific codec capability.
 */
data class CodecStatusItem(
    val codecName: String,
    val isSupported: Boolean,
    val isConfigurable: Boolean = false,
    val isNegotiated: Boolean = false,
    val isActive: Boolean = false,
)

/**
 * Honest, evidence-backed audio and codec presentation model for Android.
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
        fun unavailable(
            reason: String = "Android operating system does not expose active Bluetooth audio codec telemetry via public APIs.",
        ): AudioPresentationModel = AudioPresentationModel(
            activeCodecName = null,
            isCodecObservable = false,
            isCodecSelectable = false,
            knownSupportedCodecs = emptyList(),
            audioTransport = null,
            routeDescription = null,
            observableSampleRateHz = null,
            observableBitDepth = null,
            unavailableReason = reason,
        )

        fun fromCoreState(audioState: AudioState): AudioPresentationModel {
            val observedCodec = audioState.codec?.value
            val isObservable = audioState.codecObservable

            val reason = if (!isObservable) {
                "Active codec is not observable: Android public Bluetooth API does not expose negotiated A2DP codec telemetry without private APIs."
            } else if (observedCodec == null) {
                "Codec state has not been reported by the audio transport."
            } else {
                null
            }

            return AudioPresentationModel(
                activeCodecName = if (isObservable) observedCodec else null,
                isCodecObservable = isObservable,
                isCodecSelectable = false, // Public Android API has no third-party codec switching API
                knownSupportedCodecs = emptyList(), // Not assumed without device descriptor evidence
                audioTransport = audioState.route?.value,
                routeDescription = audioState.route?.value,
                observableSampleRateHz = null,
                observableBitDepth = null,
                unavailableReason = reason,
            )
        }
    }
}
