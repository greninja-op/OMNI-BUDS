package com.omnibuds.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability
import com.omnibuds.core.platform.desktop.DesktopDeviceDiscoverySession
import com.omnibuds.core.platform.desktop.DesktopDiscoveredDevice
import com.omnibuds.core.platform.desktop.DesktopTransportSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test double for DesktopBluetoothAdapter used in desktop UI tests.
 */
class SimulatedDesktopAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.LINUX,
        osName = "Linux Test",
        candidateTransports = setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.BLE),
    ),
    initialAvailability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.AVAILABLE,
) : DesktopBluetoothAdapter {

    private val mutex = Mutex()
    private var availability = initialAvailability
    private val availabilityFlow = MutableSharedFlow<DesktopBluetoothAvailability>(replay = 1)
    private val knownDevices = mutableListOf<DesktopDiscoveredDevice>()
    private var activeDiscoverySession: TestDiscoverySession? = null

    var shouldFailDiscoveryStart: Boolean = false

    init {
        availabilityFlow.tryEmit(initialAvailability)
    }

    suspend fun setAvailability(newAvailability: DesktopBluetoothAvailability) {
        val sessionToStop: TestDiscoverySession?
        mutex.withLock {
            availability = newAvailability
            availabilityFlow.emit(newAvailability)
            sessionToStop = if (!newAvailability.isUsable) activeDiscoverySession else null
        }
        sessionToStop?.stop()
    }

    suspend fun addKnownDevice(device: DesktopDiscoveredDevice) {
        mutex.withLock {
            knownDevices.removeAll { it.platformIdentifier == device.platformIdentifier }
            knownDevices.add(device)
        }
    }

    suspend fun emitDiscoveredDevice(device: DesktopDiscoveredDevice) {
        mutex.withLock {
            activeDiscoverySession?.emit(device)
        }
    }

    override suspend fun checkAvailability(): DesktopBluetoothAvailability = mutex.withLock {
        availability
    }

    override fun observeAvailability(): Flow<DesktopBluetoothAvailability> =
        availabilityFlow.asSharedFlow()

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(BluetoothPlatformCapabilities.unobserved())

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> = mutex.withLock {
        if (!availability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "test.discovery.start",
                    detail = "Adapter is not usable (status=$availability)",
                ),
            )
        }
        if (shouldFailDiscoveryStart) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
                    operationId = "test.discovery.start",
                    detail = "Simulated failure starting discovery scan",
                ),
            )
        }
        val session = TestDiscoverySession(onStop = {
            mutex.withLock {
                if (activeDiscoverySession === it) {
                    activeDiscoverySession = null
                }
            }
        })
        activeDiscoverySession = session
        OperationOutcome.Success(session)
    }

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> = mutex.withLock {
        OperationOutcome.Success(knownDevices.toList())
    }

    override suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                operationId = "test.transport.open",
                detail = "Simulated transport open",
            ),
        )
}

class TestDiscoverySession(
    private val onStop: suspend (TestDiscoverySession) -> Unit,
) : DesktopDeviceDiscoverySession {
    private var active = true
    private val flow = MutableSharedFlow<DesktopDiscoveredDevice>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val isActive: Boolean get() = active
    override val observations: Flow<DesktopDiscoveredDevice> = flow.asSharedFlow()

    suspend fun emit(device: DesktopDiscoveredDevice) {
        if (!active) return
        flow.emit(device)
    }

    override suspend fun stop(): OperationOutcome<Unit> {
        active = false
        onStop(this)
        return OperationOutcome.Success(Unit)
    }
}
