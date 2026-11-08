package com.omnibuds.android.ui.compose.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.omnibuds.android.hardware.pairing.BondingStage
import com.omnibuds.android.presentation.devices.DevicesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCAN_DURATION_MS = 30_000L

/**
 * Add earbuds — Solar System Orbital Radar Bluetooth Discovery UI.
 *
 * Entering starts a 30-second dual-mode hybrid scan for audio wearables; leaving the screen
 * stops discovery immediately. Discovered devices spawn onto concentric solar system orbits
 * based on signal proximity (RSSI).
 *
 * Tapping an unpaired device triggers OS-level bonding ([android.bluetooth.BluetoothDevice.createBond]),
 * displays live glowing tether and status pill telemetry, connects A2DP/HFP audio proxies,
 * and navigates to the device workspace on successful completion.
 */
@Composable
fun AddDeviceScreen(
    viewModel: DevicesViewModel,
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var localSelectedId by remember { mutableStateOf<String?>(null) }

    // Pre-flight hardware checks
    val btManager = context.getSystemService(BluetoothManager::class.java)
    val isBtEnabled = state.bluetoothState.adapterEnabled && (btManager?.adapter?.isEnabled == true)

    val locManager = context.getSystemService(LocationManager::class.java)
    val isLocEnabled = if (Build.VERSION.SDK_INT >= 28) {
        try {
            locManager?.isLocationEnabled == true
        } catch (_: Exception) {
            true
        }
    } else {
        true
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // System adapter state broadcast will automatically refresh state
    }

    val onRequestEnableBluetooth: () -> Unit = {
        try {
            btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                )
            } catch (_: Exception) {}
        }
    }

    val onRequestEnableLocation: () -> Unit = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        if (!state.isDiscovering) viewModel.startDiscoveryAsync()
    }
    LaunchedEffect(state.isDiscovering) {
        if (state.isDiscovering) {
            delay(SCAN_DURATION_MS)
            if (state.isDiscovering) viewModel.stopDiscoveryAsync()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscoveryAsync()
            viewModel.resetBondingStage()
        }
    }

    // Auto-advance to workspace when bonding and audio routing complete
    LaunchedEffect(state.bondingStage) {
        val stage = state.bondingStage
        if (stage is BondingStage.Connected) {
            delay(500L)
            onDeviceSelected(stage.address)
        }
    }

    val found = state.discoveredDevices.distinctBy { it.id }

    OrbitalScannerView(
        discoveredDevices = found,
        isScanning = state.isDiscovering,
        onStartScan = { viewModel.startDiscoveryAsync() },
        onStopScan = { viewModel.stopDiscoveryAsync() },
        onSelectDevice = { device ->
            localSelectedId = device.id
            viewModel.pairDevice(device) { success ->
                if (success) {
                    scope.launch {
                        delay(400L)
                        onDeviceSelected(device.id)
                    }
                }
            }
        },
        selectedDeviceId = localSelectedId ?: state.selectedDeviceId,
        bondingStage = state.bondingStage,
        onRetryBonding = { device ->
            viewModel.retryBonding(device)
        },
        onResetBonding = {
            viewModel.resetBondingStage()
        },
        bluetoothEnabled = isBtEnabled,
        locationEnabled = isLocEnabled,
        onRequestEnableBluetooth = onRequestEnableBluetooth,
        onRequestEnableLocation = onRequestEnableLocation,
        errorBanner = state.errorBanner,
        onDismissError = { viewModel.dismissError() },
        onRetry = if (state.isRecoverable) {
            { viewModel.startDiscoveryAsync() }
        } else {
            null
        },
        modifier = modifier,
    )
}
