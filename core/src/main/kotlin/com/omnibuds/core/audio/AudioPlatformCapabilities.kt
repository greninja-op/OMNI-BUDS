package com.omnibuds.core.audio

/**
 * What the platform itself can do about audio, independent of any headset.
 *
 * Platform capability is the "supported" rung of the ladder from master Phase
 * 10 rule 3, and it lives here — once, as data — so no observation ever has to
 * re-derive it from a device. API availability is not device capability
 * (OB-P10-REQ-019): `leAudioApiAvailable = true` on an API-33 phone says the
 * *phone* exposes `BluetoothLeAudio`; it says nothing about whether any
 * connected headset speaks LE Audio, and the reconciler must never let it.
 *
 * This type is pure data with no Android imports so the core stays
 * platform-independent; the platform module fills it in from
 * `Build.VERSION.SDK_INT` and the feature probes it already owns.
 */
data class AudioPlatformCapabilities(
    /** True when the OS API level exposes the LE Audio platform APIs (>= 33). */
    val leAudioApiAvailable: Boolean,
    /** The API level the answers below were determined on; null when undetermined. */
    val apiLevel: Int?,
    /** Whether the platform stack implements A2DP at all. */
    val a2dpSupported: Boolean,
    /** Whether the platform stack implements HFP at all. */
    val hfpSupported: Boolean,
    /** Whether the platform stack implements HSP at all. */
    val hspSupported: Boolean,
    /** Whether audio-device callbacks are available for observation. */
    val audioDeviceCallbackSupported: Boolean,
) {
    init {
        require(apiLevel == null || apiLevel > 0) {
            "apiLevel must be undetermined (null) or positive, was $apiLevel"
        }
    }

    companion object {
        /** The honest answer before the platform has been asked. */
        val UNKNOWN = AudioPlatformCapabilities(
            leAudioApiAvailable = false,
            apiLevel = null,
            a2dpSupported = false,
            hfpSupported = false,
            hspSupported = false,
            audioDeviceCallbackSupported = false,
        )
    }
}
