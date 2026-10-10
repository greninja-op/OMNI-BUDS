package com.omnibuds.desktop.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level destinations in the desktop application.
 */
sealed interface DesktopDestination {
    data object Devices : DesktopDestination
    data class DeviceWorkspace(val deviceIdentifier: String) : DesktopDestination
    data object Diagnostics : DesktopDestination
    data object Settings : DesktopDestination
    data object About : DesktopDestination
}

/**
 * State of desktop navigation stack and selection.
 */
data class NavigationState(
    val currentDestination: DesktopDestination = DesktopDestination.Devices,
    val backStack: List<DesktopDestination> = listOf(DesktopDestination.Devices),
    val selectedDeviceIdentifier: String? = null,
) {
    val canGoBack: Boolean get() = backStack.size > 1
}

/**
 * Deterministic navigation coordinator for the desktop application.
 */
class DesktopNavigationCoordinator(
    initialDestination: DesktopDestination = DesktopDestination.Devices,
    private val maxBackStackDepth: Int = 32,
) {
    private val _state = MutableStateFlow(
        NavigationState(
            currentDestination = initialDestination,
            backStack = listOf(initialDestination),
            selectedDeviceIdentifier = (initialDestination as? DesktopDestination.DeviceWorkspace)?.deviceIdentifier,
        ),
    )
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    fun navigateTo(destination: DesktopDestination) {
        val current = _state.value
        if (current.currentDestination == destination) return

        val newSelectedDevice = when (destination) {
            is DesktopDestination.DeviceWorkspace -> destination.deviceIdentifier
            DesktopDestination.Devices -> current.selectedDeviceIdentifier
            else -> current.selectedDeviceIdentifier
        }

        val updatedBackStack = (current.backStack + destination).takeLast(maxBackStackDepth)
        _state.value = current.copy(
            currentDestination = destination,
            backStack = updatedBackStack,
            selectedDeviceIdentifier = newSelectedDevice,
        )
    }

    fun openDeviceWorkspace(deviceIdentifier: String) {
        navigateTo(DesktopDestination.DeviceWorkspace(deviceIdentifier))
    }

    fun goBack(): Boolean {
        val current = _state.value
        if (!current.canGoBack) return false

        val newBackStack = current.backStack.dropLast(1)
        val previousDestination = newBackStack.last()

        val newSelectedDevice = when (previousDestination) {
            is DesktopDestination.DeviceWorkspace -> previousDestination.deviceIdentifier
            else -> current.selectedDeviceIdentifier
        }

        _state.value = current.copy(
            currentDestination = previousDestination,
            backStack = newBackStack,
            selectedDeviceIdentifier = newSelectedDevice,
        )
        return true
    }

    fun selectDevice(deviceIdentifier: String?) {
        val current = _state.value
        _state.value = current.copy(selectedDeviceIdentifier = deviceIdentifier)
    }

    fun clearWorkspace() {
        val current = _state.value
        val fallback = if (current.currentDestination is DesktopDestination.DeviceWorkspace) {
            DesktopDestination.Devices
        } else {
            current.currentDestination
        }
        _state.value = current.copy(
            currentDestination = fallback,
            selectedDeviceIdentifier = null,
            backStack = current.backStack.filterNot { it is DesktopDestination.DeviceWorkspace }
                .ifEmpty { listOf(DesktopDestination.Devices) },
        )
    }
}
