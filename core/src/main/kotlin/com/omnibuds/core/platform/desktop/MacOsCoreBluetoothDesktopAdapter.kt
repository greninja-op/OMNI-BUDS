package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType

/**
 * Base boundary for macOS desktop Bluetooth integration over CoreBluetooth / IOBluetooth.
 *
 * Honors Apple TCC privacy sandbox constraints (e.g. NSBluetoothAlwaysUsageDescription),
 * CoreBluetooth CBManagerState, and GATT limitations on macOS.
 */
class MacOsCoreBluetoothDesktopAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.MACOS,
        osName = "macOS",
        candidateTransports = setOf(TransportKind.BLE, TransportKind.GATT),
    ),
    private val isTccAuthorized: Boolean = false,
    private val isBluetoothPoweredOn: Boolean = false,
) : DesktopBluetoothAdapter {

    override suspend fun checkAvailability(): DesktopBluetoothAvailability {
        if (!isTccAuthorized) return DesktopBluetoothAvailability.PERMISSION_REQUIRED
        if (!isBluetoothPoweredOn) return DesktopBluetoothAvailability.DISABLED
        return DesktopBluetoothAvailability.AVAILABLE
    }

    override fun observeAvailability(): kotlinx.coroutines.flow.Flow<DesktopBluetoothAvailability> =
        kotlinx.coroutines.flow.flow {
            emit(checkAvailability())
        }

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(
            BluetoothPlatformCapabilities.unobserved().copy(
                platformType = PlatformType.MACOS,
                candidateTransports = platformDescriptor.candidateTransports,
            ),
        )

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> {
        val availability = checkAvailability()
        if (availability == DesktopBluetoothAvailability.PERMISSION_REQUIRED) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.PERMISSION_DENIED,
                    operationId = "macos.discovery.start",
                    detail = "macOS Bluetooth permission (TCC) not granted",
                ),
            )
        }
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "macos.discovery.start",
                    detail = "macOS Bluetooth adapter unavailable (status=$availability)",
                ),
            )
        }
        return OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                operationId = "macos.discovery.start",
                detail = "Live CoreBluetooth CBCentralManager integration is not bound in offline environment",
            ),
        )
    }

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> {
        val availability = checkAvailability()
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "macos.devices.get",
                    detail = "macOS Bluetooth adapter unavailable (status=$availability)",
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
                operationId = "macos.transport.open",
                detail = "Direct CoreBluetooth CBPeripheral connection requires native framework bindings",
            ),
        )
    }
}
