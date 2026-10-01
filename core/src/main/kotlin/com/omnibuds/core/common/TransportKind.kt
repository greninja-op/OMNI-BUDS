package com.omnibuds.core.common

/**
 * How a control channel may reach a device.
 *
 * No transport may be assumed: some headsets expose control over GATT, some over
 * RFCOMM, some over LE Audio, and some combine BLE for bookkeeping with a classic
 * channel for configuration (ADR-P0-003). [UNKNOWN] means the session has not
 * established one yet, which is not the same statement as "none is available".
 */
enum class TransportKind {
    /**
     * The BLE physical/link layer as a distinct boundary: advertising, scanning and the link a
     * GATT service may or may not sit on top of. Phase 2 originally listed only GATT, which left
     * `BleTransport` with nothing to report as its kind - BLE is not simply GATT (master section 8).
     */
    BLE,

    /** BLE GATT service/characteristic access. */
    GATT,

    /** Classic serial port profile socket. */
    RFCOMM,

    /** Other classic Bluetooth channel (SDP-discovered, vendor defined). */
    CLASSIC_BLUETOOTH,

    /** LE Audio / isochronous related control. */
    LE_AUDIO,

    /** Manufacturer-specific channel that is none of the standard kinds. */
    VENDOR_SPECIFIC,

    /** Not yet determined, or determined to be unavailable on this platform. */
    UNKNOWN,
}
