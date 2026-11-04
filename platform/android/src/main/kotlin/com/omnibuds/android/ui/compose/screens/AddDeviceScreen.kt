package com.omnibuds.android.ui.compose.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.core.globalstate.ConnectionState
import kotlinx.coroutines.delay

private const val SCAN_DURATION_MS = 30_000L

/**
 * Add earbuds — the only place scanning happens.
 *
 * Entering starts a 30-second scan for audio wearables (the provider
 * already filters out speakers, car kits and other Bluetooth gear);
 * leaving the screen stops the scan immediately. Found devices open
 * their details page on tap and stay in the gallery afterwards.
 */
@Composable
fun AddDeviceScreen(
    viewModel: DevicesViewModel,
    onDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val motionEnabled = rememberMotionEnabled()

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
        onDispose { viewModel.stopDiscoveryAsync() }
    }

    val knownIds = state.knownDevices.map { it.id }.toSet()
    val found = state.discoveredDevices.distinctBy { it.id }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            ScanPulse(active = state.isDiscovering, motionEnabled = motionEnabled)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (state.isDiscovering) {
                    "Searching for earbuds nearby"
                } else {
                    "Scan finished"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Put your earbuds in pairing mode, then pick them below. " +
                    "Devices you've already paired show up in the gallery automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (state.isDiscovering) {
                OutlinedButton(onClick = { viewModel.stopDiscoveryAsync() }) {
                    Text("Stop scanning")
                }
            } else {
                Button(onClick = { viewModel.startDiscoveryAsync() }) {
                    Text("Scan again")
                }
            }
            state.errorBanner?.let { banner ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorBanner(
                    message = banner,
                    onDismiss = { viewModel.dismissError() },
                    onRetry = if (state.isRecoverable) {
                        { viewModel.startDiscoveryAsync() }
                    } else {
                        null
                    },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (found.isEmpty()) {
            item {
                Text(
                    text = if (state.isDiscovering) {
                        "Nothing found yet…"
                    } else {
                        "No new earbuds found. Try scanning again with the case open."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(found, key = { it.id }) { device ->
                FoundDeviceRow(
                    device = device,
                    alreadyAdded = device.id in knownIds,
                    onClick = { onDeviceSelected(device.id) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ScanPulse(active: Boolean, motionEnabled: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        if (active && motionEnabled) {
            val transition = rememberInfiniteTransition(label = "scanPulse")
            for (i in 0..2) {
                val scale by transition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2400, delayMillis = i * 700),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "ring$i",
                )
                val alpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2400, delayMillis = i * 700),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "ringAlpha$i",
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(scale)
                        .alpha(alpha),
                ) {}
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = OmniIcons.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun FoundDeviceRow(
    device: AndroidDiscoveredDevice,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = OmniIcons.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = when {
                        device.connectionState is ConnectionState.Connected -> "Connected"
                        alreadyAdded -> "Already in your gallery"
                        device.rssi != null -> "Nearby"
                        else -> "Found"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
