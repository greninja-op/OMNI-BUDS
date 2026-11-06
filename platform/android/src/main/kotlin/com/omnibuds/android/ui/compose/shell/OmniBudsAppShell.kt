package com.omnibuds.android.ui.compose.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.omnibuds.android.presentation.navigation.AndroidDestination
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.android.ui.compose.screens.AboutScreen
import com.omnibuds.android.ui.compose.screens.AddDeviceScreen
import com.omnibuds.android.ui.compose.screens.DevicesScreen
import com.omnibuds.android.ui.compose.screens.DiagnosticsScreen
import com.omnibuds.android.ui.compose.screens.SettingsScreen
import com.omnibuds.android.ui.compose.screens.WorkspaceScreen
import com.omnibuds.core.globalstate.ConnectionState

/**
 * OmniBuds shell — focused controls design.
 *
 * Direct entry: connected earbuds controls are shown upfront without extra taps.
 * Top bar allows quick access to the Add/Scanning screen (+) and Settings (gear).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniBudsAppShell(
    shell: AndroidApplicationShell,
    workspaceViewModelProvider: (String) -> DeviceWorkspaceViewModel,
    modifier: Modifier = Modifier,
    onDisconnectDevice: (String) -> Unit = {},
    onForgetDevice: (String) -> Unit = {},
    onDeviceOpened: (String) -> Unit = {},
    productOverrideFor: (String) -> String? = { null },
    onProductOverrideChange: (String, String?) -> Unit = { _, _ -> },
    productPhotoFor: (String) -> String? = { null },
    onProductPhotoChange: (String, String?) -> Unit = { _, _ -> },
    deviceAliasFor: (String) -> String? = { null },
    onDeviceAliasChange: (String, String?) -> Unit = { _, _ -> },
) {
    val navState by shell.navigationCoordinator.state.collectAsState()
    val currentDest = navState.currentDestination
    val devicesState by shell.devicesViewModel.state.collectAsState()
    val motionEnabled = rememberMotionEnabled()

    // Automatically navigate straight to the connected earbuds controls on launch
    LaunchedEffect(devicesState) {
        val allDevices = (devicesState.knownDevices + devicesState.discoveredDevices).distinctBy { it.id }
        val connectedDevice = allDevices.firstOrNull { it.connectionState is ConnectionState.Connected }
        if (connectedDevice != null) {
            // Always prioritize the live connected earbuds!
            if (currentDest !is AndroidDestination.DeviceWorkspace || (currentDest as AndroidDestination.DeviceWorkspace).deviceId != connectedDevice.id) {
                // If user is not currently in Settings or Scan screen, jump straight to the connected bud
                if (currentDest is AndroidDestination.Devices || currentDest is AndroidDestination.DeviceWorkspace) {
                    shell.selectDevice(connectedDevice.id)
                }
            }
        } else if (currentDest is AndroidDestination.Devices) {
            val fallback = allDevices.firstOrNull()
            if (fallback != null) {
                shell.selectDevice(fallback.id)
            } else {
                shell.navigateTo(AndroidDestination.AddDevice)
            }
        }
    }

    // System back button: only back out from secondary screens (Settings, AddDevice)
    // The main earbud controls page acts as the root home.
    val isRootControlsScreen = currentDest is AndroidDestination.DeviceWorkspace
    BackHandler(enabled = !isRootControlsScreen && navState.canNavigateBack) {
        shell.handleBackGesture()
    }

    // Opening a device page re-checks that device against the system, so
    // the page never sits on a stale connection/battery picture.
    LaunchedEffect(currentDest) {
        if (currentDest is AndroidDestination.DeviceWorkspace) {
            onDeviceOpened(currentDest.deviceId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentDest) {
                            is AndroidDestination.DeviceWorkspace -> "OmniBuds"
                            is AndroidDestination.AddDevice -> "Scan Earbuds"
                            is AndroidDestination.Settings -> "Settings"
                            is AndroidDestination.Diagnostics -> "Diagnostics"
                            is AndroidDestination.About -> "About"
                            is AndroidDestination.Devices -> "OmniBuds"
                        },
                    )
                },
                navigationIcon = {
                    if (currentDest !is AndroidDestination.DeviceWorkspace && navState.canNavigateBack) {
                        IconButton(onClick = { shell.handleBackGesture() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentDest is AndroidDestination.DeviceWorkspace || currentDest is AndroidDestination.Devices) {
                        IconButton(onClick = { shell.navigateTo(AndroidDestination.AddDevice) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Scan Earbuds")
                        }
                        IconButton(onClick = { shell.navigateTo(AndroidDestination.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = currentDest,
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 10 })
                            .togetherWith(fadeOut(tween(110)))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "destination",
            ) { destination ->
                AppScreenContent(
                    destination = destination,
                    shell = shell,
                    workspaceViewModelProvider = workspaceViewModelProvider,
                    onDisconnectDevice = onDisconnectDevice,
                    onForgetDevice = onForgetDevice,
                    productOverrideFor = productOverrideFor,
                    onProductOverrideChange = onProductOverrideChange,
                    productPhotoFor = productPhotoFor,
                    onProductPhotoChange = onProductPhotoChange,
                    deviceAliasFor = deviceAliasFor,
                    onDeviceAliasChange = onDeviceAliasChange,
                )
            }
        }
    }
}

@Composable
private fun AppScreenContent(
    destination: AndroidDestination,
    shell: AndroidApplicationShell,
    workspaceViewModelProvider: (String) -> DeviceWorkspaceViewModel,
    onDisconnectDevice: (String) -> Unit = {},
    onForgetDevice: (String) -> Unit = {},
    onDeviceOpened: (String) -> Unit = {},
    productOverrideFor: (String) -> String? = { null },
    onProductOverrideChange: (String, String?) -> Unit = { _, _ -> },
    productPhotoFor: (String) -> String? = { null },
    onProductPhotoChange: (String, String?) -> Unit = { _, _ -> },
    deviceAliasFor: (String) -> String? = { null },
    onDeviceAliasChange: (String, String?) -> Unit = { _, _ -> },
) {
    when (destination) {
        is AndroidDestination.Devices -> {
            DevicesScreen(
                viewModel = shell.devicesViewModel,
                onDeviceSelected = { id -> shell.navigateTo(AndroidDestination.DeviceWorkspace(id)) },
                onAddDevice = { shell.navigateTo(AndroidDestination.AddDevice) },
                productOverrideFor = productOverrideFor,
                productPhotoFor = productPhotoFor,
                deviceAliasFor = deviceAliasFor,
            )
        }
        is AndroidDestination.AddDevice -> {
            AddDeviceScreen(
                viewModel = shell.devicesViewModel,
                onDeviceSelected = { id -> shell.navigateTo(AndroidDestination.DeviceWorkspace(id)) },
            )
        }
        is AndroidDestination.DeviceWorkspace -> {
            val vm = workspaceViewModelProvider(destination.deviceId)
            WorkspaceScreen(
                viewModel = vm,
                onDisconnect = { onDisconnectDevice(destination.deviceId) },
                onForget = { onForgetDevice(destination.deviceId) },
                productOverrideId = productOverrideFor(destination.deviceId),
                onProductOverrideChange = { catalogId ->
                    onProductOverrideChange(destination.deviceId, catalogId)
                },
                productPhotoPath = productPhotoFor(destination.deviceId),
                onProductPhotoChange = { path ->
                    onProductPhotoChange(destination.deviceId, path)
                },
                deviceAlias = deviceAliasFor(destination.deviceId),
                onDeviceAliasChange = { alias ->
                    onDeviceAliasChange(destination.deviceId, alias)
                },
            )
        }
        is AndroidDestination.Settings -> {
            SettingsScreen(viewModel = shell.settingsViewModel)
        }
        is AndroidDestination.Diagnostics -> {
            DiagnosticsScreen(viewModel = shell.diagnosticsViewModel)
        }
        is AndroidDestination.About -> {
            AboutScreen()
        }
    }
}
