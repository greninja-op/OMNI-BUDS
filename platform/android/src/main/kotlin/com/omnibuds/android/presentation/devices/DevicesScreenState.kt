package com.omnibuds.android.presentation.devices

import com.omnibuds.android.compat.BluetoothPlatformState
import com.omnibuds.android.compat.PermissionState
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.ConnectionState

/**
 * Screen modes required by Phase 49 Section 6.
 */
enum class DevicesScreenMode {
    INITIAL_LOADING,
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_DISABLED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    DISCOVERY_IN_PROGRESS,
    DEVICES_FOUND,
    NO_DEVICES_FOUND,
    KNOWN_DEVICES_TEMPORARILY_UNAVAILABLE,
    DISCOVERY_FAILED,
    PLATFORM_UNSUPPORTED,
}

/**
 * Discovered or paired device represented in the Android UI.
 */
data class AndroidDiscoveredDevice(
    val id: String,
    val name: String?,
    val address: String,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val rssi: Int? = null,
    val transportKind: TransportKind = TransportKind.BLE,
    val batterySummary: String? = null,
    val isSupported: Boolean = true,
    val lastSeenTimestamp: Long = 0L,
) {
    val displayName: String get() = name ?: "Unknown Device ($address)"

    /** Redacted identifier for safe logging and diagnostics. */
    val redactedAddress: String get() {
        val parts = address.split(":")
        return if (parts.size >= 4) {
            "${parts[0]}:${parts[1]}:${parts[2]}:XX:XX:XX"
        } else {
            "REDACTED_DEVICE"
        }
    }
}

/**
 * Immutable UI state for the Android Home and Device Management Screen.
 */
data class DevicesScreenState(
    val bluetoothState: BluetoothPlatformState = BluetoothPlatformState(
        adapterPresent = true,
        adapterEnabled = true,
        connectPermission = PermissionState.GRANTED,
        scanPermission = PermissionState.GRANTED,
    ),
    val isDiscovering: Boolean = false,
    val isLoading: Boolean = false,
    val discoveredDevices: List<AndroidDiscoveredDevice> = emptyList(),
    val knownDevices: List<AndroidDiscoveredDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val errorBanner: String? = null,
    val isRecoverable: Boolean = true,
    val permissionGuidance: String? = null,
) {
    val totalDeviceCount: Int get() = (discoveredDevices.map { it.id } + knownDevices.map { it.id }).distinct().size

    val screenMode: DevicesScreenMode
        get() = when {
            isLoading -> DevicesScreenMode.INITIAL_LOADING
            !bluetoothState.adapterPresent -> DevicesScreenMode.BLUETOOTH_UNAVAILABLE
            !bluetoothState.adapterEnabled -> DevicesScreenMode.BLUETOOTH_DISABLED
            bluetoothState.connectPermission == PermissionState.DENIED -> DevicesScreenMode.PERMISSION_DENIED
            bluetoothState.connectPermission != PermissionState.GRANTED -> DevicesScreenMode.PERMISSION_REQUIRED
            errorBanner != null && isDiscovering -> DevicesScreenMode.DISCOVERY_FAILED
            isDiscovering -> DevicesScreenMode.DISCOVERY_IN_PROGRESS
            discoveredDevices.isNotEmpty() || knownDevices.isNotEmpty() -> DevicesScreenMode.DEVICES_FOUND
            else -> DevicesScreenMode.NO_DEVICES_FOUND
        }
}
