package com.omnibuds.android.ui.compose.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.omnibuds.android.presentation.navigation.AndroidDestination
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.ui.compose.screens.AboutScreen
import com.omnibuds.android.ui.compose.screens.DevicesScreen
import com.omnibuds.android.ui.compose.screens.DiagnosticsScreen
import com.omnibuds.android.ui.compose.screens.SettingsScreen
import com.omnibuds.android.ui.compose.screens.WorkspaceScreen
import com.omnibuds.android.ui.compose.theme.OmniBudsOutlineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniBudsAppShell(
    shell: AndroidApplicationShell,
    workspaceViewModelProvider: (String) -> DeviceWorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val shellState by shell.shellState.collectAsState()
    val navState by shell.navigationCoordinator.state.collectAsState()

    val currentDest = navState.currentDestination

    if (shellState.useNavigationRail) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = currentDest is AndroidDestination.Devices,
                    onClick = { shell.navigateTo(AndroidDestination.Devices) },
                    label = { Text("Devices") },
                    icon = { Icon(imageVector = OmniBudsOutlineIcons.Headphones, contentDescription = "Devices") },
                )
                NavigationRailItem(
                    selected = currentDest is AndroidDestination.Settings,
                    onClick = { shell.navigateTo(AndroidDestination.Settings) },
                    label = { Text("Settings") },
                    icon = { Icon(imageVector = OmniBudsOutlineIcons.Settings, contentDescription = "Settings") },
                )
                NavigationRailItem(
                    selected = currentDest is AndroidDestination.Diagnostics,
                    onClick = { shell.navigateTo(AndroidDestination.Diagnostics) },
                    label = { Text("Diag") },
                    icon = { Icon(imageVector = OmniBudsOutlineIcons.Diagnostics, contentDescription = "Diagnostics") },
                )
                NavigationRailItem(
                    selected = currentDest is AndroidDestination.About,
                    onClick = { shell.navigateTo(AndroidDestination.About) },
                    label = { Text("About") },
                    icon = { Icon(imageVector = OmniBudsOutlineIcons.About, contentDescription = "About") },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AppScreenContent(
                    destination = currentDest,
                    shell = shell,
                    workspaceViewModelProvider = workspaceViewModelProvider,
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentDest) {
                                is AndroidDestination.Devices -> "OmniBuds"
                                is AndroidDestination.DeviceWorkspace -> "Device Workspace"
                                is AndroidDestination.Settings -> "Settings"
                                is AndroidDestination.Diagnostics -> "Diagnostics"
                                is AndroidDestination.About -> "About"
                            },
                        )
                    },
                    navigationIcon = {
                        if (navState.canNavigateBack) {
                            IconButton(onClick = { shell.handleBackGesture() }) {
                                Icon(imageVector = OmniBudsOutlineIcons.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDest is AndroidDestination.Devices,
                        onClick = { shell.navigateTo(AndroidDestination.Devices) },
                        label = { Text("Devices") },
                        icon = { Icon(imageVector = OmniBudsOutlineIcons.Headphones, contentDescription = "Devices") },
                    )
                    NavigationBarItem(
                        selected = currentDest is AndroidDestination.Settings,
                        onClick = { shell.navigateTo(AndroidDestination.Settings) },
                        label = { Text("Settings") },
                        icon = { Icon(imageVector = OmniBudsOutlineIcons.Settings, contentDescription = "Settings") },
                    )
                    NavigationBarItem(
                        selected = currentDest is AndroidDestination.Diagnostics,
                        onClick = { shell.navigateTo(AndroidDestination.Diagnostics) },
                        label = { Text("Diag") },
                        icon = { Icon(imageVector = OmniBudsOutlineIcons.Diagnostics, contentDescription = "Diagnostics") },
                    )
                    NavigationBarItem(
                        selected = currentDest is AndroidDestination.About,
                        onClick = { shell.navigateTo(AndroidDestination.About) },
                        label = { Text("About") },
                        icon = { Icon(imageVector = OmniBudsOutlineIcons.About, contentDescription = "About") },
                    )
                }
            },
            modifier = modifier,
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AppScreenContent(
                    destination = currentDest,
                    shell = shell,
                    workspaceViewModelProvider = workspaceViewModelProvider,
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
) {
    when (destination) {
        is AndroidDestination.Devices -> {
            DevicesScreen(
                viewModel = shell.devicesViewModel,
                onDeviceSelected = { id -> shell.navigateTo(AndroidDestination.DeviceWorkspace(id)) },
            )
        }
        is AndroidDestination.DeviceWorkspace -> {
            val vm = workspaceViewModelProvider(destination.deviceId)
            WorkspaceScreen(viewModel = vm)
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
