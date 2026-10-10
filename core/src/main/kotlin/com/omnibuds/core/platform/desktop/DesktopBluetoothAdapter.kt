package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Primary contract for the desktop Bluetooth adapter layer.
 *
 * Exposes adapter status, discovery, enumeration of paired/connected devices,
 * and transport session instantiation without coupling the core domain to OS-specific APIs.
 */
interface DesktopBluetoothAdapter {
    /** Describes this desktop host environment, OS name/version, and candidate capabilities. */
    val platformDescriptor: PlatformDescriptor

    /** Read current adapter availability synchronously. */
    suspend fun checkAvailability(): DesktopBluetoothAvailability

    /** Continuous stream of adapter availability transitions (e.g. power toggle, rfkill). */
    fun observeAvailability(): Flow<DesktopBluetoothAvailability>

    /** Capabilities snapshot of this desktop Bluetooth subsystem. */
    suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities>

    /** Initiates device discovery. */
    suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession>

    /** Enumerates devices currently known or paired at the OS level. */
    suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>>

    /**
     * Opens a transport session to [deviceIdentifier] over [transportKind].
     * Fails with structured error if transport is unsupported or device is unreachable.
     */
    suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession>
}
