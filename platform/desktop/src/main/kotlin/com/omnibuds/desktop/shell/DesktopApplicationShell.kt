package com.omnibuds.desktop.shell

import com.omnibuds.core.diagnostics.DiagnosticStore
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformLifecycleState
import com.omnibuds.core.platform.PlatformStoragePort
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.desktop.accessibility.DesktopFocusManager
import com.omnibuds.desktop.navigation.DesktopDestination
import com.omnibuds.desktop.navigation.DesktopNavigationCoordinator
import com.omnibuds.desktop.platform.DesktopPlatformLifecycleSource
import com.omnibuds.desktop.presentation.about.AboutScreenState
import com.omnibuds.desktop.presentation.devices.DevicesViewModel
import com.omnibuds.desktop.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.desktop.presentation.settings.SettingsViewModel
import com.omnibuds.desktop.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.desktop.theme.DesktopTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Root presentation state for the entire desktop application shell.
 */
data class ApplicationShellState(
    val theme: DesktopTheme = DesktopTheme(),
    val currentDestination: DesktopDestination = DesktopDestination.Devices,
    val canGoBack: Boolean = false,
    val windowState: WindowState = WindowState(),
    val globalError: String? = null,
    val selectedDeviceIdentifier: String? = null,
    val isRunning: Boolean = true,
)

/**
 * Central orchestrator for the OmniBuds desktop application.
 * Manages window state, navigation, per-screen viewmodels, and theme propagation.
 */
class DesktopApplicationShell(
    val desktopAdapter: DesktopBluetoothAdapter,
    val stateRepository: GlobalDeviceStateRepository,
    val diagnosticStore: DiagnosticStore,
    val storagePort: PlatformStoragePort,
    val lifecycleSource: DesktopPlatformLifecycleSource,
    val platformDescriptor: PlatformDescriptor,
    val scope: CoroutineScope,
) {
    val windowController = DesktopWindowController()
    val navigation = DesktopNavigationCoordinator()
    val focusManager = DesktopFocusManager()

    val devicesViewModel = DevicesViewModel(
        desktopAdapter = desktopAdapter,
        scope = scope,
    )

    val diagnosticsViewModel = DiagnosticsViewModel(
        diagnosticStore = diagnosticStore,
        desktopAdapter = desktopAdapter,
        platformDescriptor = platformDescriptor,
        scope = scope,
    )

    val settingsViewModel = SettingsViewModel(
        storage = storagePort,
        scope = scope,
    )

    val aboutState: AboutScreenState = AboutScreenState(
        platformDescriptor = platformDescriptor,
    )

    private val workspaceViewModels = mutableMapOf<String, DeviceWorkspaceViewModel>()
    private val globalError = MutableStateFlow<String?>(null)

    private val _shellState = MutableStateFlow(
        ApplicationShellState(
            theme = DesktopTheme.resolve(settingsViewModel.state.value.themeMode, settingsViewModel.state.value.density),
            currentDestination = navigation.state.value.currentDestination,
            canGoBack = navigation.state.value.canGoBack,
            windowState = windowController.windowState.value,
            globalError = globalError.value,
            selectedDeviceIdentifier = navigation.state.value.selectedDeviceIdentifier,
            isRunning = windowController.windowState.value.isVisible,
        ),
    )
    val shellState: StateFlow<ApplicationShellState> = _shellState.asStateFlow()

    init {
        observeChildFlows()
    }

    private fun observeChildFlows() {
        scope.launch {
            try {
                settingsViewModel.state.collect { settings ->
                    updateShellState(theme = DesktopTheme.resolve(settings.themeMode, settings.density))
                }
            } catch (_: CancellationException) {}
        }
        scope.launch {
            try {
                navigation.state.collect { navState ->
                    val destination = navState.currentDestination
                    if (destination is DesktopDestination.DeviceWorkspace) {
                        getOrCreateWorkspaceViewModel(destination.deviceIdentifier)
                    }
                    updateShellState(
                        currentDestination = navState.currentDestination,
                        canGoBack = navState.canGoBack,
                        selectedDevice = navState.selectedDeviceIdentifier,
                    )
                }
            } catch (_: CancellationException) {}
        }
        scope.launch {
            try {
                windowController.windowState.collect { winState ->
                    updateShellState(windowState = winState, isRunning = winState.isVisible)
                }
            } catch (_: CancellationException) {}
        }
        scope.launch {
            try {
                globalError.collect { err ->
                    updateShellState(globalError = err)
                }
            } catch (_: CancellationException) {}
        }
    }

    private fun updateShellState(
        theme: DesktopTheme? = null,
        currentDestination: DesktopDestination? = null,
        canGoBack: Boolean? = null,
        windowState: WindowState? = null,
        globalError: String? = this.globalError.value,
        selectedDevice: String? = null,
        isRunning: Boolean? = null,
    ) {
        val current = _shellState.value
        _shellState.value = current.copy(
            theme = theme ?: current.theme,
            currentDestination = currentDestination ?: current.currentDestination,
            canGoBack = canGoBack ?: current.canGoBack,
            windowState = windowState ?: current.windowState,
            globalError = globalError,
            selectedDeviceIdentifier = selectedDevice ?: current.selectedDeviceIdentifier,
            isRunning = isRunning ?: current.isRunning,
        )
    }

    fun navigateTo(destination: DesktopDestination) {
        navigation.navigateTo(destination)
        updateShellState(
            currentDestination = destination,
            canGoBack = navigation.state.value.canGoBack,
            selectedDevice = navigation.state.value.selectedDeviceIdentifier,
        )
    }

    fun goBack(): Boolean {
        val wentBack = navigation.goBack()
        if (wentBack) {
            updateShellState(
                currentDestination = navigation.state.value.currentDestination,
                canGoBack = navigation.state.value.canGoBack,
                selectedDevice = navigation.state.value.selectedDeviceIdentifier,
            )
        }
        return wentBack
    }

    fun resizeWindow(width: Int, height: Int) {
        windowController.resize(width, height)
        updateShellState(windowState = windowController.windowState.value)
    }

    fun getOrCreateWorkspaceViewModel(deviceIdentifier: String): DeviceWorkspaceViewModel =
        synchronized(workspaceViewModels) {
            workspaceViewModels.getOrPut(deviceIdentifier) {
                DeviceWorkspaceViewModel(
                    deviceIdentifier = deviceIdentifier,
                    stateRepository = stateRepository,
                    desktopAdapter = desktopAdapter,
                    scope = scope,
                )
            }
        }

    fun handleGlobalShortcut(key: String, isCtrl: Boolean = false): Boolean {
        if (isCtrl) {
            when (key) {
                "1" -> { navigateTo(DesktopDestination.Devices); return true }
                "2" -> { navigateTo(DesktopDestination.Diagnostics); return true }
                "3" -> { navigateTo(DesktopDestination.Settings); return true }
                "4" -> { navigateTo(DesktopDestination.About); return true }
                "w", "W" -> {
                    if (navigation.state.value.currentDestination is DesktopDestination.DeviceWorkspace) {
                        navigation.clearWorkspace()
                        updateShellState(
                            currentDestination = navigation.state.value.currentDestination,
                            canGoBack = navigation.state.value.canGoBack,
                            selectedDevice = null,
                        )
                        return true
                    }
                }
            }
        }
        if (key == "Escape" && navigation.state.value.canGoBack) {
            goBack()
            return true
        }
        return false
    }

    fun setGlobalError(error: String?) {
        globalError.value = error
        updateShellState(globalError = error)
    }

    fun clearGlobalError() {
        setGlobalError(null)
    }

    suspend fun shutdown() {
        lifecycleSource.updateState(PlatformLifecycleState.TERMINATING)
        devicesViewModel.onCleared()
        synchronized(workspaceViewModels) {
            workspaceViewModels.values.forEach { it.onCleared() }
            workspaceViewModels.clear()
        }
        windowController.close()
        updateShellState(isRunning = false)
    }
}
