package com.omnibuds.android.ui.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.DevicesScreenMode
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.theme.AndroidTouchTargets
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.StatusBadge

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        state.errorBanner?.let { banner ->
            ErrorBanner(
                message = banner,
                onDismiss = { viewModel.dismissError() },
                onRetry = if (state.isRecoverable) { { viewModel.retry() } } else null,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.isDiscovering) {
                Button(
                    onClick = { viewModel.stopDiscovery() },
                    modifier = Modifier.defaultMinSize(minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
                ) {
                    Text("Stop Scanning")
                }
            } else {
                Button(
                    onClick = { viewModel.startDiscovery() },
                    modifier = Modifier.defaultMinSize(minHeight = AndroidTouchTargets.minTouchTargetDp.dp),
                ) {
                    Text("Scan for Earbuds")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state.screenMode) {
            DevicesScreenMode.INITIAL_LOADING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            DevicesScreenMode.BLUETOOTH_UNAVAILABLE -> {
                Text(
                    text = "Bluetooth hardware is not available on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DevicesScreenMode.BLUETOOTH_DISABLED -> {
                Text(
                    text = "Bluetooth is powered off. Please enable Bluetooth in Android Settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DevicesScreenMode.PERMISSION_REQUIRED, DevicesScreenMode.PERMISSION_DENIED -> {
                Text(
                    text = state.permissionGuidance ?: "Bluetooth permissions are required to detect accessories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DevicesScreenMode.NO_DEVICES_FOUND -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No compatible earbuds or headphones found. Tap 'Scan for Earbuds' to discover nearby devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val allDevices = (state.knownDevices + state.discoveredDevices).distinctBy { it.id }
                    items(allDevices, key = { it.id }) { device ->
                        DeviceItemCard(
                            device = device,
                            onClick = { onDeviceSelected(device.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: AndroidDiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AndroidTouchTargets.minTouchTargetDp.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${device.displayName}, ${device.connectionState}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = device.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.redactedAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                text = device.connectionState.toString(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
