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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
 * Device details — Iteration 3.
 *
 * Identity is composed by the core DisplayIdentityResolver (alias,
 * then the reported name, then a neutral fallback) — the header can no
 * longer degrade to a placeholder. The layout follows the reference
 * hierarchy: hero, three-slot battery, segmented noise control,
 * per-feature cards, management actions.
 *
 * Iteration 2 notes follow.
 *
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
    deviceAlias: String? = null,
    onDeviceAliasChange: (String?) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showProductPicker by remember { mutableStateOf(false) }
    var showAliasEditor by remember { mutableStateOf(false) }
    var aliasText by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val product = ProductCatalog.resolve(state.overview?.displayName, productOverrideId)
    val connected = state.overview?.connectionState is ConnectionState.Connected

    val gattStatusMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.connectionStatus.collectAsState()
    val gattServicesMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.discoveredServices.collectAsState()
    val rawPacketsMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.lastRawPackets.collectAsState()

    val deviceGattStatus = gattStatusMap[state.deviceIdentifier] ?: if (connected) "Active" else "Idle"
    val deviceServices = gattServicesMap[state.deviceIdentifier] ?: emptyList()
    val lastRxHex = rawPacketsMap[state.deviceIdentifier]

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

        // -- Connected Device Header (Focused controls, no image clutter) -----
        item {
            val overview = state.overview
            val headerName = deviceAlias ?: overview?.displayName ?: "Bluetooth Earbuds"
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = headerName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(
                    onClick = {
                        aliasText = deviceAlias ?: ""
                        showAliasEditor = true
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = OmniIcons.Edit,
                        contentDescription = "Rename device",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, OmniBrand.Border),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connected) OmniBrand.Accent else OmniBrand.Disabled),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (connected) "Connected" else "Not connected",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (connected) OmniBrand.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
        if (ancControl != null &&
            (knownFeatures?.anc == true || ancControl.isActionable || ancControl.isReadOnly || connected)
        ) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = OmniIcons.SoundWaves,
                        contentDescription = null,
                        tint = OmniBrand.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(
                            text = "Active noise control",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Modes this model supports. OmniBuds shows the state it can observe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val modes = buildList {
                    if (knownFeatures?.anc == true || connected) add("ANC")
                    add("OFF")
                    if (knownFeatures?.transparency == true || connected) add("TRANSPARENCY")
                }.takeIf { it.size > 1 } ?: ancControl.availableModes
                NoiseControlSegmented(
                    modes = modes,
                    selectedMode = ancControl.currentValue ?: "OFF",
                    interactive = connected,
                    onSelect = { mode -> viewModel.submitFeatureControl("anc_mode", mode) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        !connected -> "Connect your earbuds first."
                        else -> "Changes apply to the earbuds."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // -- Device features (known capabilities or universal DSP, interactive when connected) ------------
        val featureRows = state.controls.filter { control ->
            control.featureId != "anc_mode" &&
                control.featureId != "transparency_mode" &&
                control.featureId != "touch_controls" &&
                ((knownFeatures != null && KnownDeviceFeatures.has(knownFeatures, control.featureId)) ||
                 (connected && (control.featureId == "eq_preset" || control.featureId == "spatial_audio")))
        }
        val codecRow = state.audio?.takeIf {
            it.isCodecObservable && it.activeCodecName != null
        }
        if (featureRows.isNotEmpty() || codecRow != null) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel("Sound & features")
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    featureRows.forEach { control ->
                        FeatureInfoRow(
                            control = control,
                            connected = connected,
                            onFeatureSelected = { fId, value ->
                                viewModel.submitFeatureControl(fId, value)
                            },
                        )
                    }
                    if (codecRow != null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                Icon(
                                    imageVector = OmniIcons.SoundWaves,
                                    contentDescription = null,
                                    tint = OmniBrand.Accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.size(16.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (connected) "Tap a feature to customize." else "Connect your earbuds first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // -- Button & touch controls --------------------------------------------
        if (product.category == ProductCatalog.Category.TWS || knownFeatures?.wearDetection == true) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel("Button & touch controls")
                Spacer(modifier = Modifier.height(12.dp))
                TouchControlsCard(
                    deviceId = state.deviceIdentifier,
                    connected = connected,
                    onGestureChanged = { bud, gesture, action ->
                        viewModel.submitFeatureControl("touch_controls", "$bud:$gesture:$action")
                    },
                )
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

        // -- Hardware Protocol Diagnostics & Live Scanner -------------------------
        item {
            Spacer(modifier = Modifier.height(24.dp))
            ProtocolDiagnosticsCard(
                deviceId = state.deviceIdentifier,
                connected = connected,
                statusText = deviceGattStatus,
                lastRxHex = lastRxHex,
                services = deviceServices,
                onSendRawHex = { hex ->
                    viewModel.submitFeatureControl("raw_hex", hex)
                },
            )
        }

        // -- Device management ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmForget = true },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, OmniBrand.Border),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = OmniIcons.Trash,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Forget")
                }
                Button(
                    onClick = { confirmDisconnect = true },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = OmniIcons.Power,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Disconnect")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // -- Dialogs ------------------------------------------------------------------
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect ${deviceAlias ?: state.overview?.displayName ?: "device"}?") },
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
            title = { Text("Forget ${deviceAlias ?: state.overview?.displayName ?: "device"}?") },
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

    if (showAliasEditor) {
        AlertDialog(
            onDismissRequest = { showAliasEditor = false },
            title = { Text("Device name") },
            text = {
                Column {
                    Text(
                        text = "Shown only in OmniBuds, on this phone. The name your " +
                            "device reports over Bluetooth does not change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeviceAliasChange(aliasText)
                        showAliasEditor = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (deviceAlias != null) {
                        TextButton(
                            onClick = {
                                onDeviceAliasChange(null)
                                showAliasEditor = false
                            },
                        ) { Text("Remove name") }
                    }
                    TextButton(onClick = { showAliasEditor = false }) { Text("Cancel") }
                }
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
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
    val leftReading = components.firstOrNull { it.kind == BatteryComponentKind.LEFT }
    val rightReading = components.firstOrNull { it.kind == BatteryComponentKind.RIGHT }
    val caseReading = components.firstOrNull { it.kind == BatteryComponentKind.CASE }
    val hasDistinctComponents = leftReading != null || rightReading != null || caseReading != null

    when {
        category == ProductCatalog.Category.TWS && hasDistinctComponents -> {
            // True individual component layout: LEFT | RIGHT | CASE (matching reference layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // LEFT EARBUD
                if (leftReading?.levelPercent != null) {
                    BatteryRing(
                        glyph = OmniIcons.BudLeft,
                        label = "Left",
                        badgeText = "L",
                        percent = leftReading.levelPercent!!,
                        charging = leftReading.isCharging == true,
                        lastKnown = lastKnown,
                    )
                } else {
                    UnavailableBatterySlot(kind = BatteryComponentKind.LEFT)
                }

                // RIGHT EARBUD
                if (rightReading?.levelPercent != null) {
                    BatteryRing(
                        glyph = OmniIcons.BudRight,
                        label = "Right",
                        badgeText = "R",
                        percent = rightReading.levelPercent!!,
                        charging = rightReading.isCharging == true,
                        lastKnown = lastKnown,
                    )
                } else {
                    UnavailableBatterySlot(kind = BatteryComponentKind.RIGHT)
                }

                // CHARGING CASE - only display real case battery; never copy earbud's level!
                if (caseReading?.levelPercent != null && (caseReading.levelPercent!! > 0 || caseReading.isCharging == true)) {
                    BatteryRing(
                        glyph = OmniIcons.Case,
                        label = "Case",
                        badgeText = "Case",
                        percent = caseReading.levelPercent!!,
                        charging = caseReading.isCharging == true,
                        lastKnown = lastKnown,
                    )
                } else {
                    UnavailableBatterySlot(kind = BatteryComponentKind.CASE)
                }
            }
            if (lastKnown) LastKnownCaption()
        }
        category == ProductCatalog.Category.TWS && battery.overallPercent != null -> {
            // Single-channel Bluetooth battery reading from the active connected earbud
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryRing(
                    glyph = OmniIcons.BudsPair,
                    label = "Active Earbuds",
                    badgeText = "Earbuds",
                    percent = battery.overallPercent,
                    charging = battery.isCharging == true,
                    lastKnown = lastKnown,
                    ringSize = 96.dp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reported by active earbud. Dual L/R and case telemetry activate when reported by the dock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                if (lastKnown) LastKnownCaption()
            }
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
                    label = "Battery",
                    percent = battery.overallPercent,
                    charging = battery.isCharging == true,
                    lastKnown = lastKnown,
                    ringSize = 100.dp,
                )
                if (lastKnown) LastKnownCaption()
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
    badgeText: String? = null,
    ringSize: Dp = 88.dp,
) {
    val motionEnabled = rememberMotionEnabled()
    val animatedProgress by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = if (motionEnabled) tween(durationMillis = 700) else snap(),
        label = "batteryRing",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ringSize + 8.dp),
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(ringSize),
                strokeWidth = 6.dp,
                color = batteryRingColor(percent, lastKnown),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Icon(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = if (lastKnown) OmniBrand.Disabled else MaterialTheme.colorScheme.onSurface,
            )
            if (charging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(OmniBrand.Success),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = OmniIcons.Bolt,
                        contentDescription = "Charging",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (badgeText != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(bottom = 3.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (charging) {
            Text(
                text = "Charging",
                style = MaterialTheme.typography.labelSmall,
                color = OmniBrand.Success,
            )
        }
    }
}

@Composable
private fun UnavailableBatterySlot(kind: BatteryComponentKind) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp),
        ) {
            CircularProgressIndicator(
                progress = { 0f },
                modifier = Modifier.size(88.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Icon(
                imageVector = glyphFor(kind),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = OmniBrand.Disabled,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(bottom = 3.dp),
        ) {
            Text(
                text = when (kind) {
                    BatteryComponentKind.LEFT -> "L"
                    BatteryComponentKind.RIGHT -> "R"
                    BatteryComponentKind.CASE -> "Case"
                    BatteryComponentKind.DEVICE -> "All"
                },
                style = MaterialTheme.typography.labelSmall,
                color = OmniBrand.Disabled,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = "Standby",
            style = MaterialTheme.typography.bodySmall,
            color = OmniBrand.Disabled,
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
        shape = RoundedCornerShape(28.dp),
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
                        Color.Transparent
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
                                "ANC" -> OmniIcons.SoundWaves
                                "OFF" -> OmniIcons.NoiseOff
                                "TRANSPARENCY" -> OmniIcons.PersonOutline
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
private fun FeatureInfoRow(
    control: HardwareControlModel,
    connected: Boolean,
    onFeatureSelected: (String, String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val isInteractive = connected

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { if (isInteractive) showDialog = true },
        enabled = isInteractive,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = when (control.featureId) {
                    "eq_preset" -> OmniIcons.Sliders
                    "spatial_audio" -> OmniIcons.Sparkle
                    "wear_detection" -> OmniIcons.BudsPair
                    "multipoint" -> OmniIcons.Headphones
                    else -> OmniIcons.Sliders
                },
                contentDescription = null,
                tint = OmniBrand.Accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = control.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = when {
                        !connected -> "Connect earbuds to configure"
                        control.currentValue != null -> "Current: ${prettyMode(control.currentValue!!)}"
                        else -> "Tap to configure"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (control.featureId == "spatial_audio" && isInteractive) {
                val isOn = control.currentValue.equals("ON", ignoreCase = true)
                Switch(
                    checked = isOn,
                    onCheckedChange = { checked ->
                        onFeatureSelected("spatial_audio", if (checked) "ON" else "OFF")
                    },
                )
            } else if (isInteractive) {
                Text(
                    text = "Configure",
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniBrand.Accent,
                )
            }
        }
    }

    if (showDialog) {
        if (control.featureId == "eq_preset") {
            EqualizerDialog(
                initialPreset = control.currentValue ?: "FLAT",
                onPresetSelected = { preset ->
                    onFeatureSelected("eq_preset", preset)
                },
                onCustomGainsChanged = { gains ->
                    onFeatureSelected("eq_custom", gains.joinToString(","))
                },
                onDismiss = { showDialog = false },
            )
        } else {
            val options = when (control.featureId) {
                "spatial_audio" -> listOf(
                    "OFF" to "Standard Stereo (Off)",
                    "ON" to "3D Spatial Surround (On)",
                )
                else -> control.availableModes.map { it to prettyMode(it) }
            }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = Color(0xFF1E1E24),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFE2E2E8),
                title = { Text(control.displayName) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (value, label) ->
                            val selected = control.currentValue == value
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) OmniBrand.Accent else Color(0xFF2C2C34),
                                border = BorderStroke(1.dp, if (selected) OmniBrand.Accent else OmniBrand.Border),
                                onClick = {
                                    onFeatureSelected(control.featureId, value)
                                    showDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (selected) OmniBrand.OnAccent else Color.White,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Close", color = OmniBrand.Accent)
                    }
                },
            )
        }
    }
}

@Composable
private fun EqualizerDialog(
    initialPreset: String,
    onPresetSelected: (String) -> Unit,
    onCustomGainsChanged: (FloatArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(
        "FLAT" to "Balanced",
        "BASS_BOOST" to "Bass Boost+",
        "VOCAL" to "Clear Vocals",
        "TREBLE_BOOST" to "Bright & Crisp",
        "HARMAN_TARGET" to "Harman Target",
        "WARMTH" to "Warm & Smooth",
    )
    val presetGains = mapOf(
        "FLAT" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_FLAT,
        "BASS_BOOST" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_BASS_BOOST,
        "VOCAL" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_VOCAL,
        "TREBLE_BOOST" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_TREBLE_BOOST,
        "HARMAN_TARGET" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_HARMAN,
        "WARMTH" to com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_WARMTH,
    )
    val freqLabels = listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")

    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var bandGains by remember {
        mutableStateOf(presetGains[initialPreset]?.copyOf() ?: com.omnibuds.android.hardware.engine.UniversalDspController.PRESET_FLAT.copyOf())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E2E8),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcons.Sliders, contentDescription = null, tint = OmniBrand.Accent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Universal Equalizer")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Instant Presets", style = MaterialTheme.typography.labelMedium, color = OmniBrand.Accent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.take(3).forEach { (key, label) ->
                            val sel = selectedPreset == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) OmniBrand.Accent else Color(0xFF2C2C34),
                                border = BorderStroke(1.dp, if (sel) OmniBrand.Accent else OmniBrand.Border),
                                modifier = Modifier.weight(1f).clickable {
                                    selectedPreset = key
                                    val newGains = presetGains[key]!!.copyOf()
                                    bandGains = newGains
                                    onPresetSelected(key)
                                },
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal),
                                    color = if (sel) OmniBrand.OnAccent else Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.drop(3).forEach { (key, label) ->
                            val sel = selectedPreset == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) OmniBrand.Accent else Color(0xFF2C2C34),
                                border = BorderStroke(1.dp, if (sel) OmniBrand.Accent else OmniBrand.Border),
                                modifier = Modifier.weight(1f).clickable {
                                    selectedPreset = key
                                    val newGains = presetGains[key]!!.copyOf()
                                    bandGains = newGains
                                    onPresetSelected(key)
                                },
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal),
                                    color = if (sel) OmniBrand.OnAccent else Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("10-Band Sliders (-10dB to +10dB)", style = MaterialTheme.typography.labelMedium, color = OmniBrand.Accent)
                }

                items(10) { i ->
                    val gain = bandGains[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = freqLabels[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.width(48.dp),
                        )
                        Slider(
                            value = gain,
                            onValueChange = { newVal ->
                                val updated = bandGains.copyOf()
                                updated[i] = (newVal * 10).toInt() / 10f
                                bandGains = updated
                                selectedPreset = "CUSTOM"
                                onCustomGainsChanged(updated)
                            },
                            valueRange = -10f..10f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${if (gain > 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", gain)}dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (gain != 0f) OmniBrand.Accent else Color.Gray,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(52.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = OmniBrand.Accent)
            }
        },
    )
}

// -- Button & touch controls card ----------------------------------------------------

@Composable
private fun TouchControlsCard(
    deviceId: String,
    connected: Boolean,
    onGestureChanged: (bud: String, gesture: String, action: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(deviceId) {
        context.getSharedPreferences("omni_touch_controls", Context.MODE_PRIVATE)
    }

    var selectedBud by remember { mutableStateOf("Left") }
    var activeGestureToEdit by remember { mutableStateOf<String?>(null) }

    fun getAction(bud: String, gesture: String): String {
        val key = "${deviceId}_${bud}_$gesture"
        return prefs.getString(key, null) ?: when (gesture) {
            "double_tap" -> if (bud == "Left") "PLAY_PAUSE" else "NEXT_TRACK"
            "triple_tap" -> if (bud == "Left") "PREVIOUS_TRACK" else "GAME_MODE"
            "long_press" -> "SWITCH_NOISE_CONTROL"
            else -> "PLAY_PAUSE"
        }
    }

    fun prettyAction(action: String): String = when (action) {
        "PLAY_PAUSE" -> "Play / Pause"
        "NEXT_TRACK" -> "Next Track"
        "PREVIOUS_TRACK" -> "Previous Track"
        "SWITCH_NOISE_CONTROL" -> "Switch Noise Control (ANC / Ambient)"
        "GAME_MODE" -> "Game Mode (Low Latency)"
        "VOICE_ASSISTANT" -> "Voice Assistant"
        "VOLUME_UP" -> "Volume Up"
        "VOLUME_DOWN" -> "Volume Down"
        "NONE" -> "None"
        else -> action
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Segmented tab: Left Earbud | Right Earbud
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
            ) {
                listOf("Left", "Right").forEach { bud ->
                    val isSelected = selectedBud == bud
                    Surface(
                        onClick = { selectedBud = bud },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) OmniBrand.Accent else Color.Transparent,
                        contentColor = if (isSelected) OmniBrand.OnAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = if (bud == "Left") OmniIcons.BudLeft else OmniIcons.BudRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$bud Earbud",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gesture rows
            val gestures = listOf(
                "double_tap" to "Double tap",
                "triple_tap" to "Triple tap",
                "long_press" to "Touch & hold",
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                gestures.forEach { (gKey, gLabel) ->
                    val currentAct = getAction(selectedBud, gKey)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, OmniBrand.Border),
                        onClick = { if (connected) activeGestureToEdit = gKey },
                        enabled = connected,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = prettyAction(currentAct),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OmniBrand.Accent,
                                )
                            }
                            if (connected) {
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (connected) "Tap any gesture to customize button behavior on the earbuds." else "Connect earbuds to customize gestures.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    activeGestureToEdit?.let { gKey ->
        val gLabel = when (gKey) {
            "double_tap" -> "Double tap"
            "triple_tap" -> "Triple tap"
            "long_press" -> "Touch & hold"
            else -> gKey
        }
        val options = listOf(
            "PLAY_PAUSE",
            "NEXT_TRACK",
            "PREVIOUS_TRACK",
            "SWITCH_NOISE_CONTROL",
            "GAME_MODE",
            "VOICE_ASSISTANT",
            if (selectedBud == "Left") "VOLUME_DOWN" else "VOLUME_UP",
            "NONE",
        )
        val currentAct = getAction(selectedBud, gKey)

        AlertDialog(
            onDismissRequest = { activeGestureToEdit = null },
            containerColor = Color(0xFF1E1E24),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E2E8),
            title = { Text("$gLabel ($selectedBud Earbud)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { act ->
                        val selected = act == currentAct
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) OmniBrand.Accent else Color(0xFF2C2C34),
                            border = BorderStroke(1.dp, if (selected) OmniBrand.Accent else OmniBrand.Border),
                            onClick = {
                                val key = "${deviceId}_${selectedBud}_$gKey"
                                prefs.edit().putString(key, act).apply()
                                onGestureChanged(selectedBud, gKey, act)
                                activeGestureToEdit = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = prettyAction(act),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                color = if (selected) OmniBrand.OnAccent else Color.White,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeGestureToEdit = null }) {
                    Text("Cancel", color = OmniBrand.Accent)
                }
            },
        )
    }
}

// -- Hardware Protocol Diagnostics & Live Scanner ----------------------------------------

@Composable
private fun ProtocolDiagnosticsCard(
    deviceId: String,
    connected: Boolean,
    statusText: String,
    lastRxHex: String?,
    services: List<com.omnibuds.android.hardware.protocol.GattServiceDiagnostic>,
    onSendRawHex: (String) -> Unit,
) {
    var expandedServices by remember { mutableStateOf(false) }
    var customHexInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = OmniIcons.Sparkle,
                        contentDescription = null,
                        tint = OmniBrand.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Protocol & Live Diagnostics",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (connected) OmniBrand.Accent.copy(alpha = 0.2f) else Color.DarkGray,
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) OmniBrand.Accent else Color.LightGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Last Received Hex Packet
            Text(
                text = "Last Incoming Packet (RX)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, OmniBrand.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = lastRxHex ?: "Awaiting live telemetry packets...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    color = if (lastRxHex != null) OmniBrand.Accent else Color.Gray,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // GATT Services Discovery Viewer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Discovered Services (${services.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { expandedServices = !expandedServices }) {
                    Text(
                        text = if (expandedServices) "Hide UUIDs" else "View All UUIDs",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniBrand.Accent,
                    )
                }
            }

            if (expandedServices) {
                if (services.isEmpty()) {
                    Text(
                        text = if (connected) "Discovering GATT services..." else "Connect earbuds to view discovered services.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        services.forEach { service ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1A1A22),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = service.name,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                    Text(
                                        text = service.uuid,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        ),
                                        color = Color.LightGray,
                                    )
                                    service.characteristics.forEach { char ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 8.dp),
                                        ) {
                                            Text(
                                                text = "↳ ${char.uuid.take(8)}...",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                ),
                                                color = Color.Gray,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = char.properties.joinToString("/"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniBrand.Accent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Custom Hex Command Sender
            Text(
                text = "Send Raw Hex Command",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = customHexInput,
                    onValueChange = { customHexInput = it },
                    placeholder = { Text("e.g. AA 08 00 00 04 04 01 01 00 01", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customHexInput.isNotBlank()) {
                            onSendRawHex(customHexInput.trim())
                        }
                    },
                    enabled = connected && customHexInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Send")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Quick preset chips for rapid testing
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF26262E),
                    onClick = { customHexInput = "AA 07 00 00 01 06 01 00 00" },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Query Batt",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF26262E),
                    onClick = { customHexInput = "AA 08 00 00 04 04 01 01 00 01" },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "ANC On",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF26262E),
                    onClick = { customHexInput = "AA 08 00 00 04 04 01 01 00 02" },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Ambient",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
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

