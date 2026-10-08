package com.omnibuds.core.audio

/**
 * The kind of an observed audio device, in platform-independent vocabulary.
 *
 * This mirrors the device-type axis Android exposes through
 * `AudioDeviceInfo.getType()` — but as a closed domain enum, so the core never
 * touches the framework class and never compares raw integers outside the single
 * mapping function in the platform module (OB-P10-REQ-005, OB-P10-REQ-012).
 * Only the Bluetooth-relevant types plus the common non-Bluetooth sinks are
 * modelled: Phase 10 observes Bluetooth audio transport, and the other entries
 * exist so a snapshot can say "audio is going to the phone speaker" instead of
 * going silent about where it went.
 *
 * A device type is not a transport claim. A `BLUETOOTH_A2DP` device tells you
 * the system sees an A2DP sink; whether the A2DP *profile* reports connected is
 * a separate observation owned by [AudioProfileState], and the two are
 * reconciled rather than assumed consistent (OB-P10-REQ-016).
 */
enum class AudioDeviceType {
    /** Classic Bluetooth A2DP sink, e.g. earbuds or a speaker in media mode. */
    BLUETOOTH_A2DP,

    /** Classic Bluetooth SCO device, typically the HFP/HSP call path. */
    BLUETOOTH_SCO,

    /** Bluetooth LE Audio headset (`AudioDeviceInfo.TYPE_BLE_HEADSET`). */
    BLE_HEADSET,

    /** Bluetooth LE Audio speaker (`AudioDeviceInfo.TYPE_BLE_SPEAKER`). */
    BLE_SPEAKER,

    /** The phone's built-in speaker. */
    BUILTIN_SPEAKER,

    /** The phone's earpiece. */
    BUILTIN_EARPIECE,

    /** A wired headset or headphones. */
    WIRED_HEADSET,

    /** USB audio accessory. */
    USB_DEVICE,

    /** The platform reported a device whose type mapped to nothing known. */
    UNKNOWN,
}
