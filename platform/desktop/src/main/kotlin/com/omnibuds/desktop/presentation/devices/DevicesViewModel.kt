package com.omnibuds.desktop.presentation.devices

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability
import com.omnibuds.core.platform.desktop.DesktopDeviceDiscoverySession
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
 * Presentation controller for the Devices screen.
 * Reactively integrates with the DesktopBluetoothAdapter and manages discovery sessions safely.
 */
class DevicesViewModel(
    private val desktopAdapter: DesktopBluetoothAdapter,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(DevicesScreenState(isLoading = true))
    val state: StateFlow<DevicesScreenState> = _state.asStateFlow()

    private var activeDiscoverySession: DesktopDeviceDiscoverySession? = null
    private var discoveryObservationJob: Job? = null
    private var availabilityObserverJob: Job? = null

    init {
        startObservingAvailability()
        loadInitialState()
    }

    private fun startObservingAvailability() {
        availabilityObserverJob = scope.launch {
            try {
                desktopAdapter.observeAvailability().collect { newAvailability ->
                    onAvailabilityChanged(newAvailability)
                }
            } catch (_: CancellationException) {
                // Expected when scope cancelled
            }
        }
    }

    private fun loadInitialState() {
        scope.launch {
            val availability = desktopAdapter.checkAvailability()
            onAvailabilityChanged(availability)
            refreshKnownDevices()
            _state.update { it.copy(isLoading = false) }
        }
    }

    suspend fun onAvailabilityChanged(newAvailability: DesktopBluetoothAvailability) {
        mutex.withLock {
            val hadActiveDiscovery = activeDiscoverySession != null
            if (!newAvailability.isUsable && hadActiveDiscovery) {
                try {
                    activeDiscoverySession?.stop()
                } catch (_: Exception) {
                    // Ignore error on teardown
                }
                discoveryObservationJob?.cancel()
                discoveryObservationJob = null
                activeDiscoverySession = null
            }

            val guidance = when (newAvailability) {
                DesktopBluetoothAvailability.PERMISSION_REQUIRED ->
                    "Bluetooth permission is required by the host operating system. Please grant permission in System Settings."
                DesktopBluetoothAvailability.PERMISSION_DENIED ->
                    "Bluetooth permission was denied. Please re-enable Bluetooth permission for OmniBuds."
                DesktopBluetoothAvailability.DISABLED ->
                    "Bluetooth is powered off or disabled on this host. Please turn on Bluetooth in System Settings."
                DesktopBluetoothAvailability.INITIALIZING ->
                    "Bluetooth subsystem is currently initializing. Please wait."
                DesktopBluetoothAvailability.UNSUPPORTED ->
                    "Bluetooth hardware or protocol stack is not supported on this host operating system."
                DesktopBluetoothAvailability.UNAVAILABLE ->
                    "Bluetooth subsystem is currently unavailable. Check daemon or driver status."
                else -> if (!newAvailability.isUsable) "Bluetooth adapter is currently unavailable." else null
            }

            _state.update { current ->
                current.copy(
                    availability = newAvailability,
                    isDiscovering = if (!newAvailability.isUsable) false else current.isDiscovering,
                    errorBanner = if (!newAvailability.isUsable && current.errorBanner == null) {
                        guidance
                    } else {
                        current.errorBanner
                    },
                )
            }
        }
    }

    suspend fun startDiscovery() {
        mutex.withLock {
            val current = _state.value
            if (!current.availability.isUsable) {
                _state.update {
                    it.copy(
                        errorBanner = it.permissionGuidance ?: "Bluetooth adapter is not available for discovery.",
                        isRecoverableError = it.availability.isAuthorizationIssue || it.availability == DesktopBluetoothAvailability.DISABLED,
                    )
                }
                return
            }
            if (current.isDiscovering || activeDiscoverySession != null) {
                return
            }

            _state.update { it.copy(isDiscovering = true, errorBanner = null) }

            when (val outcome = desktopAdapter.startDiscovery()) {
                is OperationOutcome.Success -> {
                    val session = outcome.value
                    activeDiscoverySession = session
                    discoveryObservationJob = scope.launch {
                        try {
                            session.observations.collect { observedDevice ->
                                handleDiscoveredDevice(observedDevice)
                            }
                        } catch (_: CancellationException) {
                            // Expected on stop
                        }
                    }
                }
                is OperationOutcome.Failure -> {
                    _state.update {
                        it.copy(
                            isDiscovering = false,
                            errorBanner = "Discovery failed: ${outcome.error.detail ?: outcome.error.category.name}",
                            isRecoverableError = true,
                        )
                    }
                }
                is OperationOutcome.Cancelled -> {
                    _state.update {
                        it.copy(isDiscovering = false)
                    }
                }
            }
        }
    }

    fun handleDiscoveredDevice(device: com.omnibuds.core.platform.desktop.DesktopDiscoveredDevice) {
        val item = DiscoveredDeviceItem.fromDomain(device)
        _state.update { current ->
            val existing = current.discoveredDevices.toMutableList()
            val index = existing.indexOfFirst { it.platformIdentifier == item.platformIdentifier }
            if (index >= 0) {
                existing[index] = item
            } else {
                existing.add(item)
            }
            current.copy(discoveredDevices = existing)
        }
    }

    suspend fun stopDiscovery() {
        mutex.withLock {
            val session = activeDiscoverySession ?: return
            try {
                session.stop()
            } catch (_: Exception) {
                // Safe cleanup
            }
            discoveryObservationJob?.cancel()
            discoveryObservationJob = null
            activeDiscoverySession = null
            _state.update { it.copy(isDiscovering = false) }
        }
    }

    suspend fun refreshKnownDevices() {
        when (val outcome = desktopAdapter.getKnownDevices()) {
            is OperationOutcome.Success -> {
                val items = outcome.value.map { DiscoveredDeviceItem.fromDomain(it) }
                _state.update { it.copy(knownDevices = items) }
            }
            is OperationOutcome.Failure, is OperationOutcome.Cancelled -> {
                // Keep existing known devices, don't wipe on transient error
            }
        }
    }

    fun selectDevice(deviceIdentifier: String) {
        _state.update { it.copy(selectedDeviceIdentifier = deviceIdentifier) }
    }

    fun clearError() {
        _state.update { it.copy(errorBanner = null, isRecoverableError = false) }
    }

    fun onCleared() {
        discoveryObservationJob?.cancel()
        availabilityObserverJob?.cancel()
        val session = activeDiscoverySession
        activeDiscoverySession = null
        if (session != null) {
            scope.launch {
                try {
                    session.stop()
                } catch (_: Exception) {
                    // best effort
                }
            }
        }
    }
}
