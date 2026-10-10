package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType

/**
 * Deterministic, unconfigured/unsupported desktop adapter representing an environment
 * where no supported desktop Bluetooth subsystem is available.
 *
 * Truthfully reports UNSUPPORTED status without faking hardware presence.
 */
class UnsupportedDesktopBluetoothAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.DESKTOP_GENERIC,
        osName = "Generic Desktop",
        candidateTransports = emptySet(),
    ),
    private val reason: String = "No desktop Bluetooth subsystem is configured or supported on this host",
) : DesktopBluetoothAdapter {

    override suspend fun checkAvailability(): DesktopBluetoothAvailability =
        DesktopBluetoothAvailability.UNSUPPORTED

    override fun observeAvailability(): kotlinx.coroutines.flow.Flow<DesktopBluetoothAvailability> =
        kotlinx.coroutines.flow.flowOf(DesktopBluetoothAvailability.UNSUPPORTED)

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(
            BluetoothPlatformCapabilities.unobserved().copy(
                platformType = platformDescriptor.platformType,
                candidateTransports = emptySet(),
            ),
        )

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                operationId = "desktop.discovery.start",
                detail = reason,
            ),
        )

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> =
        OperationOutcome.Success(emptyList())

    override suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                operationId = "desktop.transport.open",
                detail = "Transport $transportKind is unsupported on generic desktop: $reason",
            ),
        )
}
