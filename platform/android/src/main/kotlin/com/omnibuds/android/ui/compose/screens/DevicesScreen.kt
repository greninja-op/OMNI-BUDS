package com.omnibuds.android.ui.compose.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.DevicesScreenMode
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.workspace.ProductCatalog
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.CategoryArtwork
import com.omnibuds.android.ui.compose.components.ProductVisual
import com.omnibuds.android.ui.compose.theme.OmniBrand
import com.omnibuds.core.globalstate.ConnectionState

/**
 * Devices dashboard (UI redesign, v1.8): a product library, not a
 * utility list. Each card leads with the product's own illustration,
 * its real name, its category, and a quiet connection indicator —
 * yellow only when the device is actually connected.
 */
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddDevice: () -> Unit = {},
    productOverrideFor: (String) -> String? = { null },
    productPhotoFor: (String) -> String? = { null },
    deviceAliasFor: (String) -> String? = { null },
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 96.dp, bottom = 48.dp),
    ) {
        state.errorBanner?.let { banner ->
            ErrorBanner(
                message = banner,
                onDismiss = { viewModel.dismissError() },
                onRetry = if (state.isRecoverable) { { viewModel.retry() } } else null,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (state.screenMode) {
            DevicesScreenMode.INITIAL_LOADING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OmniBrand.Accent)
                }
            }
            DevicesScreenMode.BLUETOOTH_UNAVAILABLE -> {
                DashboardMessage(
                    title = "Bluetooth isn't available",
                    body = "Bluetooth hardware is not available on this device.",
                )
            }
            DevicesScreenMode.BLUETOOTH_DISABLED -> {
                DashboardMessage(
                    title = "Bluetooth is off",
                    body = "Bluetooth is powered off. Please enable Bluetooth in Android Settings.",
                )
            }
            DevicesScreenMode.PERMISSION_REQUIRED, DevicesScreenMode.PERMISSION_DENIED -> {
                DashboardMessage(
                    title = "Permission needed",
                    body = state.permissionGuidance
                        ?: "Bluetooth permissions are required to detect accessories.",
                )
            }
            DevicesScreenMode.NO_DEVICES_FOUND -> {
                EmptyDashboard(onAddDevice = onAddDevice)
            }
            else -> {
                val allDevices = (state.knownDevices + state.discoveredDevices).distinctBy { it.id }
                Text(
                    text = "Your devices",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (allDevices.size == 1) "1 device" else "${allDevices.size} devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(allDevices, key = { it.id }) { device ->
                        DeviceItemCard(
                            device = device,
                            overrideCatalogId = productOverrideFor(device.id),
                    userPhotoPath = productPhotoFor(device.id),
                    userAlias = deviceAliasFor(device.id),
                            onClick = { onDeviceSelected(device.id) },
                            modifier = Modifier.animateItem(fadeInSpec = tween(300)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMessage(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDashboard(onAddDevice: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            CategoryArtwork(
                category = ProductCatalog.Category.TWS,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No earbuds yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Earbuds you pair with this phone appear here automatically. To add a new pair, start discovery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddDevice,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmniBrand.Accent,
                    contentColor = OmniBrand.OnAccent,
                ),
            ) {
                Text("Add earbuds")
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: AndroidDiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overrideCatalogId: String? = null,
    userPhotoPath: String? = null,
    userAlias: String? = null,
) {
    val product = ProductCatalog.resolve(device.name, overrideCatalogId)
    val connected = device.connectionState is ConnectionState.Connected
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${userAlias ?: device.displayName}, ${connectionLabel(device.connectionState)}"
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProductVisual(
                    product = product,
                    userPhotoPath = userPhotoPath,
                    grayscale = !connected,
                    photoContentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.33f)
                        .padding(6.dp),
                )
                // Connection indicator: a single quiet dot — yellow when
                // connected, hollow grey when not. No shouting pills.
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(
                            if (connected) OmniBrand.Accent else OmniBrand.Disabled,
                        ),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = userAlias ?: device.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = product.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = connectionLabel(device.connectionState),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (connected) {
                            OmniBrand.Accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (connected) {
                        device.batterySummary?.let { battery ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = battery,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Short human label for a connection state (never a raw toString). */
fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Disconnecting -> "Disconnecting"
    else -> "Disconnected"
}
