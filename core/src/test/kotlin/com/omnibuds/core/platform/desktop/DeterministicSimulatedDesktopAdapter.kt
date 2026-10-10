package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deterministic simulated in-memory desktop adapter for testing discovery, session lifecycle,
 * availability transitions, and failure injection without physical hardware.
 *
 * Isolated strictly to test sources.
 */
class DeterministicSimulatedDesktopAdapter(
    override val platformDescriptor: PlatformDescriptor = PlatformDescriptor(
        platformType = PlatformType.LINUX,
        osName = "Simulated OS",
        candidateTransports = setOf(TransportKind.CLASSIC_BLUETOOTH, TransportKind.BLE, TransportKind.RFCOMM),
    ),
    initialAvailability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.AVAILABLE,
) : DesktopBluetoothAdapter {

    private val mutex = Mutex()
    private var currentAvailability = initialAvailability
    private val availabilityFlow = MutableSharedFlow<DesktopBluetoothAvailability>(replay = 1)

    private val knownDevices = mutableListOf<DesktopDiscoveredDevice>()
    private val activeTransportSessions = mutableMapOf<String, SimulatedDesktopTransportSession>()
    private var currentDiscoverySession: SimulatedDesktopDiscoverySession? = null

    // Failure injection controls
    var shouldFailDiscoveryStart: Boolean = false
    var discoveryStartFailureCategory: OmniBudsErrorCategory = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE
    var shouldFailTransportOpen: Boolean = false
    var transportOpenFailureCategory: OmniBudsErrorCategory = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE

    init {
        availabilityFlow.tryEmit(initialAvailability)
    }

    suspend fun setAvailability(availability: DesktopBluetoothAvailability) {
        val sessionToCancel: SimulatedDesktopDiscoverySession?
        mutex.withLock {
            currentAvailability = availability
            availabilityFlow.emit(availability)
            sessionToCancel = if (!availability.isUsable) currentDiscoverySession else null
        }
        sessionToCancel?.cancelDueToAdapterFailure(availability)
    }

    suspend fun addKnownDevice(device: DesktopDiscoveredDevice) {
        mutex.withLock {
            knownDevices.removeAll { it.platformIdentifier == device.platformIdentifier }
            knownDevices.add(device)
        }
    }

    suspend fun emitDiscoveredDevice(device: DesktopDiscoveredDevice) {
        mutex.withLock {
            currentDiscoverySession?.emitDevice(device)
        }
    }

    override suspend fun checkAvailability(): DesktopBluetoothAvailability = mutex.withLock {
        currentAvailability
    }

    override fun observeAvailability(): Flow<DesktopBluetoothAvailability> =
        availabilityFlow.asSharedFlow()

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        OperationOutcome.Success(
            BluetoothPlatformCapabilities.unobserved().copy(
                platformType = platformDescriptor.platformType,
                candidateTransports = platformDescriptor.candidateTransports,
            ),
        )

    override suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession> = mutex.withLock {
        if (!currentAvailability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "simulated.discovery.start",
                    detail = "Adapter not usable (status=$currentAvailability)",
                ),
            )
        }
        if (shouldFailDiscoveryStart) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = discoveryStartFailureCategory,
                    operationId = "simulated.discovery.start",
                    detail = "Injected discovery start failure",
                ),
            )
        }
        if (currentDiscoverySession?.isActive == true) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "simulated.discovery.start",
                    detail = "Discovery session is already active",
                ),
            )
        }
        val session = SimulatedDesktopDiscoverySession(onStop = {
            mutex.withLock {
                if (currentDiscoverySession === it) {
                    currentDiscoverySession = null
                }
            }
        })
        currentDiscoverySession = session
        OperationOutcome.Success(session)
    }

    override suspend fun getKnownDevices(): OperationOutcome<List<DesktopDiscoveredDevice>> = mutex.withLock {
        if (!currentAvailability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "simulated.devices.get",
                    detail = "Adapter not usable (status=$currentAvailability)",
                ),
            )
        }
        OperationOutcome.Success(knownDevices.toList())
    }

    override suspend fun openTransportSession(
        deviceIdentifier: String,
        transportKind: TransportKind,
    ): OperationOutcome<DesktopTransportSession> = mutex.withLock {
        if (!currentAvailability.isUsable) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                    operationId = "simulated.transport.open",
                    detail = "Adapter not usable (status=$currentAvailability)",
                ),
            )
        }
        if (shouldFailTransportOpen) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = transportOpenFailureCategory,
                    operationId = "simulated.transport.open",
                    detail = "Injected transport open failure",
                ),
            )
        }
        if (!platformDescriptor.candidateTransports.contains(transportKind)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                    operationId = "simulated.transport.open",
                    detail = "Transport kind $transportKind is not supported on this adapter",
                ),
            )
        }
        if (activeTransportSessions.containsKey(deviceIdentifier)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "simulated.transport.open",
                    detail = "Active transport session already exists for device $deviceIdentifier",
                ),
            )
        }
        val session = SimulatedDesktopTransportSession(
            transportKind = transportKind,
            deviceIdentifier = deviceIdentifier,
            onClose = {
                mutex.withLock {
                    activeTransportSessions.remove(deviceIdentifier)
                }
            },
        )
        activeTransportSessions[deviceIdentifier] = session
        OperationOutcome.Success(session)
    }

    fun isDiscoveryActive(): Boolean = currentDiscoverySession?.isActive == true

    fun activeSessionCount(): Int = activeTransportSessions.size
}

/**
 * Simulated implementation of DesktopDeviceDiscoverySession in test sources.
 */
class SimulatedDesktopDiscoverySession(
    private val onStop: suspend (SimulatedDesktopDiscoverySession) -> Unit,
) : DesktopDeviceDiscoverySession {

    private val mutex = Mutex()
    private var active = true
    private val deviceFlow = MutableSharedFlow<DesktopDiscoveredDevice>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val seenIdentifiers = mutableSetOf<String>()

    override val isActive: Boolean get() = active

    override val observations: Flow<DesktopDiscoveredDevice> = deviceFlow.asSharedFlow()

    suspend fun emitDevice(device: DesktopDiscoveredDevice) {
        mutex.withLock {
            if (!active) return
            seenIdentifiers.add(device.platformIdentifier)
            deviceFlow.emit(device)
        }
    }

    suspend fun cancelDueToAdapterFailure(reason: DesktopBluetoothAvailability) {
        mutex.withLock {
            if (!active) return
            active = false
        }
        onStop(this)
    }

    override suspend fun stop(): OperationOutcome<Unit> {
        mutex.withLock {
            if (!active) {
                return OperationOutcome.Success(Unit)
            }
            active = false
        }
        onStop(this)
        return OperationOutcome.Success(Unit)
    }
}

/**
 * Simulated implementation of DesktopTransportSession in test sources.
 */
class SimulatedDesktopTransportSession(
    override val transportKind: TransportKind,
    override val deviceIdentifier: String,
    private val onClose: suspend () -> Unit,
) : DesktopTransportSession {

    private var connected = true

    override val isConnected: Boolean get() = connected

    override suspend fun close() {
        if (!connected) return
        connected = false
        onClose()
    }
}
