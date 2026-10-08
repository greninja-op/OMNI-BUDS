package com.omnibuds.core.audio

/**
 * Where a codec claim comes from.
 *
 * Provenance is load-bearing: a claim sourced from [ANDROID_FRAMEWORK] runtime
 * observation carries more weight than one inferred from a database, and the two
 * must never be presented with the same confidence (Phase 11 RULE 18).
 *
 * OmniBuds must know WHY it believes something. Every [CodecEvidence] carries one
 * of these sources.
 */
enum class CodecEvidenceSource {
    /**
     * Read from the Android framework at runtime (Bluetooth stack, AudioManager).
     * The strongest source available to the platform adapter.
     */
    ANDROID_FRAMEWORK,

    /**
     * Read from [android.media.AudioDeviceInfo] device descriptors.
     * Describes the audio device, not the negotiated codec.
     */
    AUDIO_DEVICE_INFO,

    /**
     * Read from a Bluetooth profile proxy (A2DP, LE Audio) at runtime.
     */
    BLUETOOTH_PROFILE,

    /**
     * Read from platform codec metadata structures ([android.bluetooth.BluetoothCodecConfig]
     * and friends) where the platform exposes them.
     */
    PLATFORM_CODEC_METADATA,

    /**
     * Reported by the device over its control protocol (a later phase's subject).
     * Not available in Phase 11.
     */
    DEVICE_PROTOCOL,

    /**
     * Reported via a vendor-specific protocol extension. Must travel through the
     * existing extensibility architecture, never as a hard-coded hack (RULE 19).
     */
    VENDOR_PROTOCOL,

    /**
     * The claim has no recorded source. The default for constructed or
     * not-yet-observed records.
     */
    UNKNOWN,
}
