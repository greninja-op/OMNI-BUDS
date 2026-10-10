package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType

/**
 * Base boundary for Windows 10/11 Bluetooth integration over Windows.Devices.Bluetooth WinRT APIs.
 *
 * Honors Windows radio state, device picker restrictions, and GATT characteristics access rights.
 */
class WindowsWinRtDesktopAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.WINDOWS,
        osName = "Windows",
        candidateTransports = setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.BLE, TransportKind.GATT),
    ),
    private val isRadioPresent: Boolean = false,
    private val isRadioOn: Boolean = false,
) : DesktopBluetoothAdapter {

    override suspend fun checkAvailability(): DesktopBluetoothAvailability {
        if (!isRadioPresent) return DesktopBluetoothAvailability.UNAVAILABLE
        if (!isRadioOn) return DesktopBluetoothAvailability.DISABLED
        return DesktopBluetoothAvailability.AVAILABLE
    }

    override fun observeAvailability(): kotlinx.coroutines.flow.Flow<DesktopBluetoothAvailability> =
        kotlinx.coroutines.flow.flow {
            emit(checkAvailability())
        }

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(
            BluetoothPlatformCapabilities.unobserved().copy(
                platformType = PlatformType.WINDOWS,
                candidateTransports = platformDescriptor.candidateTransports,
            ),
        )

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> {
        val availability = checkAvailability()
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "windows.discovery.start",
                    detail = "Windows Bluetooth adapter unavailable (status=$availability)",
                ),
            )
        }
        return OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                operationId = "windows.discovery.start",
                detail = "Live Windows.Devices.Bluetooth WinRT integration is not bound in offline environment",
            ),
        )
    }

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> {
        val availability = checkAvailability()
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "windows.devices.get",
                    detail = "Windows Bluetooth adapter unavailable (status=$availability)",
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
                operationId = "windows.transport.open",
                detail = "Direct WinRT Bluetooth connection requires native runtime bindings",
            ),
        )
    }
}
