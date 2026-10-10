package com.omnibuds.android.presentation.shell

import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.android.presentation.integration.AndroidSurfaceCoordinator
import com.omnibuds.android.presentation.navigation.AndroidDestination
import com.omnibuds.android.presentation.navigation.AndroidNavigationCoordinator
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.theme.AndroidThemeMode
import com.omnibuds.android.presentation.theme.AndroidWindowSizeClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Presentation state of the top-level Android Application Shell.
 */
data class AndroidShellState(
    val currentDestination: AndroidDestination = AndroidDestination.Devices,
    val windowSizeClass: AndroidWindowSizeClass = AndroidWindowSizeClass.COMPACT,
    val themeMode: AndroidThemeMode = AndroidThemeMode.SYSTEM,
    val isHighContrast: Boolean = false,
    val isOnline: Boolean = true,
) {
    /** Whether the screen should display a Navigation Rail (tablets/landscape) vs Bottom Navigation Bar (phones). */
    val useNavigationRail: Boolean get() = windowSizeClass != AndroidWindowSizeClass.COMPACT
}

/**
 * Top-level application shell coordinator managing responsive layouts, navigation,
 * system back handling, and process restoration.
 */
class AndroidApplicationShell(
    val navigationCoordinator: AndroidNavigationCoordinator = AndroidNavigationCoordinator(),
    val devicesViewModel: DevicesViewModel,
    val diagnosticsViewModel: DiagnosticsViewModel = DiagnosticsViewModel(),
    val settingsViewModel: SettingsViewModel,
    val surfaceCoordinator: AndroidSurfaceCoordinator,
    initialWindowWidthDp: Int = 360,
) {
    private val _shellState = MutableStateFlow(
        AndroidShellState(
            currentDestination = navigationCoordinator.state.value.currentDestination,
            windowSizeClass = AndroidWindowSizeClass.fromWidthDp(initialWindowWidthDp),
            themeMode = settingsViewModel.state.value.themeMode,
            isHighContrast = settingsViewModel.state.value.isHighContrast,
        ),
    )
    val shellState: StateFlow<AndroidShellState> = _shellState.asStateFlow()

    fun updateWindowWidth(widthDp: Int) {
        val newClass = AndroidWindowSizeClass.fromWidthDp(widthDp)
        _shellState.update { it.copy(windowSizeClass = newClass) }
    }

    fun navigateTo(destination: AndroidDestination) {
        navigationCoordinator.navigateTo(destination)
        _shellState.update { it.copy(currentDestination = destination) }
    }

    fun handleBackGesture(): Boolean {
        val handled = navigationCoordinator.navigateBack()
        if (handled) {
            _shellState.update { it.copy(currentDestination = navigationCoordinator.state.value.currentDestination) }
        }
        return handled
    }

    /**
     * Preserves state during Android configuration changes or process death.
     */
    fun saveInstanceState(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["back_stack"] = navigationCoordinator.saveState()
        devicesViewModel.state.value.selectedDeviceId?.let { map["selected_device_id"] = it }
        map["theme_mode"] = settingsViewModel.state.value.themeMode.name
        return map
    }

    /**
     * Restores state after process recreation.
     * Note: Never fabricates connected state.
     */
    fun restoreInstanceState(bundle: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val backStack = bundle["back_stack"] as? List<String>
        if (!backStack.isNullOrEmpty()) {
            navigationCoordinator.restoreState(backStack)
            _shellState.update { it.copy(currentDestination = navigationCoordinator.state.value.currentDestination) }
        }
        val selectedId = bundle["selected_device_id"] as? String
        if (selectedId != null) {
            devicesViewModel.selectDevice(selectedId)
        }
    }
}
