package com.omnibuds.android.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Top-level destinations in the OmniBuds Android application.
 */
sealed interface AndroidDestination {
    val route: String

    data object Devices : AndroidDestination {
        override val route: String = "devices"
    }

    data class DeviceWorkspace(val deviceId: String) : AndroidDestination {
        override val route: String = "workspace/$deviceId"
    }

    data object Settings : AndroidDestination {
        override val route: String = "settings"
    }

    data object Diagnostics : AndroidDestination {
        override val route: String = "diagnostics"
    }

    data object About : AndroidDestination {
        override val route: String = "about"
    }

    companion object {
        fun fromRoute(route: String): AndroidDestination = when {
            route == "devices" -> Devices
            route.startsWith("workspace/") -> {
                val id = route.removePrefix("workspace/")
                DeviceWorkspace(id)
            }
            route == "settings" -> Settings
            route == "diagnostics" -> Diagnostics
            route == "about" -> About
            else -> Devices
        }
    }
}

/**
 * Immutable navigation state.
 */
data class AndroidNavigationState(
    val currentDestination: AndroidDestination = AndroidDestination.Devices,
    val backStack: List<AndroidDestination> = listOf(AndroidDestination.Devices),
) {
    val canNavigateBack: Boolean get() = backStack.size > 1
}

/**
 * Presentation controller for Android navigation, supporting system back gestures,
 * deterministic back stacks, and process recreation state saving/restoration.
 */
class AndroidNavigationCoordinator(
    initialDestination: AndroidDestination = AndroidDestination.Devices,
) {
    private val _state = MutableStateFlow(
        AndroidNavigationState(
            currentDestination = initialDestination,
            backStack = listOf(initialDestination),
        ),
    )
    val state: StateFlow<AndroidNavigationState> = _state.asStateFlow()

    fun navigateTo(destination: AndroidDestination) {
        _state.update { current ->
            if (current.currentDestination == destination) {
                current
            } else {
                current.copy(
                    currentDestination = destination,
                    backStack = current.backStack + destination,
                )
            }
        }
    }

    fun selectDevice(deviceId: String) {
        navigateTo(AndroidDestination.DeviceWorkspace(deviceId))
    }

    /**
     * Handles back navigation (e.g. system back gesture or toolbar back button).
     * Returns true if back navigation was handled, false if at the root destination.
     */
    fun navigateBack(): Boolean {
        var handled = false
        _state.update { current ->
            if (current.backStack.size > 1) {
                val newStack = current.backStack.dropLast(1)
                handled = true
                current.copy(
                    currentDestination = newStack.last(),
                    backStack = newStack,
                )
            } else {
                current
            }
        }
        return handled
    }

    fun popToRoot() {
        _state.update { current ->
            val root = current.backStack.firstOrNull() ?: AndroidDestination.Devices
            current.copy(
                currentDestination = root,
                backStack = listOf(root),
            )
        }
    }

    fun saveState(): List<String> {
        return _state.value.backStack.map { it.route }
    }

    fun restoreState(savedRoutes: List<String>) {
        if (savedRoutes.isEmpty()) return
        val stack = savedRoutes.map { AndroidDestination.fromRoute(it) }
        _state.value = AndroidNavigationState(
            currentDestination = stack.last(),
            backStack = stack,
        )
    }
}
