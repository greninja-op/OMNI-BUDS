package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType

/**
 * Base boundary for Linux Bluetooth adapters interacting with the system BlueZ daemon over DBus.
 *
 * Honors Linux permissions, daemon lifecycle, adapter power state, and RFC/BLE constraints.
 */
class LinuxBlueZDesktopAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.LINUX,
        osName = "Linux",
        candidateTransports = setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.BLE, TransportKind.RFCOMM),
    ),
    private val isDaemonAvailable: Boolean = false,
    private val isAdapterPowered: Boolean = false,
) : DesktopBluetoothAdapter {

    override suspend fun checkAvailability(): DesktopBluetoothAvailability {
        if (!isDaemonAvailable) return DesktopBluetoothAvailability.UNAVAILABLE
        if (!isAdapterPowered) return DesktopBluetoothAvailability.DISABLED
        return DesktopBluetoothAvailability.AVAILABLE
    }

    override fun observeAvailability(): kotlinx.coroutines.flow.Flow<DesktopBluetoothAvailability> =
        kotlinx.coroutines.flow.flow {
            emit(checkAvailability())
        }

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(
            BluetoothPlatformCapabilities.unobserved().copy(
                platformType = PlatformType.LINUX,
                candidateTransports = platformDescriptor.candidateTransports,
            ),
        )

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> {
        val availability = checkAvailability()
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "bluez.discovery.start",
                    detail = "BlueZ adapter unavailable (status=$availability)",
                ),
            )
        }
        return OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                operationId = "bluez.discovery.start",
                detail = "Live BlueZ D-Bus socket integration is not initialized in offline environment",
            ),
        )
    }

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> {
        val availability = checkAvailability()
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "bluez.devices.get",
                    detail = "BlueZ adapter unavailable (status=$availability)",
                ),
            )
        }
        return OperationOutcome.Success(emptyList())
    }

    override suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession> {
        return OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                operationId = "bluez.transport.open",
                detail = "Direct BlueZ transport connections require native D-Bus / socket bindings",
            ),
        )
    }
}
