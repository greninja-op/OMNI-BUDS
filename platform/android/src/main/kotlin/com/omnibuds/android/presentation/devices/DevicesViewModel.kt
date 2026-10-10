package com.omnibuds.android.presentation.devices

import com.omnibuds.android.compat.BluetoothDecision
import com.omnibuds.android.compat.BluetoothPlatformPolicy
import com.omnibuds.android.compat.BluetoothPlatformState
import com.omnibuds.android.compat.PermissionState
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Presentation controller for the Home and Device Management Screen.
 * Pure Kotlin — zero androidx.compose imports.
 */
class DevicesViewModel(
    private val scope: CoroutineScope,
    private val discoveryProvider: AndroidDiscoveryProvider? = null,
    private val globalStateRepository: GlobalDeviceStateRepository? = null,
    initialState: DevicesScreenState = DevicesScreenState(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<DevicesScreenState> = _state.asStateFlow()

    private var activeDiscoverySession: AndroidDiscoverySession? = null
    private var discoveryJob: Job? = null
    private var repositoryJob: Job? = null

    init {
        startObservingKnownDevices()
    }

    private fun startObservingKnownDevices() {
        val repo = globalStateRepository ?: return
        repositoryJob = scope.launch {
            try {
                repo.allDevices.collect { deviceMap ->
                    mutex.withLock {
                        val known = deviceMap.values.map { gds ->
                            val name = when (val id = gds.identity) {
                                is IdentityState.Identified -> id.modelId ?: id.manufacturerId
                                else -> null
                            }
                            val batterySummary = when (val b = gds.battery) {
                                is BatteryState.Known -> b.levelPercent?.let { "$it%" }
                                else -> null
                            }
                            val isSupported = when (val c = gds.capabilities) {
                                is CapabilityState.Ready -> c.capabilityIds.isNotEmpty()
                                else -> false
                            }
                            AndroidDiscoveredDevice(
                                id = gds.deviceId.value,
                                name = name,
                                address = gds.deviceId.value,
                                connectionState = gds.connection,
                                transportKind = TransportKind.BLE,
                                batterySummary = batterySummary,
                                isSupported = isSupported,
                            )
                        }
                        _state.update { current ->
                            current.copy(knownDevices = known)
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Screen or scope cancelled
            }
        }
    }

    suspend fun updatePlatformState(newState: BluetoothPlatformState) {
        mutex.withLock {
            val decision = BluetoothPlatformPolicy.decide(newState)
            val hadActiveDiscovery = activeDiscoverySession != null

            if (decision is BluetoothDecision.CannotOperate && hadActiveDiscovery) {
                try {
                    activeDiscoverySession?.stop()
                } catch (_: Exception) {
                    // Ignore on teardown
                }
                discoveryJob?.cancel()
                discoveryJob = null
                activeDiscoverySession = null
            }

            val guidance = when (newState.connectPermission) {
                PermissionState.DENIED ->
                    "Nearby devices (BLUETOOTH_CONNECT) permission was denied. Please allow it in Android Settings to manage audio hardware."
                PermissionState.UNAVAILABLE ->
                    "Bluetooth permissions are unavailable on this device configuration."
                PermissionState.RESTRICTED ->
                    "Bluetooth permissions are restricted by device management policy."
                PermissionState.UNKNOWN ->
                    "Bluetooth permission state is currently verifying."
                PermissionState.GRANTED ->
                    if (!newState.adapterPresent) "No Bluetooth hardware detected on this device."
                    else if (!newState.adapterEnabled) "Bluetooth is disabled. Please turn on Bluetooth in Android Quick Settings or Settings."
                    else null
            }

            _state.update { current ->
                current.copy(
                    bluetoothState = newState,
                    isDiscovering = if (decision is BluetoothDecision.CannotOperate) false else current.isDiscovering,
                    permissionGuidance = guidance,
                )
            }
        }
    }

    suspend fun startDiscovery() {
        mutex.withLock {
            val bState = _state.value.bluetoothState
            val decision = BluetoothPlatformPolicy.decide(bState)
            if (decision is BluetoothDecision.CannotOperate) {
                _state.update {
                    it.copy(
                        errorBanner = decision.reason,
                        isRecoverable = false,
                    )
                }
                return
            }

            if (bState.scanPermission == PermissionState.DENIED) {
                _state.update {
                    it.copy(
                        errorBanner = "Nearby devices (BLUETOOTH_SCAN) permission is required to scan for accessories.",
                        isRecoverable = true,
                    )
                }
                return
            }

            val provider = discoveryProvider
            if (provider == null) {
                _state.update {
                    it.copy(
                        errorBanner = "Bluetooth discovery is not available in the current environment.",
                        isRecoverable = false,
                    )
                }
                return
            }

            try {
                _state.update { it.copy(isDiscovering = true, errorBanner = null) }
                val session = provider.startDiscovery()
                activeDiscoverySession = session

                discoveryJob?.cancel()
                discoveryJob = scope.launch {
                    try {
                        session.results.collect { discovered ->
                            mutex.withLock {
                                val existing = _state.value.discoveredDevices.associateBy { it.id }.toMutableMap()
                                for (device in discovered) {
                                    existing[device.id] = device
                                }
                                _state.update { it.copy(discoveredDevices = existing.values.toList()) }
                            }
                        }
                    } catch (_: CancellationException) {
                        // Expected when cancelled or stopped
                    } catch (e: Exception) {
                        mutex.withLock {
                            _state.update {
                                it.copy(
                                    isDiscovering = false,
                                    errorBanner = "Discovery interrupted: ${e.message ?: "stream failure"}",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDiscovering = false,
                        errorBanner = "Failed to start discovery: ${e.message ?: "system error"}",
                    )
                }
            }
        }
    }

    suspend fun stopDiscovery() {
        mutex.withLock {
            try {
                activeDiscoverySession?.stop()
            } catch (_: Exception) {
            }
            discoveryJob?.cancel()
            discoveryJob = null
            activeDiscoverySession = null
            _state.update { it.copy(isDiscovering = false) }
        }
    }

    suspend fun cancelDiscovery() {
        mutex.withLock {
            try {
                activeDiscoverySession?.cancel()
            } catch (_: Exception) {
            }
            discoveryJob?.cancel()
            discoveryJob = null
            activeDiscoverySession = null
            _state.update { it.copy(isDiscovering = false) }
        }
    }

    fun startDiscoveryAsync() {
        scope.launch { startDiscovery() }
    }

    fun stopDiscoveryAsync() {
        scope.launch { stopDiscovery() }
    }

    fun cancelDiscoveryAsync() {
        scope.launch { cancelDiscovery() }
    }

    fun selectDevice(deviceId: String) {
        _state.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun dismissError() {
        _state.update { it.copy(errorBanner = null) }
    }

    fun retry() {
        dismissError()
        startDiscoveryAsync()
    }

    fun addDiscoveredDevice(device: AndroidDiscoveredDevice) {
        _state.update { current ->
            val updated = (current.discoveredDevices.filterNot { it.id == device.id } + device)
            current.copy(discoveredDevices = updated)
        }
    }

    fun removeDiscoveredDevice(deviceId: String) {
        _state.update { current ->
            current.copy(discoveredDevices = current.discoveredDevices.filterNot { it.id == deviceId })
        }
    }

    fun cleanUp() {
        val session = activeDiscoverySession
        activeDiscoverySession = null
        if (session != null) {
            scope.launch {
                try {
                    session.cancel()
                } catch (_: Exception) {
                }
            }
        }
        discoveryJob?.cancel()
        discoveryJob = null
        repositoryJob?.cancel()
        repositoryJob = null
        _state.update { it.copy(isDiscovering = false) }
    }
}
