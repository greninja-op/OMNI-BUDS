package com.omnibuds.core.audio

/**
 * One audio device the platform reported, in domain vocabulary.
 *
 * This is deliberately separate from Bluetooth device identity (OB-P10-REQ-008):
 * an [ObservedAudioDevice] is what Android's audio subsystem sees — a sink or
 * source with a platform-scoped id — while the Bluetooth device is what the
 * radio sees. The two are associated where the platform makes the association
 * safe, and left unassociated where it does not. In particular:
 *
 * - [platformDeviceId] is the `AudioDeviceInfo.getId()` value: a handle valid
 *   for the current audio session only. It must never be stored as a persistent
 *   user-device identity, never used as a database key across restarts, and
 *   never treated as proof that two observations are the same physical device.
 * - [bluetoothAddress] is present only when the platform legitimately exposed
 *   it for this audio device. Absence is normal (many audio-device callbacks
 *   carry no address) and must not be "resolved" by guessing from names.
 * - [productName] is a display label, not an identity: two devices may share a
 *   name, and one device may change its name. Devices are never merged on name
 *   similarity (OB-P10-REQ-015).
 *
 * Every field that the platform may withhold is nullable or [AudioDeviceType.UNKNOWN]:
 * a partial device record is a valid observation, and the engine renders exactly
 * what was reported (ADR-P0-016).
 */
data class ObservedAudioDevice(
    /** Platform-scoped device handle. Not a persistent identity — see above. */
    val platformDeviceId: Int,
    /** What kind of device the platform says this is. */
    val type: AudioDeviceType,
    /** Human-readable label where exposed, e.g. "Pixel Buds Pro". Never an identity. */
    val productName: String?,
    /** Bluetooth MAC where legitimately exposed; null is normal, not an error. */
    val bluetoothAddress: String?,
    /** Which way audio flows on this device, as observed. */
    val direction: AudioDirection,
    /** Whether this device is currently in the platform's active audio path. */
    val isActive: Boolean,
    /**
     * Which platform API produced this record (e.g. "AudioDeviceCallback").
     * Provenance for diagnostics, so a contradiction can be traced to its source.
     */
    val source: String,
) {
    init {
        require(productName == null || productName.isNotBlank()) {
            "productName must be absent (null) or non-blank, was \"$productName\""
        }
        require(source.isNotBlank()) { "source must name the platform API that produced this record" }
    }
}
