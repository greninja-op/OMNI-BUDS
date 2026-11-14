package com.omnibuds.android.ui.compose.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.presentation.workspace.HardwareControlModel
import com.omnibuds.android.presentation.workspace.KnownDeviceFeatures
import com.omnibuds.android.presentation.workspace.ProductCatalog
import com.omnibuds.android.hardware.capability.HardwareCapabilityInspector
import com.omnibuds.android.hardware.capability.PhysicalInputSensor
import com.omnibuds.android.hardware.engine.BinauralCrossfeedMode
import com.omnibuds.android.hardware.engine.NonTwsHardwareController
import com.omnibuds.android.ui.compose.components.AdaptiveAudioGlyph
import com.omnibuds.android.hardware.capability.StrictBrandProfile
import com.omnibuds.android.hardware.capability.BatteryDisplayContract
import com.omnibuds.android.hardware.capability.StrictBrandProfileResolver
import com.omnibuds.android.hardware.capability.VerifiedDeviceCatalog
import com.omnibuds.android.hardware.capability.BrandSchema
import com.omnibuds.android.ui.compose.components.NothingXNoiseControlCard
import com.omnibuds.android.ui.compose.components.NothingXEqualizerCard
import com.omnibuds.android.ui.compose.components.NothingXUltraBassCard
import com.omnibuds.android.ui.compose.components.NothingXTouchControlsCard
import com.omnibuds.android.ui.compose.components.NothingXDeviceSettingsCard
import com.omnibuds.android.ui.compose.components.AdaptiveBatteryRings
import com.omnibuds.android.ui.compose.components.AutoStandbyTimerCard
import com.omnibuds.android.ui.compose.components.CollarVibrationCard
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.FormFactorPickerDialog
import com.omnibuds.android.ui.compose.components.GlyphSide
import com.omnibuds.android.ui.compose.components.InlineRockerControllerCard
import com.omnibuds.android.ui.compose.components.MagneticHallSwitchCard
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.components.OpenAcousticsStudioCard
import com.omnibuds.android.ui.compose.components.OverEarAmbientAndClampingCard
import com.omnibuds.android.ui.compose.components.ProductVisual
import com.omnibuds.android.ui.compose.components.SpeakToChatCard
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.android.ui.compose.model.AudioFormFactor
import com.omnibuds.android.ui.compose.model.AudioFormFactorResolver
import com.omnibuds.android.ui.compose.theme.OmniBrand
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.presentation.battery.BatteryComponentKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val product = ProductCatalog.resolve(state.overview?.displayName, productOverrideId)
    val connected = state.overview?.connectionState is ConnectionState.Connected
    val saveStates by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.configSaveState.collectAsState()
    val saveMessage = saveStates[state.deviceIdentifier]

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager }
    val bluetoothDevice = remember(state.deviceIdentifier) {
        try { bluetoothManager?.adapter?.getRemoteDevice(state.deviceIdentifier) } catch (_: Exception) { null }
    }

    val gattStatusMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.connectionStatus.collectAsState()
    val gattServicesMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.discoveredServices.collectAsState()
    val rawPacketsMap by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.lastRawPackets.collectAsState()

    val deviceGattStatus = gattStatusMap[state.deviceIdentifier] ?: if (connected) "Active" else "Idle"
    val deviceServices = gattServicesMap[state.deviceIdentifier] ?: emptyList()
    val lastRxHex = rawPacketsMap[state.deviceIdentifier]

    var formFactorOverride by remember(state.deviceIdentifier) {
        mutableStateOf(AudioFormFactorResolver.getOverriddenFormFactor(context, state.deviceIdentifier))
    }
    var forceCombinedView by remember(state.deviceIdentifier) {
        mutableStateOf(AudioFormFactorResolver.getCombinedViewPreference(context, state.deviceIdentifier))
    }
    var showFormFactorPicker by remember { mutableStateOf(false) }

    val manifest = remember(state.deviceIdentifier, state.overview?.displayName, formFactorOverride) {
        HardwareCapabilityInspector.inspect(
            null,
            state.deviceIdentifier,
            state.overview?.displayName,
            explicitFormFactor = formFactorOverride,
        )
    }

    val strictProfile = remember(state.deviceIdentifier, state.overview?.displayName) {
        StrictBrandProfileResolver.resolve(
            device = bluetoothDevice,
            nameOverride = state.overview?.displayName,
            addressOverride = state.deviceIdentifier,
            discoveredGattUuids = deviceServices.map { it.uuid },
        )
    }

    val verifiedProfile = remember(state.deviceIdentifier, state.overview?.displayName) {
        VerifiedDeviceCatalog.resolve(
            state.overview?.displayName,
            state.deviceIdentifier,
        )
    }

    var nothingAncSubLevel by remember(state.deviceIdentifier) {
        mutableStateOf("High (42dB)")
    }
    var nothingEqPreset by remember(state.deviceIdentifier) {
        mutableStateOf(strictProfile.defaultEqPreset)
    }
    var ultraBassOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var ultraBassLevel by remember(state.deviceIdentifier) {
        androidx.compose.runtime.mutableIntStateOf(3)
    }
    var inEarDetectionOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var lowLagModeOn by remember(state.deviceIdentifier) {
        mutableStateOf(false)
    }
    var dualConnectionOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }

    val nonTwsController = remember { NonTwsHardwareController() }
    var openAirBassLevel by remember(state.deviceIdentifier) {
        androidx.compose.runtime.mutableIntStateOf(2)
    }
    var antiLeakageOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var crossfeedMode by remember(state.deviceIdentifier) {
        mutableStateOf(BinauralCrossfeedMode.OFF)
    }
    var boneClarifierOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var autoPlayOnSeparate by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var instantPauseOnClasp by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var collarVibrationOn by remember(state.deviceIdentifier) {
        mutableStateOf(true)
    }
    var ambientSoundLevel by remember(state.deviceIdentifier) {
        androidx.compose.runtime.mutableIntStateOf(10)
    }
    var voiceFocusOn by remember(state.deviceIdentifier) {
        mutableStateOf(false)
    }
    var speakToChatOn by remember(state.deviceIdentifier) {
        mutableStateOf(false)
    }
    var speakToChatSensitivity by remember(state.deviceIdentifier) {
        mutableStateOf(NonTwsHardwareController.SpeakToChatSensitivity.AUTO)
    }
    var speakToChatPauseTime by remember(state.deviceIdentifier) {
        mutableStateOf(NonTwsHardwareController.SpeakToChatPauseTime.STANDARD_15S)
    }
    var autoStandbyTimer by remember(state.deviceIdentifier) {
        mutableStateOf(NonTwsHardwareController.AutoStandbyTime.NEVER)
    }
    var optimizerRunning by remember { mutableStateOf(false) }

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
        contentPadding = PaddingValues(top = 96.dp, bottom = 80.dp),
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
                connected = connected,
                battery = if (connected) state.battery else null,
                category = product.category,
                deviceName = state.overview?.displayName,
                deviceId = state.deviceIdentifier,
                formFactor = manifest.formFactor,
                forceCombinedView = forceCombinedView,
                supportsCaseBatteryWhenUndocked = strictProfile.supportsCaseBatteryWhenUndocked,
                brandSchema = strictProfile.brandSchema,
                batteryContract = verifiedProfile?.batteryContract ?: strictProfile.batteryContract,
                batteryTopology = strictProfile.batteryTopology,
                verifiedLayoutLabel = verifiedProfile?.let { "Layout matched to ${it.officialAppName}" },
                onChangeStyleClick = { showFormFactorPicker = true },
                onToggleViewMode = {
                    val next = !forceCombinedView
                    forceCombinedView = next
                    AudioFormFactorResolver.setCombinedViewPreference(context, state.deviceIdentifier, next)
                },
            )
        }

        // -- Noise control / Open-Acoustics Studio ------------------------------
        val ancControl = state.controls.firstOrNull { it.featureId == "anc_mode" }
        val knownFeatures = product.features

        if (strictProfile.brandSchema == BrandSchema.NOTHING_X) {
            // -- 1. Nothing X Noise Control Card --
            item {
                Spacer(modifier = Modifier.height(28.dp))
                NothingXNoiseControlCard(
                    currentMode = ancControl?.currentValue ?: "Active Noise Cancellation",
                    currentSubLevel = nothingAncSubLevel,
                    interactive = connected,
                    onSelectMode = { mode ->
                        viewModel.submitFeatureControl("anc_mode", mode)
                    },
                    onSelectSubLevel = { subLvl ->
                        nothingAncSubLevel = subLvl
                        viewModel.submitFeatureControl("pro_anc_level", subLvl)
                    },
                )
            }

            // -- 2. Nothing X Equalizer Card --
            item {
                Spacer(modifier = Modifier.height(16.dp))
                NothingXEqualizerCard(
                    currentPreset = nothingEqPreset,
                    interactive = connected,
                    onSelectPreset = { preset ->
                        nothingEqPreset = preset
                        viewModel.submitFeatureControl("eq_preset", preset)
                    },
                    on3BandGainsChanged = { b, m, t ->
                        viewModel.submitFeatureControl("eq_3band", "$b,$m,$t")
                    },
                )
            }

            // -- 3. Ultra Bass Technology 2.0 Card --
            if (strictProfile.hasUltraBass) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    NothingXUltraBassCard(
                        enabled = ultraBassOn,
                        level = ultraBassLevel,
                        interactive = connected,
                        onToggle = { on ->
                            ultraBassOn = on
                            viewModel.submitFeatureControl("ultra_bass", "${if (on) "ON" else "OFF"}:$ultraBassLevel")
                        },
                        onLevelChange = { lvl ->
                            ultraBassLevel = lvl
                            viewModel.submitFeatureControl("ultra_bass", "ON:$lvl")
                        },
                    )
                }
            }

            // -- 4. Touch Controls Card --
            item {
                Spacer(modifier = Modifier.height(16.dp))
                NothingXTouchControlsCard(
                    deviceId = state.deviceIdentifier,
                    interactive = connected,
                    onGestureChanged = { bud, gesture, action ->
                        viewModel.submitFeatureControl("touch_controls", "$bud:$gesture:$action")
                    },
                )
            }

            // -- 5. Device Settings & System Toggles --
            item {
                Spacer(modifier = Modifier.height(16.dp))
                NothingXDeviceSettingsCard(
                    profile = strictProfile,
                    inEarDetectionOn = inEarDetectionOn,
                    lowLagModeOn = lowLagModeOn,
                    dualConnectionOn = dualConnectionOn,
                    interactive = connected,
                    onInEarDetectionToggle = { on ->
                        inEarDetectionOn = on
                        viewModel.submitFeatureControl("in_ear_detection", if (on) "ON" else "OFF")
                    },
                    onLowLagModeToggle = { on ->
                        lowLagModeOn = on
                        viewModel.submitFeatureControl("low_lag_mode", if (on) "ON" else "OFF")
                    },
                    onDualConnectionToggle = { on ->
                        dualConnectionOn = on
                        viewModel.submitFeatureControl("dual_connection", if (on) "ON" else "OFF")
                    },
                    onRingEarbud = { bud, ring ->
                        viewModel.submitFeatureControl("find_my_buds", "$bud:${if (ring) "ON" else "OFF"}")
                    },
                )
            }
        } else if (manifest.isOpenAcoustics) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                OpenAcousticsStudioCard(
                    formFactor = manifest.formFactor,
                    bassLevel = openAirBassLevel,
                    antiLeakageOn = antiLeakageOn,
                    crossfeedMode = crossfeedMode,
                    boneClarifierOn = boneClarifierOn,
                    interactive = connected,
                    onBassLevelChange = { lvl ->
                        openAirBassLevel = lvl
                        viewModel.submitFeatureControl("open_air_bass", "$lvl")
                    },
                    onAntiLeakageToggle = { on ->
                        antiLeakageOn = on
                        viewModel.submitFeatureControl("anti_leakage", if (on) "ON" else "OFF")
                    },
                    onCrossfeedChange = { mode ->
                        crossfeedMode = mode
                        viewModel.submitFeatureControl("crossfeed_mode", mode.name)
                    },
                    onBoneClarifierToggle = { on ->
                        boneClarifierOn = on
                        viewModel.submitFeatureControl("bone_clarifier", if (on) "ON" else "OFF")
                    },
                )
            }
        } else {
            // -- Noise control ------------------------------------------------------
            if (ancControl != null &&
                (knownFeatures?.anc == true || ancControl.isActionable || ancControl.isReadOnly || connected)
            ) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = OmniIcons.SoundWaves,
                            contentDescription = null,
                            tint = OmniBrand.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "ANC",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    val modes = buildList {
                        if (knownFeatures?.anc == true || connected) add("ANC")
                        add("OFF")
                        if (knownFeatures?.transparency == true || connected) add("TRANSPARENCY")
                    }.takeIf { it.size > 1 } ?: ancControl.availableModes
                    NoiseControlCircles(
                        modes = modes,
                        selectedMode = ancControl.currentValue ?: "OFF",
                        interactive = connected,
                        auditAddress = state.deviceIdentifier,
                        onSelect = { mode -> viewModel.submitFeatureControl("anc_mode", mode) },
                    )
                    saveMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF22E55E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NoiseControlDiagnosticDrawer(
                        deviceAddress = state.deviceIdentifier,
                        device = bluetoothDevice,
                        connected = connected,
                    )
                }
            }
        }

        if (strictProfile.brandSchema != BrandSchema.NOTHING_X) {
            // -- Over-Ear Controls (Ambient & Pressure Optimizer, Speak-to-Chat, Standby Timer) ---
            if (manifest.formFactor == AudioFormFactor.HEADSET_OVER_ON_EAR) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OverEarAmbientAndClampingCard(
                        ambientLevel = ambientSoundLevel,
                        voiceFocusOn = voiceFocusOn,
                        interactive = connected,
                        onAmbientLevelChange = { lvl ->
                            ambientSoundLevel = lvl
                            viewModel.submitFeatureControl("overear_ambient_lvl", "$lvl")
                        },
                        onVoiceFocusToggle = { on ->
                            voiceFocusOn = on
                            viewModel.submitFeatureControl("overear_voice_focus", if (on) "ON" else "OFF")
                        },
                        onRunClampingOptimizer = {
                            scope.launch {
                                optimizerRunning = true
                                nonTwsController.runClampingOptimizer(
                                    onProgress = {},
                                    onComplete = { optimizerRunning = false },
                                )
                            }
                        },
                        optimizerRunning = optimizerRunning,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SpeakToChatCard(
                        enabled = speakToChatOn,
                        sensitivity = speakToChatSensitivity,
                        pauseTime = speakToChatPauseTime,
                        interactive = connected,
                        onToggle = { on ->
                            speakToChatOn = on
                            viewModel.submitFeatureControl("speak_to_chat", if (on) "ON" else "OFF")
                        },
                        onSensitivityChange = { s -> speakToChatSensitivity = s },
                        onPauseTimeChange = { p -> speakToChatPauseTime = p },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AutoStandbyTimerCard(
                        currentTimer = autoStandbyTimer,
                        interactive = connected,
                        onTimerChange = { t -> autoStandbyTimer = t },
                    )
                }
            }

            // -- Device features (known capabilities or universal DSP, interactive when connected) ------------
            val featureRows = state.controls.filter { control ->
                control.featureId != "anc_mode" &&
                    control.featureId != "transparency_mode" &&
                    control.featureId != "touch_controls" &&
                    ((knownFeatures != null && KnownDeviceFeatures.has(knownFeatures, control.featureId)) ||
                     (connected && (control.featureId == "eq_preset" ||
                                    control.featureId == "spatial_audio" ||
                                    control.featureId == "game_mode" ||
                                    control.featureId == "volume_enhancer")))
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
                }
            }

            // -- Button & touch controls / Neckband Controller ---------------------
            if (manifest.formFactor == AudioFormFactor.NECKBAND) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionLabel("Neckband Magnetic & Inline Button Controls")
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MagneticHallSwitchCard(
                            autoPlayOnSeparate = autoPlayOnSeparate,
                            instantPauseOnClasp = instantPauseOnClasp,
                            interactive = connected,
                            onAutoPlayToggle = { on ->
                                autoPlayOnSeparate = on
                                nonTwsController.isAutoPlayOnSeparateEnabled = on
                            },
                            onInstantPauseToggle = { on ->
                                instantPauseOnClasp = on
                                nonTwsController.isInstantPauseOnClaspEnabled = on
                            },
                        )
                        CollarVibrationCard(
                            vibrationOn = collarVibrationOn,
                            interactive = connected,
                            onVibrationToggle = { on ->
                                collarVibrationOn = on
                                nonTwsController.isCollarVibrationEnabled = on
                            },
                            onTestPulse = {
                                nonTwsController.triggerVibrationPulse(context)
                            },
                        )
                        InlineRockerControllerCard()
                    }
                }
            } else if (product.category == ProductCatalog.Category.TWS || knownFeatures?.wearDetection == true || manifest.formFactor.isBinauralSplit) {
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
                    if (com.omnibuds.android.hardware.engine.UniversalTwsEngine.hasVerifiedModeReadback(state.deviceIdentifier)) {
                        val holdMasks by com.omnibuds.android.hardware.engine.UniversalTwsEngine.holdCycleMasks.collectAsState()
                        var holdSaving by remember(state.deviceIdentifier) { mutableStateOf(false) }
                        var holdMessage by remember(state.deviceIdentifier) { mutableStateOf<String?>(null) }
                        val modeBytes = com.omnibuds.android.hardware.engine.UniversalTwsEngine.getAncMapping(state.deviceIdentifier)
                        Spacer(modifier = Modifier.height(12.dp))
                        HoldCycleCard(
                            currentMask = holdMasks[state.deviceIdentifier],
                            ancBit = modeBytes.ancByte.toInt() and 0xFF,
                            transparencyBit = modeBytes.transByte.toInt() and 0xFF,
                            ambientBit = modeBytes.offByte.toInt() and 0xFF,
                            enabled = connected && !holdSaving,
                            saving = holdSaving,
                            message = holdMessage,
                            onSelect = { mask ->
                                val device = bluetoothDevice
                                if (device != null) {
                                    holdSaving = true
                                    holdMessage = null
                                    scope.launch {
                                        val saved = com.omnibuds.android.hardware.engine.UniversalTwsEngine.setHoldCycle(device, mask)
                                        holdSaving = false
                                        holdMessage = if (saved) {
                                            "Saved inside the earbuds."
                                        } else {
                                            "The earbuds did not keep that change. Nothing was changed."
                                        }
                                    }
                                }
                            },
                        )
                    }
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
    if (showFormFactorPicker) {
        FormFactorPickerDialog(
            currentFactor = manifest.formFactor,
            deviceId = state.deviceIdentifier,
            onDismiss = { showFormFactorPicker = false },
            onSelect = { factor ->
                formFactorOverride = factor
            },
        )
    }

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
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
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
    connected: Boolean,
    battery: com.omnibuds.android.presentation.battery.BatteryPresentationModel?,
    category: ProductCatalog.Category,
    deviceName: String?,
    deviceId: String,
    formFactor: AudioFormFactor,
    forceCombinedView: Boolean,
    supportsCaseBatteryWhenUndocked: Boolean = false,
    brandSchema: BrandSchema = BrandSchema.GENERIC,
    batteryContract: BatteryDisplayContract = BatteryDisplayContract(),
    batteryTopology: com.omnibuds.android.hardware.capability.BatteryTopologyMode =
        com.omnibuds.android.hardware.capability.BatteryTopologyMode.DYNAMIC_COMBINED_FALLBACK,
    verifiedLayoutLabel: String? = null,
    onChangeStyleClick: () -> Unit,
    onToggleViewMode: () -> Unit,
) {
    if (!connected) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, OmniBrand.Border.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = OmniIcons.BudLeft,
                    contentDescription = null,
                    tint = OmniBrand.Disabled,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Earbuds Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Turn on Bluetooth or reconnect your earbuds to view battery telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val liveReports by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.latestBatteryReports.collectAsState()
    val liveReport = liveReports[deviceId]

    val components = battery?.components?.filter { it.kind != BatteryComponentKind.DEVICE }.orEmpty()
    val lastKnown = battery?.isLastKnown ?: false
    val leftReading = components.firstOrNull { it.kind == BatteryComponentKind.LEFT }
    val rightReading = components.firstOrNull { it.kind == BatteryComponentKind.RIGHT }
    val caseReading = components.firstOrNull { it.kind == BatteryComponentKind.CASE }

    val lPercent = if (liveReport != null) liveReport.left else (if (connected) leftReading?.levelPercent else null)
    val rPercent = if (liveReport != null) liveReport.right else (if (connected) rightReading?.levelPercent else null)
    val cPercent = if (liveReport != null) liveReport.case else (if (connected) caseReading?.levelPercent?.takeIf { it in 1..100 } else null)

    val hasAnyRealData = lPercent != null || rPercent != null || cPercent != null || battery?.overallPercent != null

    if (!hasAnyRealData && liveReport == null) {
        Text(
            text = "Battery levels appear here when a connected device reports them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val lChg = liveReport?.leftCharging ?: (leftReading?.isCharging == true)
    val rChg = liveReport?.rightCharging ?: (rightReading?.isCharging == true)
    val cChg = liveReport?.caseCharging ?: (caseReading?.isCharging == true)
    val caseIsLive = liveReport?.caseIsLive ?: (cPercent != null)

    Column(modifier = Modifier.fillMaxWidth()) {
        AdaptiveBatteryRings(
            formFactor = formFactor,
            leftPercent = lPercent,
            rightPercent = rPercent,
            casePercent = cPercent,
            devicePercent = battery?.overallPercent,
            leftCharging = lChg,
            rightCharging = rChg,
            caseCharging = cChg,
            deviceCharging = false,
            caseIsLive = caseIsLive,
            lastKnown = lastKnown && liveReport == null,
            forceCombinedView = forceCombinedView,
            supportsCaseBatteryWhenUndocked = supportsCaseBatteryWhenUndocked,
            brandSchema = brandSchema,
            budsDisplay = batteryContract.budsDisplay,
            caseDisplay = batteryContract.caseDisplay,
            caseVisibility = batteryContract.caseVisibility,
            caseWhileBudsOut = batteryContract.caseWhileBudsOut,
            batteryTopology = batteryTopology,
            onChangeStyleClick = onChangeStyleClick,
            onToggleViewMode = onToggleViewMode,
        )

        if (lastKnown && liveReport == null) {
            LastKnownCaption()
        }

        AnimatedVisibility(
            visible = verifiedLayoutLabel != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            Text(
                text = verifiedLayoutLabel.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = OmniBrand.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
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

/** 100–51% green, 50–21% orange, 20–0% red, last-known neutral grey. */
private fun batteryRingColor(percent: Int, lastKnown: Boolean): Color = when {
    lastKnown -> OmniBrand.Disabled
    percent > 50 -> Color(0xFF22E55E)
    percent > 20 -> Color(0xFFFF9800)
    else -> Color(0xFFFF2222)
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
            color = batteryRingColor(percent, lastKnown),
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
private fun NoiseControlCircles(
    modes: List<String>,
    selectedMode: String?,
    interactive: Boolean,
    onSelect: (String) -> Unit,
    auditAddress: String? = null,
) {
    val effectiveSelected = selectedMode ?: "OFF"

    fun handleModeClick(mode: String) {
        if (!interactive) return
        // Tapping ANC or Transparency while it is already the active mode switches back
        // to Ambient (Off). That is only safe when the earbuds report their own mode:
        // the selected state is then the real state, so the tap cannot invert. On
        // devices without that read-back every button simply requests its own mode.
        val toggleToOff = auditAddress != null &&
            !mode.equals("OFF", ignoreCase = true) &&
            effectiveSelected.equals(mode, ignoreCase = true) &&
            com.omnibuds.android.hardware.engine.UniversalTwsEngine.hasVerifiedModeReadback(auditAddress)
        val targetMode = if (toggleToOff) "OFF" else mode
        if (auditAddress != null) {
            com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                stage = "CLICK",
                address = auditAddress,
                activeMode = effectiveSelected,
                clickedMode = mode,
                requestedMode = targetMode,
            )
        }
        onSelect(targetMode)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            val isSelected = when (mode) {
                "OFF" -> effectiveSelected.equals("OFF", ignoreCase = true) || effectiveSelected.isBlank()
                else -> effectiveSelected.equals(mode, ignoreCase = true)
            }

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                label = "circleScale_$mode",
            )
            val circleColor by animateColorAsState(
                targetValue = if (isSelected) OmniBrand.Accent else Color(0xFF1B1D22),
                animationSpec = tween(200),
                label = "circleColor_$mode",
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) Color.Black else Color(0xFF8E929B),
                animationSpec = tween(200),
                label = "iconTint_$mode",
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color(0xFF8E929B),
                animationSpec = tween(200),
                label = "labelColor_$mode",
            )

            val displayLabel = when (mode) {
                "ANC" -> "ANC"
                "OFF" -> "Ambient"
                "TRANSPARENCY" -> "Transparency"
                else -> prettyMode(mode)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = interactive,
                        onClick = { handleModeClick(mode) },
                    ),
            ) {
                // Individual Circle Button
                Surface(
                    shape = CircleShape,
                    color = circleColor,
                    border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF2E313A)),
                    shadowElevation = if (isSelected) 8.dp else 0.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                "ANC" -> OmniIcons.SoundWaves
                                "TRANSPARENCY" -> OmniIcons.PersonOutline
                                "OFF" -> OmniIcons.AmbientNormal
                                else -> OmniIcons.SoundWaves
                            },
                            contentDescription = displayLabel,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text switch / label placed under and outside the circle, in the middle
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Apple-grade squishy liquid toggle switch.
 * The thumb ball stretches/squeezes dynamically in motion, snaps to its destination
 * with a slight settling shake, and smoothly crossfades track and thumb colors.
 */
@Composable
private fun FluidSquishySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motionEnabled = rememberMotionEnabled()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motionEnabled) {
            spring(
                dampingRatio = 0.62f, // Subtle settling shake/bounce
                stiffness = 380f,
            )
        } else snap(),
        label = "squishySwitchProgress",
    )

    // Dynamic Squeeze: The ball stretches horizontally (1.30x) and squishes vertically (0.82x) in transit
    val velocityFactor = sin(progress.coerceIn(0f, 1f) * PI.toFloat())
    val scaleX = if (motionEnabled) (1f + velocityFactor * 0.30f) else 1f
    val scaleY = if (motionEnabled) (1f - velocityFactor * 0.18f) else 1f

    val trackColor by animateColorAsState(
        targetValue = if (checked) OmniBrand.Accent else Color(0xFF282A31),
        animationSpec = tween(260),
        label = "switchTrackColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) OmniBrand.Accent else Color(0xFF4E5360),
        animationSpec = tween(260),
        label = "switchBorderColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF0A0A0A) else Color(0xFFF4F5F7),
        animationSpec = tween(260),
        label = "switchThumbColor",
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(BorderStroke(1.2.dp, borderColor), RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbOffsetDp = 3.dp + (22.dp * progress)

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(24.dp)
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                }
                .shadow(elevation = if (checked) 2.dp else 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor),
        )
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
    val isToggle = control.featureId == "spatial_audio" ||
        control.featureId == "game_mode" ||
        control.featureId == "volume_enhancer"

    var localToggleState by remember(control.currentValue) {
        mutableStateOf(control.currentValue.equals("ON", ignoreCase = true))
    }
    val isToggleOn = if (isToggle) localToggleState else control.currentValue.equals("ON", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, Color(0xFF26282E)),
        onClick = {
            if (isInteractive) {
                if (isToggle) {
                    val nextState = !localToggleState
                    localToggleState = nextState
                    onFeatureSelected(control.featureId, if (nextState) "ON" else "OFF")
                } else {
                    showDialog = true
                }
            }
        },
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
                    "game_mode" -> OmniIcons.Bolt
                    "volume_enhancer" -> OmniIcons.SoundWaves
                    "wear_detection" -> OmniIcons.BudsPair
                    "multipoint" -> OmniIcons.Headphones
                    else -> OmniIcons.Sliders
                },
                contentDescription = null,
                tint = if (isToggle && !isToggleOn) Color(0xFF8E929B) else OmniBrand.Accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (control.featureId) {
                        "eq_preset" -> "Equalizer"
                        "game_mode" -> "Game Mode (Low Latency)"
                        "volume_enhancer" -> "Volume Enhancer"
                        "spatial_audio" -> "Spatial Audio"
                        else -> control.displayName
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        !connected -> "Connect earbuds to enable"
                        control.featureId == "spatial_audio" -> if (isToggleOn) "3D Spatial Surround • Enabled" else "Standard Stereo • Off"
                        control.featureId == "game_mode" -> if (isToggleOn) "50ms Ultra-Low Latency • Active" else "Standard Latency • Off"
                        control.featureId == "volume_enhancer" -> if (isToggleOn) "Safe Limiter Boost (+5.0dB) • On" else "Standard Volume • Off"
                        control.featureId == "eq_preset" -> "Preset: ${prettyMode(control.currentValue ?: "FLAT")}"
                        control.currentValue != null -> prettyMode(control.currentValue!!)
                        else -> "Standard"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isToggle && isInteractive) {
                FluidSquishySwitch(
                    checked = isToggleOn,
                    onCheckedChange = { checked ->
                        localToggleState = checked
                        onFeatureSelected(control.featureId, if (checked) "ON" else "OFF")
                    },
                )
            } else if (isInteractive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF26282E),
                    border = BorderStroke(1.dp, Color(0xFF383B44)),
                ) {
                    Text(
                        text = if (control.featureId == "eq_preset") "Presets ▾" else "Configure ▾",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = OmniBrand.Accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (showDialog && !isToggle) {
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
            val options = control.availableModes.map { it to prettyMode(it) }
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
    var isPresetTransition by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(240, easing = LinearOutSlowInEasing)) +
                scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.90f),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF16171B),
                border = BorderStroke(1.dp, Color(0xFF2E3138)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                OmniIcons.Sliders,
                                contentDescription = null,
                                tint = OmniBrand.Accent,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Universal Equalizer",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                ),
                                color = Color.White,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF8E929B),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                "Instant Presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniBrand.Accent,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                presets.take(3).forEach { (key, label) ->
                                    val sel = selectedPreset == key
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) OmniBrand.Accent else Color(0xFF24262C),
                                        border = BorderStroke(1.dp, if (sel) OmniBrand.Accent else Color(0xFF33363F)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedPreset = key
                                                val newGains = presetGains[key]!!.copyOf()
                                                isPresetTransition = true
                                                bandGains = newGains
                                                onPresetSelected(key)
                                                scope.launch {
                                                    delay(350)
                                                    isPresetTransition = false
                                                }
                                            },
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            ),
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
                                        color = if (sel) OmniBrand.Accent else Color(0xFF24262C),
                                        border = BorderStroke(1.dp, if (sel) OmniBrand.Accent else Color(0xFF33363F)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedPreset = key
                                                val newGains = presetGains[key]!!.copyOf()
                                                isPresetTransition = true
                                                bandGains = newGains
                                                onPresetSelected(key)
                                                scope.launch {
                                                    delay(350)
                                                    isPresetTransition = false
                                                }
                                            },
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            ),
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
                            Text(
                                "10-Band Sliders (-10dB to +10dB)",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniBrand.Accent,
                            )
                        }

                        items(10) { i ->
                            val targetGain = bandGains[i]
                            val animatedGain by animateFloatAsState(
                                targetValue = targetGain,
                                animationSpec = if (isPresetTransition) {
                                    tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                } else {
                                    snap()
                                },
                                label = "eqBand_$i",
                            )

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
                                    value = animatedGain,
                                    onValueChange = { newVal ->
                                        isPresetTransition = false
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
                                    text = "${if (animatedGain > 0.05f) "+" else ""}${String.format(java.util.Locale.US, "%.1f", animatedGain)}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (animatedGain.absoluteValue > 0.05f) OmniBrand.Accent else Color.Gray,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Done", color = OmniBrand.Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
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
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16171B))
                    .border(BorderStroke(1.dp, Color(0xFF2C2F36)), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Left", "Right").forEach { bud ->
                    val isSelected = selectedBud == bud
                    Surface(
                        onClick = { selectedBud = bud },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected) OmniBrand.Accent else Color.Transparent,
                        contentColor = if (isSelected) Color.Black else Color(0xFFA1A1AA),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = if (bud == "Left") OmniIcons.BudLeft else OmniIcons.BudRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) Color.Black else Color(0xFFA1A1AA),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$bud Earbud",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
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

// -- Press-and-hold cycle card -------------------------------------------------------
// The earbuds keep this setting inside themselves. The card shows what they report and
// saves nothing on the phone, so the choice follows the buds to any other phone.

@Composable
private fun HoldCycleCard(
    currentMask: Int?,
    ancBit: Int,
    transparencyBit: Int,
    ambientBit: Int,
    enabled: Boolean,
    saving: Boolean,
    message: String?,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(
        (ancBit or transparencyBit) to "ANC and Transparency",
        (ancBit or ambientBit) to "ANC and Ambient (off)",
        (transparencyBit or ambientBit) to "Transparency and Ambient (off)",
    )
    val matched = options.any { it.first == currentMask }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Press and hold on the earbuds",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Holding an earbud switches between these two. The choice is stored inside the earbuds, so it stays the same on any phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (mask, label) ->
                    val selected = mask == currentMask
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) OmniBrand.Accent else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) OmniBrand.Accent else OmniBrand.Border),
                        onClick = { if (!selected) onSelect(mask) },
                        enabled = enabled && !selected,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (selected) OmniBrand.OnAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            val status = when {
                saving -> "Saving to the earbuds and checking it..."
                currentMask == null -> "Waiting for the earbuds to report this setting."
                !matched -> "The earbuds are set to a different combination (0x%X).".format(currentMask)
                else -> message
            }
            if (status != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    "ANC" -> "ANC"
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

@Composable
fun NoiseControlDiagnosticDrawer(
    deviceAddress: String,
    device: BluetoothDevice?,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val diagStates by com.omnibuds.android.hardware.protocol.EarbudProtocolManager.noiseControlDiagnosticStates.collectAsState()
    val diagState = diagStates[deviceAddress] ?: com.omnibuds.android.hardware.engine.NoiseControlDiagnosticState()
    val auditEntries by com.omnibuds.android.hardware.protocol.AncAuditLog.entries.collectAsState()
    val auditTimeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF16181D),
        border = BorderStroke(1.dp, if (expanded) OmniBrand.Accent.copy(alpha = 0.5f) else Color(0xFF2E313A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Header Row (Clickable toggle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Icon(
                    imageVector = OmniIcons.SoundWaves,
                    contentDescription = null,
                    tint = OmniBrand.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Test & Verify Noise Control Modes",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "▲ Collapse" else "▼ Diagnostic",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OmniBrand.Accent,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF2E313A), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Telemetry Card
                Text(
                    text = "LIVE GATT TRANSMISSION TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E92A0),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F1013),
                    border = BorderStroke(1.dp, Color(0xFF242730)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Outgoing Hex: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = diagState.lastOutgoingHex,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                color = OmniBrand.Accent,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Write Latency: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = "${diagState.lastLatencyMs} ms (<30ms NO_RESPONSE)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (diagState.lastLatencyMs in 1..40) Color(0xFF55FF55) else Color(0xFFFFCC00),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Write Count: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = "${diagState.writeCount} (Strict +1 deduplication, never +2)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Active Off-Byte: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = "0x%02X".format(diagState.activeOffByte),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                color = OmniBrand.Accent,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Confirmed Mode: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Text(
                                text = diagState.confirmedMode,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = OmniBrand.Accent,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Click -> active mode -> requested mode -> sent bytes -> ACK.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ANC CLICK / SIGNAL AUDIT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF8E92A0),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = OmniBrand.Accent,
                        modifier = Modifier.clickable {
                            com.omnibuds.android.hardware.protocol.AncAuditLog.clear()
                        },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F1013),
                    border = BorderStroke(1.dp, Color(0xFF242730)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        if (auditEntries.isEmpty()) {
                            Text(
                                text = "Tap ANC, Ambient or Transparency to record a click.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E92A0),
                            )
                        } else {
                            auditEntries.takeLast(30).reversed().forEach { entry ->
                                val stageColor = when (entry.stage) {
                                    "CLICK" -> OmniBrand.Accent
                                    "SEND" -> Color(0xFF55D0FF)
                                    "ACK" -> Color(0xFF22E55E)
                                    "NOTIFY", "CONFIRMED" -> Color(0xFFFFCC00)
                                    "REJECTED", "ENGINE_SKIP" -> Color(0xFFFF5555)
                                    else -> Color.White
                                }
                                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Row {
                                        Text(
                                            text = auditTimeFormat.format(java.util.Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = Color(0xFF8E92A0),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.stage,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = stageColor,
                                        )
                                    }
                                    Text(
                                        text = buildString {
                                            entry.activeMode?.let { append("active=$it ") }
                                            entry.clickedMode?.let { append("clicked=$it ") }
                                            entry.requestedMode?.let { append("requested=$it ") }
                                        }.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                    )
                                    entry.sentHex?.let {
                                        Text(
                                            text = "sent=$it",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = Color(0xFF55D0FF),
                                        )
                                    }
                                    entry.mapping?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8E92A0),
                                        )
                                    }
                                    entry.responseHex?.let {
                                        Text(
                                            text = "rx=$it",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = Color(0xFF22E55E),
                                        )
                                    }
                                    entry.note?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFCC00),
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF242730), thickness = 1.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3-Variant "Off-Byte Tester"
                Text(
                    text = "3-VARIANT OFF-BYTE RESET TESTER (BES DSP MICS)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E92A0),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If Normal/Off mode plays a chime but doesn't turn off transparency or ANC mics on realme Buds T300, tap each candidate below to hear which resets the hardware mic registers, then lock it in permanently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B3C0),
                )
                Spacer(modifier = Modifier.height(10.dp))

                val variants = listOf(
                    Triple("Variant 1 (0x00, 0x00)", 0x00.toByte(), "Standard HeyMelody / BES zero-bypass"),
                    Triple("Variant 2 (0x02, 0x00)", 0x02.toByte(), "Alternative BES secondary register"),
                    Triple("Variant 3 (0x03, 0x00)", 0x03.toByte(), "Direct hardware mic teardown reset"),
                )

                variants.forEach { (title, byteVal, desc) ->
                    val isCurrent = diagState.activeOffByte == byteVal
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F1013),
                        border = BorderStroke(1.dp, if (isCurrent) OmniBrand.Accent else Color(0xFF242730)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCurrent) OmniBrand.Accent else Color.White,
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = OmniBrand.Accent.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = "LOCKED",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = OmniBrand.Accent,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8E92A0),
                                    )
                                }

                                Row {
                                    // Test button
                                    Button(
                                        onClick = {
                                            if (device != null) {
                                                scope.launch {
                                                    val ok = com.omnibuds.android.hardware.protocol.EarbudProtocolManager.testOffByteVariant(device, byteVal)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        if (ok) "Sent 0x%02X, 0x00 reset frame".format(byteVal) else "Failed to send test frame",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "Device not connected", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = connected,
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2E313A),
                                            contentColor = Color.White,
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text("Test", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Lock In button
                                    Button(
                                        onClick = {
                                            com.omnibuds.android.hardware.protocol.EarbudProtocolManager.lockInOffByteVariant(deviceAddress, byteVal)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Locked in 0x%02X as permanent Off/Normal mode!".format(byteVal),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        enabled = connected,
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = if (isCurrent) Color(0xFF333333) else OmniBrand.Accent,
                                            contentColor = if (isCurrent) Color.White else Color.Black,
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text("Lock In", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

