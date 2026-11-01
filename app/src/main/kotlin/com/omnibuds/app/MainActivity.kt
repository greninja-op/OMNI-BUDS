package com.omnibuds.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.android.presentation.integration.AndroidSurfaceCoordinator
import com.omnibuds.android.presentation.navigation.AndroidNavigationCoordinator
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.ui.compose.shell.OmniBudsAppShell
import com.omnibuds.android.ui.compose.theme.OmniBudsTheme
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = GlobalDeviceStateRepository()
        val surfaceCoordinator = AndroidSurfaceCoordinator(repository = repository)
        val devicesViewModel = DevicesViewModel(scope = appScope, globalStateRepository = repository)
        val diagnosticsViewModel = DiagnosticsViewModel()
        val settingsViewModel = SettingsViewModel(scope = appScope)
        val navigationCoordinator = AndroidNavigationCoordinator()
        val shell = AndroidApplicationShell(
            navigationCoordinator = navigationCoordinator,
            devicesViewModel = devicesViewModel,
            diagnosticsViewModel = diagnosticsViewModel,
            settingsViewModel = settingsViewModel,
            surfaceCoordinator = surfaceCoordinator,
        )

        setContent {
            OmniBudsTheme {
                OmniBudsAppShell(
                    shell = shell,
                    workspaceViewModelProvider = { deviceId ->
                        DeviceWorkspaceViewModel(
                            deviceIdentifier = deviceId,
                            scope = appScope,
                            stateRepository = repository,
                        )
                    },
                )
            }
        }
    }
}
