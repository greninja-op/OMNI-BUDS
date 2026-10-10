package com.omnibuds.desktop.presentation.devices

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability
import com.omnibuds.core.platform.desktop.DesktopDiscoveredDevice

/**
 * Visual representation of an observed device in the devices list.
 */
data class DiscoveredDeviceItem(
    val platformIdentifier: String,
    val name: String?,
    val safeLabel: String,
    val bluetoothAddress: String?,
    val observedTransports: Set<TransportKind>,
    val rssiDbm: Int?,
    val isPaired: Boolean,
    val isConnectedAtOsLevel: Boolean,
    val observedAtEpochMillis: Long?,
) {
    companion object {
        fun fromDomain(domain: DesktopDiscoveredDevice): DiscoveredDeviceItem =
            DiscoveredDeviceItem(
                platformIdentifier = domain.platformIdentifier,
                name = domain.name,
                safeLabel = domain.safeLabel,
                bluetoothAddress = domain.bluetoothAddress,
                observedTransports = domain.observedTransports,
                rssiDbm = domain.rssiDbm,
                isPaired = domain.isPaired,
                isConnectedAtOsLevel = domain.isConnectedAtOsLevel,
                observedAtEpochMillis = domain.observedAtEpochMillis,
            )
    }
}

/**
 * Immutable screen state for the Devices screen.
 */
data class DevicesScreenState(
    val availability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.UNKNOWN,
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<DiscoveredDeviceItem> = emptyList(),
    val knownDevices: List<DiscoveredDeviceItem> = emptyList(),
    val selectedDeviceIdentifier: String? = null,
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = false,
    val isLoading: Boolean = false,
) {
    val isReadyForDiscovery: Boolean
        get() = availability.isUsable && !isDiscovering

    val isEmptyList: Boolean
        get() = discoveredDevices.isEmpty() && knownDevices.isEmpty()

    val permissionGuidance: String?
        get() = when (availability) {
            DesktopBluetoothAvailability.PERMISSION_REQUIRED ->
                "Bluetooth permission is required by the host operating system. Please grant permission in System Settings."
            DesktopBluetoothAvailability.PERMISSION_DENIED ->
                "Bluetooth permission was denied. Please allow OmniBuds to access Bluetooth in OS security preferences."
            DesktopBluetoothAvailability.DISABLED ->
                "Bluetooth radio is powered off or disabled by hardware kill-switch (rfkill). Enable Bluetooth to scan."
            DesktopBluetoothAvailability.UNAVAILABLE ->
                "No Bluetooth adapter hardware detected on this desktop computer."
            DesktopBluetoothAvailability.UNSUPPORTED ->
                "Bluetooth operations are not supported by the current desktop platform subsystem."
            else -> null
        }
}

/**
 * User actions supported on the Devices screen.
 */
sealed interface DevicesAction {
    data object StartDiscovery : DevicesAction
    data object StopDiscovery : DevicesAction
    data object RefreshStatus : DevicesAction
    data object ClearError : DevicesAction
    data class SelectDevice(val deviceIdentifier: String) : DevicesAction
}
