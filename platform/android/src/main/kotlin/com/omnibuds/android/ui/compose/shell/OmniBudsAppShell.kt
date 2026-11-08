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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibuds.android.presentation.navigation.AndroidDestination
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore
import com.omnibuds.android.hardware.engine.BluetoothAdapterStatus
import com.omnibuds.android.R
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.android.ui.compose.screens.AboutScreen
import com.omnibuds.android.ui.compose.screens.AddDeviceScreen
import com.omnibuds.android.ui.compose.screens.DiagnosticsScreen
import com.omnibuds.android.ui.compose.screens.SettingsScreen
import com.omnibuds.android.ui.compose.screens.WorkspaceScreen
import com.omnibuds.android.ui.compose.theme.OmniBrand
import com.omnibuds.core.globalstate.ConnectionState

/**
 * OmniBuds shell — modern floating capsule header with edge-to-edge black gradient fade.
 *
 * Direct entry: connected earbuds controls are shown upfront without extra taps.
 * Top section is transparent with floating capsule buttons and a vertical black gradient fade.
 * Scrolled content glides smoothly underneath the capsule controls and dissolves into pure black.
 */
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
    val context = LocalContext.current
    val btStatus by BluetoothAdapterStateStore.status.collectAsState()
    val allDevices = (devicesState.knownDevices + devicesState.discoveredDevices).distinctBy { it.id }
    val connectedDevice = allDevices.firstOrNull { it.connectionState is ConnectionState.Connected }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Content Layer (scrolls edge-to-edge behind the floating capsules & gradients)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
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

        // Top Gradient Black Fade (smoothly dissolves scrolled-up content)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.40f to Color.Black.copy(alpha = 0.92f),
                        0.75f to Color.Black.copy(alpha = 0.45f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )

        // Top Floating Navigation Bar + Live Bluetooth Adapter Status Pill & Prompt
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Navigation Bar (Scanning leftmost, Settings rightmost, Logo/Title strictly centered)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                // Leftmost Action: Back button (if inside subscreen) or Scanning button (on root screen)
                if (currentDest !is AndroidDestination.DeviceWorkspace && navState.canNavigateBack) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF202126).copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, Color(0xFF32343A)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.CenterStart),
                    ) {
                        IconButton(onClick = { shell.handleBackGesture() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = OmniBrand.TextPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF202126).copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, Color(0xFF32343A)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.CenterStart),
                    ) {
                        IconButton(onClick = { shell.navigateTo(AndroidDestination.AddDevice) }) {
                            Icon(
                                imageVector = OmniIcons.Radar,
                                contentDescription = "Scan Earbuds",
                                tint = OmniBrand.Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                // Center: Straight down the middle - OmniBuds Logo and Title
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF202126).copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color(0xFF32343A)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .height(42.dp)
                        .align(Alignment.Center),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "OmniBuds Logo",
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentDest) {
                                is AndroidDestination.DeviceWorkspace -> "OmniBuds"
                                is AndroidDestination.AddDevice -> "Scan Earbuds"
                                is AndroidDestination.Settings -> "Settings"
                                is AndroidDestination.Diagnostics -> "Diagnostics"
                                is AndroidDestination.About -> "About"
                                is AndroidDestination.Devices -> "OmniBuds"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = OmniBrand.TextPrimary,
                        )
                    }
                }

                // Rightmost Action: Settings Gear Icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF202126).copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color(0xFF32343A)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.CenterEnd),
                ) {
                    IconButton(onClick = {
                        if (currentDest is AndroidDestination.Settings) {
                            shell.handleBackGesture()
                        } else {
                            shell.navigateTo(AndroidDestination.Settings)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = if (currentDest is AndroidDestination.Settings) OmniBrand.Accent else OmniBrand.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Live Bluetooth Adapter Status Pill (Always visible)
            BluetoothAdapterStatusPill(
                status = btStatus,
                connectedDeviceName = connectedDevice?.let { it.name ?: "realme Buds T300" },
                onClick = {
                    if (btStatus == BluetoothAdapterStatus.DISABLED) {
                        try {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    }
                },
            )

            // Instant Launch & Mid-Session "Bluetooth is Off" Warning Banner
            if (btStatus == BluetoothAdapterStatus.DISABLED) {
                Spacer(modifier = Modifier.height(8.dp))
                BluetoothDisabledPromptCard(
                    onEnableClick = {
                        try {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    },
                )
            }
        }

        // Bottom Gradient Fade (smoothly dissolves content above the navigation buttons)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.40f to Color.Black.copy(alpha = 0.45f),
                        0.75f to Color.Black.copy(alpha = 0.85f),
                        1.0f to Color.Black,
                    ),
                ),
        )
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
            // DevicesScreen is retired: direct user directly to AddDevice scanner
            AddDeviceScreen(
                viewModel = shell.devicesViewModel,
                onDeviceSelected = { id -> shell.navigateTo(AndroidDestination.DeviceWorkspace(id)) },
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

@Composable
private fun BluetoothAdapterStatusPill(
    status: BluetoothAdapterStatus,
    connectedDeviceName: String?,
    onClick: () -> Unit,
) {
    val dotColor = when (status) {
        BluetoothAdapterStatus.ENABLED -> if (connectedDeviceName != null) OmniBrand.Accent else Color(0xFF36D66F)
        BluetoothAdapterStatus.TURNING_ON -> Color(0xFFFFD54F)
        BluetoothAdapterStatus.TURNING_OFF -> Color(0xFFFFA726)
        BluetoothAdapterStatus.DISABLED -> Color(0xFFFF5252)
    }

    val statusText = when (status) {
        BluetoothAdapterStatus.ENABLED -> {
            if (connectedDeviceName != null) "Bluetooth Active • Connected to $connectedDeviceName"
            else "Bluetooth Enabled • Ready to Connect"
        }
        BluetoothAdapterStatus.TURNING_ON -> "Turning Bluetooth On..."
        BluetoothAdapterStatus.TURNING_OFF -> "Turning Bluetooth Off..."
        BluetoothAdapterStatus.DISABLED -> "Bluetooth Disabled • Tap to Turn On"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF191A20).copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color(0xFF2C2E36)),
        shadowElevation = 3.dp,
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                ),
                color = if (status == BluetoothAdapterStatus.DISABLED) Color(0xFFFF6E6E) else Color(0xFFD1D5DB),
            )
        }
    }
}

@Composable
private fun BluetoothDisabledPromptCard(
    onEnableClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1B1C22),
        border = BorderStroke(1.dp, OmniBrand.Accent.copy(alpha = 0.5f)),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Bluetooth is Turned Off",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Please enable Bluetooth to connect and control your earbuds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0A5B1),
                )
            }
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmniBrand.Accent,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = "Turn On",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
