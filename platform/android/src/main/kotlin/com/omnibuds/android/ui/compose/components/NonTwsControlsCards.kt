package com.omnibuds.android.ui.compose.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnibuds.android.hardware.engine.BinauralCrossfeedMode
import com.omnibuds.android.hardware.engine.NonTwsHardwareController
import com.omnibuds.android.ui.compose.model.AudioFormFactor
import com.omnibuds.android.ui.compose.model.AudioFormFactorResolver
import com.omnibuds.android.ui.compose.theme.OmniBrand
import kotlinx.coroutines.launch

/**
 * Dialog to select and override the hardware form factor with live vector previews.
 */
@Composable
fun FormFactorPickerDialog(
    currentFactor: AudioFormFactor,
    deviceId: String,
    onDismiss: () -> Unit,
    onSelect: (AudioFormFactor) -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Hardware Form Factor",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                item {
                    Text(
                        text = "Choose the industrial silhouette and DSP control matrix for this device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                val factors = AudioFormFactor.entries
                items(factors.size) { idx ->
                    val factor = factors[idx]
                    val isSelected = currentFactor == factor
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) OmniBrand.Accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else OmniBrand.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                AudioFormFactorResolver.setOverriddenFormFactor(context, deviceId, factor)
                                onSelect(factor)
                                onDismiss()
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp),
                        ) {
                            AdaptiveAudioGlyph(
                                formFactor = factor,
                                side = GlyphSide.LEFT,
                                size = 42.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = factor.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) OmniBrand.Accent else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = when (factor) {
                                        AudioFormFactor.STEM_IN_EAR -> "realme Buds T300, AirPods Pro, OnePlus Buds Pro"
                                        AudioFormFactor.STEM_HALF_IN_EAR -> "AirPods 3/4, Nothing Ear stick"
                                        AudioFormFactor.BUD_BEAN_IN_EAR -> "Galaxy Buds, Sony WF-1000XM5, Pixel Buds"
                                        AudioFormFactor.BUD_OPEN_RING -> "Sony LinkBuds, Galaxy Buds Live"
                                        AudioFormFactor.OPEN_EAR_HOOK -> "Powerbeats Pro, Nothing Ear Open, Soundgear"
                                        AudioFormFactor.OPEN_EAR_CLIP -> "Bose Ultra Open, Huawei FreeClip"
                                        AudioFormFactor.NECKBAND -> "OnePlus Bullets Wireless, realme Buds Wireless"
                                        AudioFormFactor.BONE_CONDUCTION -> "Shokz OpenRun Pro, Suunto Wing"
                                        AudioFormFactor.HEADSET_OVER_ON_EAR -> "Sony WH-1000XM5, AirPods Max, Bose QC"
                                        AudioFormFactor.HEADSET_OPEN_BACK -> "HiFiMAN Deva Pro, Grado Open-Back"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OmniBrand.Accent,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AudioFormFactorResolver.clearOverriddenFormFactor(context, deviceId)
                    val resolved = AudioFormFactorResolver.resolve(context, deviceId, null)
                    onSelect(resolved)
                    onDismiss()
                },
            ) { Text("Auto-Detect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Category C: Open-Acoustics & Anti-Leakage Studio Card.
 * Replaces ANC for Open-Back, Open-Ear, and Bone Conduction devices.
 */
@Composable
fun OpenAcousticsStudioCard(
    formFactor: AudioFormFactor,
    bassLevel: Int,
    antiLeakageOn: Boolean,
    crossfeedMode: BinauralCrossfeedMode,
    boneClarifierOn: Boolean,
    interactive: Boolean,
    onBassLevelChange: (Int) -> Unit,
    onAntiLeakageToggle: (Boolean) -> Unit,
    onCrossfeedChange: (BinauralCrossfeedMode) -> Unit,
    onBoneClarifierToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = OmniIcons.Sparkle,
                    contentDescription = null,
                    tint = OmniBrand.Accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Open-Acoustics & Anti-Leakage Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Acoustic physics compensation for open soundstage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Dipole Open-Air Bass Restorer (25Hz–110Hz)
            Text(
                text = "Dipole Open-Air Bass Restorer: ${if (bassLevel == 0) "Off" else "Level $bassLevel"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Harmonic missing-fundamental compensation for unsealed acoustic air-loss",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = bassLevel.toFloat(),
                onValueChange = { onBassLevelChange(it.toInt()) },
                valueRange = 0f..5f,
                steps = 4,
                enabled = interactive,
                colors = SliderDefaults.colors(thumbColor = OmniBrand.Accent, activeTrackColor = OmniBrand.Accent),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Anti-Sound-Leakage Private / Whisper Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anti-Sound-Leakage (Whisper Mode)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "Transient peak compressor prevents sound leaking to bystanders in quiet rooms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = antiLeakageOn,
                    onCheckedChange = onAntiLeakageToggle,
                    enabled = interactive,
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            // 3. Natural Binaural Crossfeed Matrix
            if (formFactor == AudioFormFactor.HEADSET_OPEN_BACK || formFactor == AudioFormFactor.HEADSET_OVER_ON_EAR) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Binaural Crossfeed Matrix (Natural Soundstage)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Blends hard-panned stereo into a natural speaker-like soundstage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BinauralCrossfeedMode.entries.forEach { mode ->
                        val isSelected = crossfeedMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) OmniBrand.Accent else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else OmniBrand.Border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = interactive) { onCrossfeedChange(mode) },
                        ) {
                            Text(
                                text = mode.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 4. Bone Conduction Vocal & Treble Clarifier
            if (formFactor == AudioFormFactor.BONE_CONDUCTION) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cheekbone Vibration Clarifier",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Eliminates painful 60Hz cheek tickle while boosting vocal intelligibility",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = boneClarifierOn,
                        onCheckedChange = onBoneClarifierToggle,
                        enabled = interactive,
                        colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                    )
                }
            }
        }
    }
}

/**
 * Category A: Neckband Magnetic Hall-Switch & Auto-Resume Card.
 */
@Composable
fun MagneticHallSwitchCard(
    autoPlayOnSeparate: Boolean,
    instantPauseOnClasp: Boolean,
    interactive: Boolean,
    onAutoPlayToggle: (Boolean) -> Unit,
    onInstantPauseToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Magnetic Hall-Switch Auto-Control",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Play on Magnetic Separation",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Resumes audio within 400ms of uncoupling earbuds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoPlayOnSeparate,
                    onCheckedChange = onAutoPlayToggle,
                    enabled = interactive,
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Pause on Magnetic Clasp",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Snapping buds together instantly halts media and enters standby",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = instantPauseOnClasp,
                    onCheckedChange = onInstantPauseToggle,
                    enabled = interactive,
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }
        }
    }
}

/**
 * Category A: Neckband Collar Vibration Alert Manager Card.
 */
@Composable
fun CollarVibrationCard(
    vibrationOn: Boolean,
    interactive: Boolean,
    onVibrationToggle: (Boolean) -> Unit,
    onTestPulse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Collar Vibration Alerts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Vibrates the neckband collar during incoming phone calls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onTestPulse,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Test Pulse", style = MaterialTheme.typography.labelSmall)
                }
            }
            Switch(
                checked = vibrationOn,
                onCheckedChange = onVibrationToggle,
                enabled = interactive,
                colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
            )
        }
    }
}

/**
 * Category A: Inline Physical 3-Button Rocker & Key Mapper Card.
 */
@Composable
fun InlineRockerControllerCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Inline Rocker Physical Controller",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Volume Up (+)", style = MaterialTheme.typography.bodyMedium)
                Text("Short Press: Vol+ / Hold: Next", style = MaterialTheme.typography.bodySmall, color = OmniBrand.Accent)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Multi-Function Button (●)", style = MaterialTheme.typography.bodyMedium)
                Text("1x: Play/Pause | 2x: Next | 3x: Prev", style = MaterialTheme.typography.bodySmall, color = OmniBrand.Accent)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Volume Down (-)", style = MaterialTheme.typography.bodyMedium)
                Text("Short Press: Vol- / Hold: Prev", style = MaterialTheme.typography.bodySmall, color = OmniBrand.Accent)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Dedicated ANC / Quick Switch Key", style = MaterialTheme.typography.bodyMedium)
                Text("Toggle ANC / Double-Press Quick Switch", style = MaterialTheme.typography.bodySmall, color = OmniBrand.Accent)
            }
        }
    }
}

/**
 * Category B: Over-Ear 20-Level Ambient Sound & Acoustic Clamping Optimizer Card.
 */
@Composable
fun OverEarAmbientAndClampingCard(
    ambientLevel: Int,
    voiceFocusOn: Boolean,
    interactive: Boolean,
    onAmbientLevelChange: (Int) -> Unit,
    onVoiceFocusToggle: (Boolean) -> Unit,
    onRunClampingOptimizer: () -> Unit,
    optimizerRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "20-Level Ambient Passthrough & Pressure Optimizer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ambient Passthrough Level: $ambientLevel / 20",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Slider(
                value = ambientLevel.toFloat(),
                onValueChange = { onAmbientLevelChange(it.toInt()) },
                valueRange = 1f..20f,
                steps = 18,
                enabled = interactive,
                colors = SliderDefaults.colors(thumbColor = OmniBrand.Accent, activeTrackColor = OmniBrand.Accent),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = voiceFocusOn,
                    onCheckedChange = onVoiceFocusToggle,
                    enabled = interactive,
                    colors = CheckboxDefaults.colors(checkedColor = OmniBrand.Accent),
                )
                Text(
                    text = "Voice Passthrough (Focus on Human Speech)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRunClampingOptimizer,
                enabled = interactive && !optimizerRunning,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = OmniBrand.Accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (optimizerRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calibrating Earcup Clamping Force...")
                } else {
                    Text("Run Acoustic Pressure / Clamping Optimizer")
                }
            }
        }
    }
}

/**
 * Category B: Speak-to-Chat Card.
 */
@Composable
fun SpeakToChatCard(
    enabled: Boolean,
    sensitivity: NonTwsHardwareController.SpeakToChatSensitivity,
    pauseTime: NonTwsHardwareController.SpeakToChatPauseTime,
    interactive: Boolean,
    onToggle: (Boolean) -> Unit,
    onSensitivityChange: (NonTwsHardwareController.SpeakToChatSensitivity) -> Unit,
    onPauseTimeChange: (NonTwsHardwareController.SpeakToChatPauseTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Speak-to-Chat",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Automatically pauses audio & enables ambient mode when you start talking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = interactive,
                    colors = SwitchDefaults.colors(checkedThumbColor = OmniBrand.Accent),
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Microphone Sensitivity",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NonTwsHardwareController.SpeakToChatSensitivity.entries.forEach { s ->
                        val isSelected = sensitivity == s
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) OmniBrand.Accent else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else OmniBrand.Border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = interactive) { onSensitivityChange(s) },
                        ) {
                            Text(
                                text = s.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
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

/**
 * Category B: Auto-Standby Timer Card.
 */
@Composable
fun AutoStandbyTimerCard(
    currentTimer: NonTwsHardwareController.AutoStandbyTime,
    interactive: Boolean,
    onTimerChange: (NonTwsHardwareController.AutoStandbyTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Auto Power-Off Standby Timer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Disconnects and powers down device after inactivity to preserve battery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NonTwsHardwareController.AutoStandbyTime.entries.take(4).forEach { t ->
                    val isSelected = currentTimer == t
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) OmniBrand.Accent else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isSelected) OmniBrand.Accent else OmniBrand.Border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = interactive) { onTimerChange(t) },
                    ) {
                        Text(
                            text = if (t.minutes == 0) "Never" else "${t.minutes}m",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

