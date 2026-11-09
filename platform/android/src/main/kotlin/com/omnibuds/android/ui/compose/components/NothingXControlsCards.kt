package com.omnibuds.android.ui.compose.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibuds.android.hardware.capability.StrictBrandProfile
import com.omnibuds.android.ui.compose.theme.OmniBrand

/**
 * 1:1 Nothing X Official Noise Control Card.
 * Modes: Active Noise Cancellation, Transparency Mode, Off.
 * ANC Sub-levels: High (42dB), Mid, Low, Adaptive.
 */
@Composable
fun NothingXNoiseControlCard(
    currentMode: String,
    currentSubLevel: String = "High (42dB)",
    interactive: Boolean,
    onSelectMode: (String) -> Unit,
    onSelectSubLevel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        "Active Noise Cancellation" to OmniIcons.SoundWaves,
        "Transparency Mode" to OmniIcons.Sparkle,
        "Off" to OmniIcons.Sliders,
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Noise Control",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Main 3-mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16171B))
                    .border(BorderStroke(1.dp, Color(0xFF2C2F36)), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                modes.forEach { (modeLabel, icon) ->
                    val isSelected = currentMode.equals(modeLabel, ignoreCase = true) ||
                        (modeLabel == "Active Noise Cancellation" && (currentMode.contains("ANC", ignoreCase = true) || currentMode.contains("HIGH", ignoreCase = true))) ||
                        (modeLabel == "Transparency Mode" && currentMode.contains("TRANS", ignoreCase = true)) ||
                        (modeLabel == "Off" && (currentMode.equals("OFF", ignoreCase = true) || currentMode.equals("NORMAL", ignoreCase = true)))

                    Surface(
                        onClick = { if (interactive) onSelectMode(modeLabel) },
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
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) Color.Black else Color(0xFFA1A1AA),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (modeLabel) {
                                    "Active Noise Cancellation" -> "ANC"
                                    "Transparency Mode" -> "Transparency"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // ANC Sub-levels (High 42dB, Mid, Low, Adaptive)
            val isAncActive = currentMode.contains("ANC", ignoreCase = true) ||
                currentMode.contains("HIGH", ignoreCase = true) ||
                currentMode.contains("ACTIVE", ignoreCase = true)

            AnimatedVisibility(
                visible = isAncActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Noise Cancellation Level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val subLevels = listOf("High (42dB)", "Mid", "Low", "Adaptive")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        subLevels.forEach { lvl ->
                            val selected = currentSubLevel.equals(lvl, ignoreCase = true) ||
                                (lvl.startsWith("High") && (currentSubLevel.contains("High", ignoreCase = true) || currentSubLevel.contains("42", ignoreCase = true)))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color(0xFF2E323D) else Color(0xFF1B1D22),
                                border = BorderStroke(1.dp, if (selected) OmniBrand.Accent else Color(0xFF2C2F36)),
                                onClick = { if (interactive) onSelectSubLevel(lvl) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = lvl.replace(" (42dB)", ""),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (selected) OmniBrand.Accent else Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Nothing X Equalizer Card.
 * Presets: Dirac Opteo, Balanced, More Bass, More Treble, Voice, Custom.
 * Interactive 3-band Bass / Mid / Treble tuning for Custom.
 */
@Composable
fun NothingXEqualizerCard(
    currentPreset: String,
    interactive: Boolean,
    onSelectPreset: (String) -> Unit,
    on3BandGainsChanged: (bass: Int, mid: Int, treble: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = listOf("Dirac Opteo", "Balanced", "More Bass", "More Treble", "Voice", "Custom")
    var bassGain by remember { mutableIntStateOf(0) }
    var midGain by remember { mutableIntStateOf(0) }
    var trebleGain by remember { mutableIntStateOf(0) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Dirac Opteo HD signature tuning",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniBrand.Accent,
                    )
                }
                Icon(
                    imageVector = OmniIcons.Sliders,
                    contentDescription = null,
                    tint = OmniBrand.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets grid (2 rows of 3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { p ->
                            val isSelected = currentPreset.equals(p, ignoreCase = true) ||
                                (p == "Dirac Opteo" && (currentPreset.equals("DIRAC", ignoreCase = true) || currentPreset.equals("OPTEO", ignoreCase = true)))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) OmniBrand.Accent else Color(0xFF1B1D22),
                                border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else Color(0xFF2C2F36)),
                                onClick = { if (interactive) onSelectPreset(p) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = p,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                    color = if (isSelected) Color.Black else Color(0xFFD1D1D6),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Interactive 3-band Custom Sliders (Bass / Mid / Treble)
            val isCustom = currentPreset.equals("Custom", ignoreCase = true)
            AnimatedVisibility(
                visible = isCustom,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Custom 3-Band Tuning (-6dB to +6dB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    BandSlider("Bass", bassGain) {
                        bassGain = it
                        on3BandGainsChanged(bassGain, midGain, trebleGain)
                    }
                    BandSlider("Mid", midGain) {
                        midGain = it
                        on3BandGainsChanged(bassGain, midGain, trebleGain)
                    }
                    BandSlider("Treble", trebleGain) {
                        trebleGain = it
                        on3BandGainsChanged(bassGain, midGain, trebleGain)
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = -6f..6f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = OmniBrand.Accent,
                activeTrackColor = OmniBrand.Accent,
                inactiveTrackColor = Color(0xFF2C2F36),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${if (value > 0) "+$value" else "$value"}dB",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = OmniBrand.Accent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/**
 * 1:1 Ultra Bass Technology 2.0 Card.
 * Toggle Switch (On/Off) + 5-Level Intensity Slider (Level 1 to Level 5).
 */
@Composable
fun NothingXUltraBassCard(
    enabled: Boolean,
    level: Int,
    interactive: Boolean,
    onToggle: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = OmniIcons.SoundWaves,
                        contentDescription = null,
                        tint = if (enabled) OmniBrand.Accent else Color.Gray,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ultra Bass Technology 2.0",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (enabled) "Real-time sub-harmonic DSP • Level $level" else "Sub-harmonic enhancer • Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { if (interactive) onToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Bass Intensity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Level $level",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OmniBrand.Accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (1..5).forEach { lvl ->
                            val isSelected = level == lvl
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) OmniBrand.Accent else Color(0xFF1B1D22),
                                border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else Color(0xFF2C2F36)),
                                onClick = { if (interactive) onLevelChange(lvl) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "$lvl",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) Color.Black else Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 CMF Buds 1 Official Touch Controls Card.
 * Gestures: Double Tap, Triple Tap, Tap & Hold, Double Tap & Hold.
 */
@Composable
fun NothingXTouchControlsCard(
    deviceId: String,
    interactive: Boolean,
    onGestureChanged: (bud: String, gesture: String, action: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(deviceId) {
        context.getSharedPreferences("omni_touch_controls_$deviceId", Context.MODE_PRIVATE)
    }

    var selectedBud by remember { mutableStateOf("Left") }
    var activeGestureToEdit by remember { mutableStateOf<String?>(null) }

    fun getAction(bud: String, gesture: String): String {
        val key = "${bud}_$gesture"
        return prefs.getString(key, null) ?: when (gesture) {
            "double_tap" -> if (bud == "Left") "PLAY_PAUSE" else "SKIP_FORWARD"
            "triple_tap" -> if (bud == "Left") "SKIP_BACK" else "VOICE_ASSISTANT"
            "tap_hold" -> "SWITCH_ANC_TRANSPARENCY"
            "double_tap_hold" -> if (bud == "Left") "VOLUME_DOWN" else "VOLUME_UP"
            else -> "PLAY_PAUSE"
        }
    }

    fun prettyAction(action: String): String = when (action) {
        "PLAY_PAUSE" -> "Play / Pause"
        "SKIP_FORWARD" -> "Skip Forward"
        "SKIP_BACK" -> "Skip Back"
        "VOICE_ASSISTANT" -> "Voice Assistant"
        "SWITCH_ANC_TRANSPARENCY" -> "Switch ANC / Transparency"
        "DECLINE_CALL" -> "Decline Incoming Call"
        "VOLUME_UP" -> "Volume Up"
        "VOLUME_DOWN" -> "Volume Down"
        "LOW_LAG_MODE" -> "Low Lag Mode"
        "NONE" -> "No Action"
        else -> action
    }

    val gestures = listOf(
        "double_tap" to "Double Tap",
        "triple_tap" to "Triple Tap",
        "tap_hold" to "Tap & Hold",
        "double_tap_hold" to "Double Tap & Hold",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Touch Controls",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(14.dp))

            // Gesture rows
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                gestures.forEach { (gKey, gLabel) ->
                    val currentAct = getAction(selectedBud, gKey)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFF2C2F36)),
                        onClick = { if (interactive) activeGestureToEdit = gKey },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Text(
                                    text = gLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = prettyAction(currentAct),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OmniBrand.Accent,
                                )
                            }
                            Text(
                                text = "Edit ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for choosing action
    activeGestureToEdit?.let { gestureKey ->
        val gestureDef = com.omnibuds.android.hardware.capability.StrictBrandProfileDatabase.CMF_BUDS_1_GESTURES
            .firstOrNull { it.gestureId == gestureKey }
        val actions = gestureDef?.allowedActions ?: listOf(
            "PLAY_PAUSE" to "Play/Pause",
            "SKIP_FORWARD" to "Skip Forward",
            "SKIP_BACK" to "Skip Back",
            "VOICE_ASSISTANT" to "Voice Assistant",
            "NONE" to "No Action",
        )

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { activeGestureToEdit = null },
            containerColor = Color(0xFF1E1E24),
            titleContentColor = Color.White,
            title = {
                Text(
                    text = "$selectedBud Earbud: ${gestures.firstOrNull { it.first == gestureKey }?.second}",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (actKey, actLabel) ->
                        val isSelected = getAction(selectedBud, gestureKey) == actKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) OmniBrand.Accent else Color(0xFF2C2C34),
                            border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else Color(0xFF383B44)),
                            onClick = {
                                prefs.edit().putString("${selectedBud}_$gestureKey", actKey).apply()
                                onGestureChanged(selectedBud, gestureKey, actKey)
                                activeGestureToEdit = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = actLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { activeGestureToEdit = null }) {
                    Text("Close", color = OmniBrand.Accent)
                }
            },
        )
    }
}

/**
 * 1:1 Nothing X Official Device Settings & Toggles Card.
 * Controls: In-Ear Detection, Low Lag Mode, Dual Connection, Find My Earbuds.
 */
@Composable
fun NothingXDeviceSettingsCard(
    profile: StrictBrandProfile,
    inEarDetectionOn: Boolean,
    lowLagModeOn: Boolean,
    dualConnectionOn: Boolean,
    interactive: Boolean,
    onInEarDetectionToggle: (Boolean) -> Unit,
    onLowLagModeToggle: (Boolean) -> Unit,
    onDualConnectionToggle: (Boolean) -> Unit,
    onRingEarbud: (bud: String, ring: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ringingLeft by remember { mutableStateOf(false) }
    var ringingRight by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Device Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // In-Ear Detection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = OmniIcons.BudsPair,
                        contentDescription = null,
                        tint = if (inEarDetectionOn) OmniBrand.Accent else Color.Gray,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "In-Ear Detection",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Auto play when worn / pause when removed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = inEarDetectionOn,
                    onCheckedChange = { if (interactive) onInEarDetectionToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            // Low Lag Mode (Nothing X official label)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = OmniIcons.Bolt,
                        contentDescription = null,
                        tint = if (lowLagModeOn) OmniBrand.Accent else Color.Gray,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Low Lag Mode",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Reduces audio latency during gaming and videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = lowLagModeOn,
                    onCheckedChange = { if (interactive) onLowLagModeToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            // Dual Connection (Multipoint)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = OmniIcons.Headphones,
                        contentDescription = null,
                        tint = if (dualConnectionOn) OmniBrand.Accent else Color.Gray,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Dual Connection",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Connect to two devices simultaneously",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = dualConnectionOn,
                    onCheckedChange = { if (interactive) onDualConnectionToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            // Find My Earbuds
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "Find My Earbuds",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Play high-pitch locator chime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = !ringingLeft
                            ringingLeft = next
                            onRingEarbud("Left", next)
                        },
                        enabled = interactive,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (ringingLeft) OmniBrand.Accent else OmniBrand.Border),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (ringingLeft) "Stop Left" else "Ring Left",
                            color = if (ringingLeft) OmniBrand.Accent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val next = !ringingRight
                            ringingRight = next
                            onRingEarbud("Right", next)
                        },
                        enabled = interactive,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (ringingRight) OmniBrand.Accent else OmniBrand.Border),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (ringingRight) "Stop Right" else "Ring Right",
                            color = if (ringingRight) OmniBrand.Accent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
