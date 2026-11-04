package com.omnibuds.android.ui.compose.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.presentation.workspace.HardwareControlModel
import com.omnibuds.android.presentation.workspace.KnownDeviceFeatures
import com.omnibuds.android.presentation.workspace.ProductCatalog
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.components.ProductVisual
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.android.ui.compose.theme.OmniBrand
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.presentation.battery.BatteryComponentKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Device details — Iteration 2.
 *
 * Information architecture: product identity (genuine photograph where
 * one legitimately exists, the user's own photo, or honest category
 * artwork) → battery (per-component when a source reports components,
 * aggregate-only otherwise, last-known labelled when disconnected) →
 * noise control (verified modes only; interactive only when the
 * capability engine has established a real control link) → device
 * features (known features shown as clearly labelled, non-interactive
 * capabilities until linked) → device management with confirmations.
 *
 * There is deliberately NO media-player card: OmniBuds manages
 * hardware, and it stays out of the media path.
 */
@Composable
fun WorkspaceScreen(
    viewModel: DeviceWorkspaceViewModel,
    onDisconnect: () -> Unit = {},
    onForget: () -> Unit = {},
    modifier: Modifier = Modifier,
    productOverrideId: String? = null,
    onProductOverrideChange: (String?) -> Unit = {},
    productPhotoPath: String? = null,
    onProductPhotoChange: (String?) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showProductPicker by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val product = ProductCatalog.resolve(state.overview?.displayName, productOverrideId)
    val connected = state.overview?.connectionState is ConnectionState.Connected

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    copyDevicePhoto(context, state.deviceIdentifier, uri)
                }
                if (path != null) onProductPhotoChange(path)
                showProductPicker = false
            }
        }
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            state.errorBanner?.let { banner ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorBanner(
                    message = banner,
                    onDismiss = { viewModel.dismissError() },
                    onRetry = if (state.isRecoverableError) {
                        { viewModel.retryFailedOperations() }
                    } else {
                        null
                    },
                )
            }
        }

        // -- Product identity ------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(16.dp))
            val overview = state.overview
            var heroShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { heroShown = true }
            val motionEnabled = rememberMotionEnabled()
            val heroProgress by animateFloatAsState(
                targetValue = if (heroShown) 1f else 0f,
                animationSpec = if (motionEnabled) tween(420) else snap(),
                label = "heroEntrance",
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = heroProgress
                        scaleX = 0.97f + 0.03f * heroProgress
                        scaleY = 0.97f + 0.03f * heroProgress
                    },
            ) {
                ProductVisual(
                    product = product,
                    userPhotoPath = productPhotoPath,
                    grayscale = !connected,
                    photoContentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp),
                )
            }
            if (productPhotoPath == null && product.imageAttribution != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = product.imageAttribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = overview?.displayName ?: "Device",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = product.category.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (connected) OmniBrand.Accent else OmniBrand.Disabled),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = when (overview?.connectionState) {
                        is ConnectionState.Connected -> "Active"
                        is ConnectionState.Connecting -> "Connecting"
                        is ConnectionState.Disconnecting -> "Disconnecting"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showProductPicker = true }) {
                Text(
                    text = "Wrong product shown? Fix the match",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // -- Battery ----------------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("Battery")
            Spacer(modifier = Modifier.height(12.dp))
            BatterySection(
                battery = state.battery,
                category = product.category,
                deviceName = state.overview?.displayName,
            )
        }

        // -- Noise control ------------------------------------------------------
        val ancControl = state.controls.firstOrNull { it.featureId == "anc_mode" }
        val knownFeatures = product.features
        val showAnc = ancControl != null &&
            (knownFeatures?.anc == true || ancControl.isActionable || ancControl.isReadOnly)
        if (showAnc && ancControl != null) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel("Noise control")
                Spacer(modifier = Modifier.height(12.dp))
                val modes = buildList {
                    if (knownFeatures?.anc == true) add("ANC")
                    add("OFF")
                    if (knownFeatures?.transparency == true) add("TRANSPARENCY")
                }.takeIf { it.size > 1 } ?: ancControl.availableModes
                NoiseControlSegmented(
                    modes = modes,
                    selectedMode = ancControl.currentValue,
                    interactive = ancControl.isActionable,
                    onSelect = { mode -> viewModel.submitFeatureControl("anc_mode", mode) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        !connected -> "Connect your earbuds first."
                        !ancControl.isActionable ->
                            "View only — control for this model is not linked in OmniBuds."
                        else -> "Changes apply to the earbuds."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // -- Device features (known capabilities, honestly labelled) ------------
        val featureRows = state.controls.filter { control ->
            control.featureId != "anc_mode" &&
                control.featureId != "transparency_mode" &&
                knownFeatures != null &&
                KnownDeviceFeatures.has(knownFeatures, control.featureId)
        }
        val codecRow = state.audio?.takeIf {
            it.isCodecObservable && it.activeCodecName != null
        }
        if (featureRows.isNotEmpty() || codecRow != null) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel("Device features")
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        featureRows.forEachIndexed { index, control ->
                            if (index > 0) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            FeatureInfoRow(control = control)
                        }
                        if (codecRow != null) {
                            if (featureRows.isNotEmpty()) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    imageVector = OmniIcons.SoundWaves,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text("Sound", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "Active codec: ${codecRow.activeCodecName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (featureRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This model has these features. OmniBuds cannot control them yet — no verified control link exists for it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // -- Limitations ---------------------------------------------------------
        if (state.limitations.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                state.limitations.forEach { limitation ->
                    Text(
                        text = limitation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // -- Device management ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmDisconnect = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Disconnect") }
                OutlinedButton(
                    onClick = { confirmForget = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Forget") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // -- Dialogs ------------------------------------------------------------------
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect ${state.overview?.displayName ?: "device"}?") },
            text = {
                Text(
                    "The earbuds will disconnect from this phone. You can reconnect " +
                        "from Android Bluetooth settings.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        onDisconnect()
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ${state.overview?.displayName ?: "device"}?") },
            text = {
                Text(
                    "Android will unpair this device. Your product match and any " +
                        "photo you added for it are removed too.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        onForget()
                    },
                ) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }

    if (showProductPicker) {
        ProductPickerDialog(
            product = product,
            hasUserPhoto = productPhotoPath != null,
            hasOverride = productOverrideId != null,
            onSelectEntry = { entryId ->
                onProductOverrideChange(entryId)
                showProductPicker = false
            },
            onSelectGeneric = {
                onProductOverrideChange(ProductCatalog.GENERIC_OVERRIDE_ID)
                showProductPicker = false
            },
            onUseAutomatic = {
                onProductOverrideChange(null)
                showProductPicker = false
            },
            onPickPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemovePhoto = {
                val path = productPhotoPath
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (path != null) runCatching { File(path).delete() }
                    }
                    onProductPhotoChange(null)
                    showProductPicker = false
                }
            },
            onDismiss = { showProductPicker = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Copies a picked image into app-private storage; returns its path. */
private fun copyDevicePhoto(context: Context, deviceId: String, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "product_photos").apply { mkdirs() }
        val safeId = deviceId.replace(Regex("[^A-Za-z0-9]"), "_")
        val target = File(dir, "$safeId.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    } catch (e: Exception) {
        null
    }
}

// -- Battery ----------------------------------------------------------------------

@Composable
private fun BatterySection(
    battery: com.omnibuds.android.presentation.battery.BatteryPresentationModel?,
    category: ProductCatalog.Category,
    deviceName: String?,
) {
    if (battery == null || !battery.hasAnyData) {
        Text(
            text = "Battery levels appear here when a connected device reports them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val components = battery.components.filter { it.kind != BatteryComponentKind.DEVICE }
    val lastKnown = battery.isLastKnown
    when {
        components.isNotEmpty() && category == ProductCatalog.Category.TWS -> {
            // Preferred TWS layout: LEFT | CASE | RIGHT. A component the
            // device never reported renders as an explicit unavailable
            // slot — never as a copied or guessed percentage.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(
                    BatteryComponentKind.LEFT,
                    BatteryComponentKind.CASE,
                    BatteryComponentKind.RIGHT,
                ).forEach { kind ->
                    val reading = components.firstOrNull { it.kind == kind }
                    if (reading?.levelPercent != null) {
                        BatteryRing(
                            glyph = glyphFor(kind),
                            label = labelFor(kind),
                            percent = reading.levelPercent,
                            charging = reading.isCharging == true,
                            lastKnown = lastKnown,
                        )
                    } else {
                        UnavailableBatterySlot(kind = kind)
                    }
                }
            }
            if (lastKnown) LastKnownCaption()
        }
        components.isNotEmpty() -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                components.filter { it.levelPercent != null }.forEach { reading ->
                    BatteryRing(
                        glyph = glyphFor(reading.kind),
                        label = labelFor(reading.kind),
                        percent = reading.levelPercent!!,
                        charging = reading.isCharging == true,
                        lastKnown = lastKnown,
                    )
                }
            }
            if (lastKnown) LastKnownCaption()
        }
        battery.overallPercent != null -> {
            // Aggregate-only source: one honest ring. The aggregate is
            // never split into invented components.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryRing(
                    glyph = when (category) {
                        ProductCatalog.Category.TWS -> OmniIcons.BudsPair
                        ProductCatalog.Category.NECKBAND -> OmniIcons.Neckband
                        else -> OmniIcons.Headphones
                    },
                    label = if (category == ProductCatalog.Category.TWS) "Earbuds" else "Battery",
                    percent = battery.overallPercent,
                    charging = battery.isCharging == true,
                    lastKnown = lastKnown,
                )
                when {
                    lastKnown -> LastKnownCaption()
                    battery.isStale -> Text(
                        text = "This reading may be out of date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastKnownCaption() {
    Text(
        text = "Last known — the device is disconnected.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

private fun glyphFor(kind: BatteryComponentKind): ImageVector = when (kind) {
    BatteryComponentKind.LEFT -> OmniIcons.BudLeft
    BatteryComponentKind.RIGHT -> OmniIcons.BudRight
    BatteryComponentKind.CASE -> OmniIcons.Case
    BatteryComponentKind.DEVICE -> OmniIcons.BudsPair
}

private fun labelFor(kind: BatteryComponentKind): String = when (kind) {
    BatteryComponentKind.LEFT -> "Left"
    BatteryComponentKind.RIGHT -> "Right"
    BatteryComponentKind.CASE -> "Case"
    BatteryComponentKind.DEVICE -> "Battery"
}

/** Normal = green, low = amber, critical = red, last-known = neutral grey. */
private fun batteryRingColor(percent: Int, lastKnown: Boolean): Color = when {
    lastKnown -> OmniBrand.Disabled
    percent <= 10 -> OmniBrand.Error
    percent <= 20 -> OmniBrand.Warning
    else -> OmniBrand.Success
}

@Composable
private fun BatteryRing(
    glyph: ImageVector,
    label: String,
    percent: Int,
    charging: Boolean,
    lastKnown: Boolean,
) {
    val motionEnabled = rememberMotionEnabled()
    val animatedProgress by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = if (motionEnabled) tween(durationMillis = 700) else snap(),
        label = "batteryRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(88.dp),
                strokeWidth = 6.dp,
                color = batteryRingColor(percent, lastKnown),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Icon(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "$label: $percent%", style = MaterialTheme.typography.bodyMedium)
        if (charging) {
            Text(
                text = "Charging",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnavailableBatterySlot(kind: BatteryComponentKind) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            CircularProgressIndicator(
                progress = { 0f },
                modifier = Modifier.size(88.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = labelFor(kind), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Not reported",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// -- Noise control ------------------------------------------------------------------

@Composable
private fun NoiseControlSegmented(
    modes: List<String>,
    selectedMode: String?,
    interactive: Boolean,
    onSelect: (String) -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEachIndexed { index, mode ->
                val selected = mode == selectedMode
                val startCorner by animateDpAsState(
                    targetValue = when {
                        selected -> 24.dp
                        index == 0 -> 20.dp
                        else -> 6.dp
                    },
                    animationSpec = if (motionEnabled) tween(260) else snap(),
                    label = "segmentStart",
                )
                val endCorner by animateDpAsState(
                    targetValue = when {
                        selected -> 24.dp
                        index == modes.lastIndex -> 20.dp
                        else -> 6.dp
                    },
                    animationSpec = if (motionEnabled) tween(260) else snap(),
                    label = "segmentEnd",
                )
                val shape = RoundedCornerShape(
                    topStart = startCorner, bottomStart = startCorner,
                    topEnd = endCorner, bottomEnd = endCorner,
                )
                val background by animateColorAsState(
                    targetValue = if (selected) {
                        OmniBrand.Accent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = if (motionEnabled) tween(260) else snap(),
                    label = "segmentBg",
                )
                val foreground by animateColorAsState(
                    targetValue = if (selected) {
                        OmniBrand.OnAccent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = if (motionEnabled) tween(260) else snap(),
                    label = "segmentFg",
                )
                val content: @Composable () -> Unit = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                "ANC" -> OmniIcons.NoiseOff
                                "OFF" -> Icons.Filled.Close
                                "TRANSPARENCY" -> OmniIcons.SoundWaves
                                "ADAPTIVE" -> OmniIcons.Sparkle
                                else -> OmniIcons.SoundWaves
                            },
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prettyMode(mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = foreground,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (interactive) {
                    Surface(
                        onClick = { onSelect(mode) },
                        shape = shape,
                        color = background,
                        contentColor = foreground,
                        modifier = Modifier.weight(1f),
                    ) { content() }
                } else {
                    Surface(
                        shape = shape,
                        color = background,
                        contentColor = foreground,
                        modifier = Modifier.weight(1f),
                    ) { content() }
                }
            }
        }
    }
}

// -- Feature rows ---------------------------------------------------------------------

@Composable
private fun FeatureInfoRow(control: HardwareControlModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = when (control.featureId) {
                "eq_preset" -> OmniIcons.Sliders
                "spatial_audio" -> OmniIcons.SoundWaves
                "wear_detection" -> OmniIcons.BudsPair
                "multipoint" -> OmniIcons.Headphones
                else -> OmniIcons.Sliders
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = control.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (control.isActionable) {
            Text(
                text = control.currentValue?.let { prettyMode(it) } ?: "Available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "Not linked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// -- Product picker ---------------------------------------------------------------------

@Composable
private fun ProductPickerDialog(
    product: ProductCatalog.ProductInfo,
    hasUserPhoto: Boolean,
    hasOverride: Boolean,
    onSelectEntry: (String) -> Unit,
    onSelectGeneric: () -> Unit,
    onUseAutomatic: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val candidates = ProductCatalog.candidatesFor(product.category).filter { entry ->
        query.isBlank() ||
            "${entry.manufacturer} ${entry.model}".contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Product match") },
        text = {
            Column {
                Text(
                    text = when {
                        hasUserPhoto -> "Showing: your photo of this device."
                        product.catalogId != null &&
                            product.confidence != ProductCatalog.Confidence.CATEGORY_FALLBACK ->
                            "Matched: ${product.manufacturer} ${product.model}"
                        hasOverride -> "Showing: generic category artwork (your choice)."
                        else -> "No catalog match — generic category artwork is shown."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The picture and features follow your choice. The device name always stays as your earbuds report it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (
                        product.catalogId != null && !hasOverride &&
                        (product.confidence == ProductCatalog.Confidence.EXACT ||
                            product.confidence == ProductCatalog.Confidence.FAMILY)
                    ) {
                        TextButton(onClick = { onSelectEntry(product.catalogId) }) {
                            Text("This match is correct")
                        }
                    }
                    TextButton(onClick = onPickPhoto) {
                        Text("Use a photo of your device")
                    }
                }
                if (hasUserPhoto) {
                    TextButton(onClick = onRemovePhoto) { Text("Remove your photo") }
                }
                if (hasOverride) {
                    TextButton(onClick = onUseAutomatic) {
                        Text("Use the automatic match instead")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search the catalog") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(candidates.size) { index ->
                        val entry = candidates[index]
                        val isCurrent = product.catalogId == entry.id &&
                            product.confidence == ProductCatalog.Confidence.OVERRIDE
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectEntry(entry.id) }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = isCurrent,
                                onClick = { onSelectEntry(entry.id) },
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column {
                                Text(
                                    text = "${entry.manufacturer} ${entry.model}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = entry.category.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectGeneric() }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = product.confidence ==
                                    ProductCatalog.Confidence.OVERRIDE &&
                                    product.catalogId == null,
                                onClick = { onSelectGeneric() },
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "None of these — generic artwork",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun prettyMode(mode: String): String = when (mode) {
    "ANC" -> "Noise cancellation"
    "OFF" -> "Off"
    "TRANSPARENCY" -> "Transparency"
    "ADAPTIVE" -> "Adaptive"
    "FLAT" -> "Flat"
    "BASS_BOOST" -> "Bass boost"
    "VOCAL" -> "Vocal"
    "TREBLE_BOOST" -> "Treble boost"
    "ON" -> "On"
    "HEAD_TRACKING" -> "Head tracking"
    "NATURAL" -> "Natural"
    "VOICE" -> "Voice"
    "DISABLED" -> "Disabled"
    "ENABLED" -> "Enabled"
    "LOW" -> "Low"
    "HIGH" -> "High"
    else -> mode.lowercase().replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
}

