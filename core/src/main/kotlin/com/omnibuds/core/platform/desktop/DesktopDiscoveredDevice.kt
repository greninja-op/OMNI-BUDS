package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.TransportKind

/**
 * Single observation of a Bluetooth device on a desktop platform.
 *
 * Encapsulates facts reported directly by the operating system layer,
 * preserving provenance and identity confidence without assuming MAC addresses
 * or display names are always present, non-blank, or unique.
 */
data class DesktopDiscoveredDevice(
    /** Platform-specific device identifier (e.g. BlueZ object path, CoreBluetooth UUID string). */
    val platformIdentifier: String,

    /** Human-readable device name if exposed by the OS, or null. */
    val name: String? = null,

    /** Bluetooth hardware address (e.g. MAC string) if permitted and exposed by the OS, or null. */
    val bluetoothAddress: String? = null,

    /** Set of transports observed or supported for this device. */
    val observedTransports: Set<TransportKind> = emptySet(),

    /** Signal strength (RSSI in dBm) if reported during discovery, or null. */
    val rssiDbm: Int? = null,

    /** Whether device is currently paired or bonded at the OS level. */
    val isPaired: Boolean = false,

    /** Whether device is currently connected at the OS level (e.g. OS audio profile active). */
    val isConnectedAtOsLevel: Boolean = false,

    /** Epoch timestamp in milliseconds when this observation was produced. */
    val observedAtEpochMillis: Long? = null,

    /** Provenance metadata indicating which desktop backend reported this device. */
    val backendSource: String = "desktop-backend",
) {
    init {
        require(platformIdentifier.isNotBlank()) {
            "platformIdentifier must not be blank"
        }
    }

    /** Safe display representation redacting private hardware address details. */
    val safeLabel: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Device[${platformIdentifier.take(8)}...]"
}
