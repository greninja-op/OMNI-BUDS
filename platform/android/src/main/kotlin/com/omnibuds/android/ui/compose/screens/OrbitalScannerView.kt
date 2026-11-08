package com.omnibuds.android.ui.compose.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibuds.android.R
import com.omnibuds.android.hardware.pairing.BondingStage
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.ui.compose.components.AdaptiveAudioGlyph
import com.omnibuds.android.ui.compose.components.ErrorBanner
import com.omnibuds.android.ui.compose.components.GlyphSide
import com.omnibuds.android.ui.compose.components.OmniIcons
import com.omnibuds.android.ui.compose.components.rememberMotionEnabled
import com.omnibuds.android.ui.compose.model.AudioFormFactor
import com.omnibuds.android.ui.compose.model.AudioFormFactorResolver
import com.omnibuds.android.ui.compose.theme.OmniBrand
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deterministic stardust micro-particle representation drifting inside the conic radar wake.
 */
private data class FairyParticle(
    val radiusFraction: Float,
    val deltaAngleDeg: Float,
    val sizePx: Float,
    val baseAlpha: Float,
)

private val FAIRY_DUST_PARTICLES = listOf(
    FairyParticle(0.32f, -8f, 2.5f, 0.90f),
    FairyParticle(0.48f, -14f, 3.2f, 0.85f),
    FairyParticle(0.62f, -22f, 2.0f, 0.75f),
    FairyParticle(0.78f, -28f, 3.8f, 0.70f),
    FairyParticle(0.38f, -36f, 1.8f, 0.60f),
    FairyParticle(0.55f, -44f, 2.8f, 0.50f),
    FairyParticle(0.70f, -52f, 2.0f, 0.40f),
    FairyParticle(0.85f, -60f, 3.5f, 0.35f),
    FairyParticle(0.42f, -12f, 1.6f, 0.80f),
    FairyParticle(0.58f, -18f, 2.6f, 0.82f),
    FairyParticle(0.73f, -26f, 1.8f, 0.68f),
    FairyParticle(0.88f, -34f, 3.0f, 0.60f),
    FairyParticle(0.35f, -42f, 2.4f, 0.52f),
    FairyParticle(0.65f, -50f, 1.9f, 0.42f),
    FairyParticle(0.80f, -58f, 2.8f, 0.32f),
    FairyParticle(0.50f, -64f, 1.5f, 0.25f),
    FairyParticle(0.28f, -16f, 2.0f, 0.85f),
    FairyParticle(0.45f, -24f, 3.4f, 0.72f),
    FairyParticle(0.60f, -32f, 1.7f, 0.62f),
    FairyParticle(0.75f, -40f, 2.5f, 0.54f),
    FairyParticle(0.83f, -48f, 3.6f, 0.45f),
    FairyParticle(0.33f, -56f, 1.6f, 0.38f),
    FairyParticle(0.68f, -62f, 2.2f, 0.28f),
    FairyParticle(0.52f, -10f, 2.8f, 0.90f),
    FairyParticle(0.40f, -30f, 2.1f, 0.65f),
    FairyParticle(0.82f, -15f, 2.5f, 0.78f),
    FairyParticle(0.26f, -46f, 1.8f, 0.48f),
    FairyParticle(0.90f, -54f, 3.0f, 0.36f),
)

/**
 * Geometric and visual placement data for a discovered device on the radar rings.
 */
internal data class OrbitalDevicePlacement(
    val device: AndroidDiscoveredDevice,
    val orbitIndex: Int,
    val radiusPx: Float,
    val angleDeg: Float,
    val formFactor: AudioFormFactor,
    val categoryTag: String,
)

/**
 * Solar System Orbital Radar Bluetooth Discovery UI.
 *
 * Implements a full-width orbital solar system scanner canvas:
 * - Center Sun: App Logo hub with a fluorescent yellow pulsing halo.
 * - Concentric planetary orbits (Inner: -30..-55 dBm, Middle: -56..-75 dBm, Outer: -76+ dBm).
 * - Continuous 360° rotating fluorescent yellow conic radar sweep line + 70° fading trail
 *   with floating luminous fairy-dust micro-particles.
 * - Floating planet bubbles with category-specific AdaptiveAudioGlyph silhouettes,
 *   signal strength bars, collision-free placement, and tap-to-connect glowing tether lines.
 * - Bottom physical Pairing Mode instruction banner with interactive helper tabs.
 */
@Composable
fun OrbitalScannerView(
    discoveredDevices: List<AndroidDiscoveredDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (AndroidDiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier,
    selectedDeviceId: String? = null,
    bondingStage: BondingStage = BondingStage.Idle,
    onRetryBonding: ((AndroidDiscoveredDevice) -> Unit)? = null,
    onResetBonding: (() -> Unit)? = null,
    bluetoothEnabled: Boolean = true,
    locationEnabled: Boolean = true,
    onRequestEnableBluetooth: (() -> Unit)? = null,
    onRequestEnableLocation: (() -> Unit)? = null,
    errorBanner: String? = null,
    onDismissError: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val motionEnabled = rememberMotionEnabled()

    // 1. Radar Sweep Infinite Rotation (3.5s per revolution)
    val sweepTransition = rememberInfiniteTransition(label = "RadarSweepTransition")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepAngle",
    )

    // 2. Center Sun Pulsing Outer Halo
    val haloScale by sweepTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "haloScale",
    )
    val haloAlpha by sweepTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "haloAlpha",
    )

    // 3. Zero-Gravity Bobbing Phase
    val bobPhase by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bobPhase",
    )

    // 4. Glowing Tether Dash Phase for active selection
    val tetherPhase by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tetherPhase",
    )

    // Revealed device tracking for radar sweep pass reveal animation
    val revealedDeviceIds = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmniBrand.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Space below the floating shell navigation capsule
        Spacer(modifier = Modifier.height(78.dp))

        // Top Status & Scan Control Strip
        ScanControlHeader(
            isScanning = isScanning,
            deviceCount = discoveredDevices.size,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
        )

        // Pre-Flight Check 1: Bluetooth Disabled Banner with 1-Tap Enable
        if (!bluetoothEnabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = OmniBrand.SurfaceElevated,
                border = BorderStroke(1.dp, OmniBrand.Border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Bluetooth is turned off",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = OmniBrand.TextPrimary,
                    )
                    Button(
                        onClick = { onRequestEnableBluetooth?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniBrand.Accent,
                            contentColor = OmniBrand.OnAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Turn On", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Pre-Flight Check 2: Location Disabled Banner with 1-Tap Enable
        if (!locationEnabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = OmniBrand.SurfaceElevated,
                border = BorderStroke(1.dp, OmniBrand.Border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Location is disabled (required for scan)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = OmniBrand.TextPrimary,
                    )
                    Button(
                        onClick = { onRequestEnableLocation?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniBrand.Accent,
                            contentColor = OmniBrand.OnAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Enable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Live Bonding Feedback Banner (Pairing / Audio Routing / Failed Retry)
        when (bondingStage) {
            is BondingStage.Bonding -> {
                LiveBondingBanner(
                    message = bondingStage.message,
                    isError = false,
                )
            }
            is BondingStage.AudioConnecting -> {
                LiveBondingBanner(
                    message = bondingStage.message,
                    isError = false,
                )
            }
            is BondingStage.Failed -> {
                LiveBondingBanner(
                    message = bondingStage.error,
                    isError = true,
                    onRetry = {
                        val failedDev = discoveredDevices.find { it.address == bondingStage.address || it.id == bondingStage.address }
                            ?: AndroidDiscoveredDevice(
                                id = bondingStage.address,
                                name = bondingStage.name,
                                address = bondingStage.address,
                                connectionState = com.omnibuds.core.globalstate.ConnectionState.Disconnected,
                                rssi = null,
                                transportKind = com.omnibuds.core.common.TransportKind.CLASSIC_BLUETOOTH,
                                isSupported = true,
                            )
                        onRetryBonding?.invoke(failedDev)
                    },
                    onDismiss = { onResetBonding?.invoke() },
                )
            }
            else -> {}
        }

        // Error Banner if present
        errorBanner?.let { banner ->
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                ErrorBanner(
                    message = banner,
                    onDismiss = onDismissError,
                    onRetry = onRetry,
                )
            }
        }

        // Center Orbital Solar System Canvas & Floating Bubbles
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }
            val centerOffset = Offset(canvasWidthPx / 2f, canvasHeightPx / 2f)

            // Outer boundary accounts for node bubbles and labels so they never clip against screen borders
            val availableHalfPx = minOf(canvasWidthPx, canvasHeightPx) / 2f
            val maxRadarRadiusPx = (availableHalfPx - with(density) { 44.dp.toPx() }).coerceAtLeast(100f)

            // Calculate collision-free device placements
            val devicePlacements = remember(discoveredDevices, maxRadarRadiusPx, context) {
                calculateCollisionFreePlacements(
                    devices = discoveredDevices,
                    maxRadiusPx = maxRadarRadiusPx,
                    context = context,
                )
            }

            // Find selected placement if any
            val selectedIndex = devicePlacements.indexOfFirst { it.device.id == selectedDeviceId }
            val selectedPlacement = if (selectedIndex >= 0) devicePlacements[selectedIndex] else null

            // Layer 1: Radar Orbital Canvas (Orbits + Rotating Sweep + Conic Trail + Fairy Dust + Tether)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // A. Concentric Planetary Orbits
                drawOrbitalRings(center = centerOffset, maxRadius = maxRadarRadiusPx)

                // B. Rotating Conic Radar Sweep & Fairy Dust
                if (isScanning && motionEnabled) {
                    drawConicRadarSweep(
                        center = centerOffset,
                        radarRadius = maxRadarRadiusPx,
                        sweepAngle = sweepAngle,
                    )
                }

                // C. Tap-to-Connect Glowing Tether Line
                if (selectedPlacement != null) {
                    val rad = Math.toRadians(selectedPlacement.angleDeg.toDouble())
                    val bobY = if (motionEnabled) (sin(bobPhase + selectedIndex * 1.7) * 3f).toFloat() else 0f
                    val bobX = if (motionEnabled) (cos(bobPhase * 0.8 + selectedIndex * 2.1) * 2f).toFloat() else 0f
                    val targetX = centerOffset.x + (selectedPlacement.radiusPx * cos(rad)).toFloat() + bobX
                    val targetY = centerOffset.y + (selectedPlacement.radiusPx * sin(rad)).toFloat() + bobY

                    val isBondingActive = bondingStage is BondingStage.Bonding || bondingStage is BondingStage.AudioConnecting
                    drawGlowingTether(
                        start = centerOffset,
                        end = Offset(targetX, targetY),
                        dashPhase = if (motionEnabled) (if (isBondingActive) tetherPhase * 2f else tetherPhase) else 0f,
                        isPulsing = isBondingActive,
                    )
                }
            }

            // Layer 2: Center Sun Hub (App Logo + Pulsing Halo)
            CenterSunHub(
                isScanning = isScanning && motionEnabled,
                haloScale = haloScale,
                haloAlpha = haloAlpha,
            )

            // Layer 3: Discovered Device Floating Planet Bubbles
            devicePlacements.forEachIndexed { index, placement ->
                val isSelected = placement.device.id == selectedDeviceId
                val sweepDiff = (sweepAngle - placement.angleDeg + 360f) % 360f

                // Radar sweeps past node within 0°..36° arc
                val isRadarSweepingPast = isScanning && motionEnabled && (sweepDiff in 0f..36f)

                // Auto-reveal on radar sweep pass or when scanning stops / motion disabled
                if (!revealedDeviceIds.contains(placement.device.id)) {
                    if (isRadarSweepingPast || !isScanning || !motionEnabled) {
                        revealedDeviceIds.add(placement.device.id)
                    }
                }

                val isRevealed = revealedDeviceIds.contains(placement.device.id)

                FloatingPlanetBubbleNode(
                    placement = placement,
                    centerOffset = centerOffset,
                    bobPhase = if (motionEnabled) bobPhase else 0f,
                    deviceIndex = index,
                    isSelected = isSelected,
                    bondingStage = if (isSelected) bondingStage else BondingStage.Idle,
                    onRetry = { onRetryBonding?.invoke(placement.device) },
                    isRevealed = isRevealed,
                    sweepDiff = if (isRadarSweepingPast) sweepDiff else null,
                    onSelect = { onSelectDevice(placement.device) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        // Bottom Section: Physical Pairing Mode Instruction Card
        PairingModeGuideCard(
            isScanning = isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))
    }
}

/**
 * Top Scan Control Header with radar status pill, device count, and scan toggle button.
 */
@Composable
private fun ScanControlHeader(
    isScanning: Boolean,
    deviceCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status Pill with blinking indicator
        Surface(
            shape = RoundedCornerShape(50),
            color = OmniBrand.SurfaceVariant.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, OmniBrand.Border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isScanning) OmniBrand.Accent else OmniBrand.Disabled),
                )
                Text(
                    text = if (isScanning) "Orbital Radar Active" else "Radar Idle",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isScanning) OmniBrand.Accent else OmniBrand.TextSecondary,
                )
                if (deviceCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(OmniBrand.Accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "$deviceCount found",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OmniBrand.Accent,
                        )
                    }
                }
            }
        }

        // Toggle Scan Button
        if (isScanning) {
            OutlinedButton(
                onClick = onStopScan,
                border = BorderStroke(1.dp, OmniBrand.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OmniBrand.TextSecondary),
            ) {
                Text("Stop", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Button(
                onClick = onStartScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmniBrand.Accent,
                    contentColor = OmniBrand.OnAccent,
                ),
            ) {
                Text(
                    "Scan Again",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * Center Sun: App Logo hub with a sleek glassmorphic container and pulsing outer halo.
 */
@Composable
private fun CenterSunHub(
    isScanning: Boolean,
    haloScale: Float,
    haloAlpha: Float,
) {
    Box(contentAlignment = Alignment.Center) {
        // Pulsing fluorescent yellow outer halo
        if (isScanning) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(haloScale)
                    .alpha(haloAlpha)
                    .clip(CircleShape)
                    .background(OmniBrand.Accent.copy(alpha = 0.45f)),
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(haloScale * 1.25f)
                    .alpha(haloAlpha * 0.5f)
                    .clip(CircleShape)
                    .border(1.dp, OmniBrand.Accent.copy(alpha = haloAlpha), CircleShape),
            )
        }

        // Glassmorphic Sun Core Hub displaying official OmniBuds app logo
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "OmniBuds Radar Core Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
}

/**
 * Floating Planet Bubble Node representing a discovered Bluetooth device.
 *
 * Positioned cleanly with zero-gravity bobbing, concentric ripple animations,
 * unclipped corner badge pills, and active pairing connection states.
 */
@Composable
private fun FloatingPlanetBubbleNode(
    placement: OrbitalDevicePlacement,
    centerOffset: Offset,
    bobPhase: Float,
    deviceIndex: Int,
    isSelected: Boolean,
    bondingStage: BondingStage = BondingStage.Idle,
    onRetry: (() -> Unit)? = null,
    isRevealed: Boolean,
    sweepDiff: Float?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Spring physics reveal on radar ping pass
    val revealScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "revealScale",
    )

    // Calculate Cartesian coordinates with zero-gravity bobbing (±3px)
    val rad = Math.toRadians(placement.angleDeg.toDouble())
    val bobY = (sin(bobPhase + deviceIndex * 1.7) * 3f).toFloat()
    val bobX = (cos(bobPhase * 0.8 + deviceIndex * 2.1) * 2f).toFloat()

    val targetXPx = centerOffset.x + (placement.radiusPx * cos(rad)).toFloat() + bobX
    val targetYPx = centerOffset.y + (placement.radiusPx * sin(rad)).toFloat() + bobY

    // 108dp wrapper width and 54dp bubble diameter ensure mathematical centering
    val halfWrapperWidthPx = with(density) { 54.dp.toPx() }
    val halfBubbleHeightPx = with(density) { 27.dp.toPx() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (targetXPx - halfWrapperWidthPx).roundToInt(),
                    y = (targetYPx - halfBubbleHeightPx).roundToInt(),
                )
            }
            .width(108.dp)
            .scale(revealScale),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Anchor container for 54dp circular bubble, ripple ring, and unclipped badge
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Radar Ping Ripple Pulse (expands outward from bubble center as sweep passes)
                if (sweepDiff != null) {
                    val progress = (sweepDiff / 36f).coerceIn(0f, 1f)
                    val rippleScale = 1.0f + progress * 0.85f
                    val rippleAlpha = (1.0f - progress) * 0.80f

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .scale(rippleScale)
                            .border(1.5.dp, OmniBrand.Accent.copy(alpha = rippleAlpha), CircleShape),
                    )
                }

                // Tap Highlight Outer Halo
                if (isSelected) {
                    val haloBorderColor = if (bondingStage is BondingStage.Failed) OmniBrand.Error else OmniBrand.Accent
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .border(2.dp, haloBorderColor, CircleShape),
                    )
                }

                // Main Circular Glassmorphic Bubble Body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(OmniBrand.SurfaceVariant.copy(alpha = 0.90f))
                        .border(
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    if (bondingStage is BondingStage.Failed) OmniBrand.Error else OmniBrand.Accent
                                } else {
                                    OmniBrand.Border
                                },
                            ),
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isSelected && bondingStage is BondingStage.Failed) {
                                    onRetry?.invoke()
                                } else {
                                    onSelect()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveAudioGlyph(
                        formFactor = placement.formFactor,
                        side = GlyphSide.PAIR,
                        size = 30.dp,
                        shellColor = OmniBrand.TextPrimary,
                        accentColor = OmniBrand.Accent,
                    )
                }

                // Category & Signal Strength Badge Pill (Top-End Corner, NOT clipped)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .background(OmniBrand.SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(0.5.dp, OmniBrand.Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 1.5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = if (placement.device.isBonded) "Paired" else placement.categoryTag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = OmniBrand.Accent,
                        )
                        SignalStrengthBars(rssi = placement.device.rssi)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Device Display Name Pill & Live Pairing Status
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OmniBrand.Surface.copy(alpha = 0.88f),
                border = BorderStroke(
                    width = 0.5.dp,
                    color = if (isSelected) {
                        if (bondingStage is BondingStage.Failed) OmniBrand.Error.copy(alpha = 0.8f) else OmniBrand.Accent.copy(alpha = 0.7f)
                    } else {
                        OmniBrand.Border.copy(alpha = 0.5f)
                    },
                ),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                if (isSelected) {
                    val statusText = when (bondingStage) {
                        is BondingStage.Bonding -> "Pairing..."
                        is BondingStage.Bonded -> "Pairing OK"
                        is BondingStage.AudioConnecting -> "Audio..."
                        is BondingStage.Connected -> "Connected ✓"
                        is BondingStage.Failed -> "Failed • Retry"
                        else -> "Pairing..."
                    }
                    val isFailed = bondingStage is BondingStage.Failed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clickable(enabled = isFailed) { onRetry?.invoke() },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isFailed) OmniBrand.Error else OmniBrand.Accent),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isFailed) OmniBrand.Error else OmniBrand.Accent,
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = placement.device.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = OmniBrand.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (placement.device.isBonded) {
                            Text(
                                text = "Paired • Tap to Connect",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = OmniBrand.Accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3-bar signal strength meter indicating Bluetooth RSSI level.
 */
@Composable
private fun SignalStrengthBars(rssi: Int?) {
    val litBars = when {
        rssi == null -> 1
        rssi >= -55 -> 3
        rssi >= -75 -> 2
        else -> 1
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.height(7.dp),
    ) {
        // Bar 1
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(3.dp)
                .background(if (litBars >= 1) OmniBrand.Accent else OmniBrand.Disabled.copy(alpha = 0.4f)),
        )
        // Bar 2
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(5.dp)
                .background(if (litBars >= 2) OmniBrand.Accent else OmniBrand.Disabled.copy(alpha = 0.4f)),
        )
        // Bar 3
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(7.dp)
                .background(if (litBars >= 3) OmniBrand.Accent else OmniBrand.Disabled.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Draws the 3-4 concentric planetary orbit rings with solid and dashed geometric variations.
 */
private fun DrawScope.drawOrbitalRings(center: Offset, maxRadius: Float) {
    val rInner = maxRadius * 0.40f
    val rMid = maxRadius * 0.66f
    val rOuter = maxRadarRadius(maxRadius)

    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    // Inner Orbit (Strongest: -30 to -55 dBm) - Dashed
    drawCircle(
        color = OmniBrand.Border.copy(alpha = 0.50f),
        radius = rInner,
        center = center,
        style = Stroke(width = 1.dp.toPx(), pathEffect = dashedEffect),
    )

    // Middle Orbit (-56 to -75 dBm) - Solid
    drawCircle(
        color = OmniBrand.Border.copy(alpha = 0.40f),
        radius = rMid,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )

    // Outer Orbit (-76+ dBm) - Dashed
    drawCircle(
        color = OmniBrand.Border.copy(alpha = 0.35f),
        radius = rOuter,
        center = center,
        style = Stroke(width = 1.dp.toPx(), pathEffect = dashedEffect),
    )

    // Subtle Outer Planetary Boundary - Faint Solid
    drawCircle(
        color = OmniBrand.Border.copy(alpha = 0.20f),
        radius = maxRadius,
        center = center,
        style = Stroke(width = 0.8.dp.toPx()),
    )
}

private fun maxRadarRadius(max: Float): Float = max * 0.88f

/**
 * Draws the rotating 360° radar sweep line, fading conic sector wake (70° arc),
 * and glowing fairy-dust micro-particles.
 */
private fun DrawScope.drawConicRadarSweep(
    center: Offset,
    radarRadius: Float,
    sweepAngle: Float,
) {
    val trailArcDegrees = 70f

    rotate(degrees = sweepAngle, pivot = center) {
        // Conic Gradient Sector Trail
        val conicBrush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                ((360f - trailArcDegrees) / 360f) to Color.Transparent,
                1.0f to OmniBrand.Accent.copy(alpha = 0.25f),
            ),
            center = center,
        )

        drawArc(
            brush = conicBrush,
            startAngle = -trailArcDegrees,
            sweepAngle = trailArcDegrees,
            useCenter = true,
            topLeft = Offset(center.x - radarRadius, center.y - radarRadius),
            size = Size(radarRadius * 2f, radarRadius * 2f),
        )

        // Leading Fluorescent Yellow Sweep Line
        drawLine(
            color = OmniBrand.Accent,
            start = center,
            end = center + Offset(radarRadius, 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = OmniBrand.Accent.copy(alpha = 0.35f),
            start = center,
            end = center + Offset(radarRadius, 0f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Fairy-Dust Shimmer Particles drifting in the radar wake
        FAIRY_DUST_PARTICLES.forEachIndexed { index, particle ->
            val rad = Math.toRadians(particle.deltaAngleDeg.toDouble())
            val r = radarRadius * particle.radiusFraction
            val px = center.x + (r * cos(rad)).toFloat()
            val py = center.y + (r * sin(rad)).toFloat()

            // Trail fade from leading line down to 0% at tail
            val trailFade = ((particle.deltaAngleDeg + trailArcDegrees) / trailArcDegrees).coerceIn(0f, 1f)
            val shimmer = (sin(sweepAngle * 0.08f + index * 1.3f) * 0.25f + 0.75f).coerceIn(0.5f, 1f)
            val finalAlpha = (particle.baseAlpha * trailFade * shimmer).coerceIn(0f, 1f)

            // Micro Particle Core
            drawCircle(
                color = OmniBrand.Accent.copy(alpha = finalAlpha),
                radius = particle.sizePx,
                center = Offset(px, py),
            )
            // Soft Particle Glow
            if (particle.sizePx >= 2.5f) {
                drawCircle(
                    color = OmniBrand.Accent.copy(alpha = finalAlpha * 0.35f),
                    radius = particle.sizePx * 2.2f,
                    center = Offset(px, py),
                )
            }
        }
    }
}

/**
 * Draws a glowing sci-fi connection tether from the bubble to the center Sun hub.
 */
private fun DrawScope.drawGlowingTether(
    start: Offset,
    end: Offset,
    dashPhase: Float,
    isPulsing: Boolean = false,
) {
    val glowWidth = if (isPulsing) 8.dp.toPx() else 5.dp.toPx()
    val glowAlpha = if (isPulsing) 0.60f else 0.35f

    // Outer Glow Line
    drawLine(
        color = OmniBrand.Accent.copy(alpha = glowAlpha),
        start = start,
        end = end,
        strokeWidth = glowWidth,
        cap = StrokeCap.Round,
    )
    // Inner Animated Dashed Line
    val dashed = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), dashPhase)
    drawLine(
        color = OmniBrand.Accent,
        start = start,
        end = end,
        strokeWidth = if (isPulsing) 2.5.dp.toPx() else 2.dp.toPx(),
        pathEffect = dashed,
        cap = StrokeCap.Round,
    )
    // Anchoring Dots
    val dotRadius = if (isPulsing) 5.dp.toPx() else 4.dp.toPx()
    drawCircle(color = OmniBrand.Accent, radius = dotRadius, center = start)
    drawCircle(color = OmniBrand.Accent, radius = dotRadius, center = end)
}

/**
 * Live feedback banner displayed during Bluetooth bonding and audio routing.
 */
@Composable
private fun LiveBondingBanner(
    message: String,
    isError: Boolean,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) OmniBrand.Error.copy(alpha = 0.15f) else OmniBrand.SurfaceElevated,
        border = BorderStroke(1.dp, if (isError) OmniBrand.Error else OmniBrand.Accent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isError) OmniBrand.Error else OmniBrand.Accent),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isError) OmniBrand.Error else OmniBrand.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isError && onRetry != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OmniBrand.Accent,
                        contentColor = OmniBrand.OnAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Retry",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/**
 * Physical "Pairing Mode" Instruction Banner & Interactive Helper.
 */
@Composable
fun PairingModeGuideCard(
    isScanning: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isExpanded by remember { mutableStateOf(true) }
    var userInteracted by remember { mutableStateOf(false) }

    // Subtle pulsing animation for status prompt indicator
    val infiniteTransition = rememberInfiniteTransition(label = "PairingBannerTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Subtle automatic cycling guide if user has not interacted
    LaunchedEffect(isScanning, userInteracted) {
        if (isScanning && !userInteracted) {
            while (true) {
                delay(6000L)
                selectedTab = (selectedTab + 1) % 3
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = OmniBrand.SurfaceVariant.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, OmniBrand.Border),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Live Status Prompt Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (isScanning) pulseScale else 1f)
                        .alpha(if (isScanning) pulseAlpha else 0.5f)
                        .clip(CircleShape)
                        .background(if (isScanning) OmniBrand.Accent else OmniBrand.Disabled),
                )
                Text(
                    text = if (isScanning) {
                        "Make sure your earbuds or headphones are in Pairing Mode so they become visible on the radar."
                    } else {
                        "Radar idle. Put devices in Pairing Mode, then tap Scan."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                    ),
                    color = OmniBrand.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isExpanded) "Hide" else "Guide",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OmniBrand.Accent,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Category-Specific Physical Action Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuideChip(
                            label = "In-Case Buds",
                            isSelected = selectedTab == 0,
                            onClick = {
                                userInteracted = true
                                selectedTab = 0
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GuideChip(
                            label = "Stem Touch (No Btn)",
                            isSelected = selectedTab == 1,
                            onClick = {
                                userInteracted = true
                                selectedTab = 1
                            },
                            modifier = Modifier.weight(1.15f),
                        )
                        GuideChip(
                            label = "Neckband/Headset",
                            isSelected = selectedTab == 2,
                            onClick = {
                                userInteracted = true
                                selectedTab = 2
                            },
                            modifier = Modifier.weight(1.1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Physical Instruction Text Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OmniBrand.SurfaceElevated,
                        border = BorderStroke(0.5.dp, OmniBrand.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Open the charging case lid (keep buds inside) and press & hold the case button—or touch & hold both earbud stems simultaneously—for 3–5 seconds until the indicator LED flashes white/blue."
                                1 -> "Take both earbuds out of the case (or keep them in the open case) and touch & hold both Left & Right touch areas simultaneously for 4–5 seconds until you hear the pairing chime."
                                else -> "From powered-off (or on), press and hold the Power / Multi-Function button for 5–7 seconds until the LED flashes rapidly."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 17.sp,
                                color = OmniBrand.TextSecondary,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) OmniBrand.Accent.copy(alpha = 0.18f) else OmniBrand.Surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) OmniBrand.Accent else OmniBrand.Border,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp,
            ),
            color = if (isSelected) OmniBrand.Accent else OmniBrand.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        )
    }
}

/**
 * Calculates collision-free polar coordinates for all discovered devices.
 */
internal fun calculateCollisionFreePlacements(
    devices: List<AndroidDiscoveredDevice>,
    maxRadiusPx: Float,
    context: Context? = null,
): List<OrbitalDevicePlacement> {
    val rInner = maxRadiusPx * 0.40f
    val rMid = maxRadiusPx * 0.66f
    val rOuter = maxRadarRadius(maxRadiusPx)

    // Group devices into 3 proximity orbits based on RSSI (paired devices on outer orbit)
    val orbitGroups = Array(3) { mutableListOf<AndroidDiscoveredDevice>() }
    devices.forEach { device ->
        val orbit = when {
            device.isBonded -> 2
            device.rssi == null -> 2
            device.rssi >= -55 -> 0
            device.rssi >= -75 -> 1
            else -> 2
        }
        orbitGroups[orbit].add(device)
    }

    val result = mutableListOf<OrbitalDevicePlacement>()

    for (orbitIdx in 0..2) {
        val group = orbitGroups[orbitIdx]
        val n = group.size
        if (n == 0) continue

        val radiusPx = when (orbitIdx) {
            0 -> rInner
            1 -> rMid
            else -> rOuter
        }

        // Stagger base angles between orbits so bubbles across rings don't align radially
        val baseAngle = when (orbitIdx) {
            0 -> 45f
            1 -> 125f
            else -> 205f
        }

        group.forEachIndexed { i, device ->
            // Collision-free equidistant angular spacing along the orbit
            val angleDeg = (baseAngle + i * (360f / n)) % 360f

            val formFactor = AudioFormFactorResolver.resolve(
                context = context,
                address = device.address,
                deviceName = device.displayName,
            )

            val categoryTag = if (device.isBonded) {
                "Paired"
            } else {
                when (formFactor) {
                    AudioFormFactor.STEM_IN_EAR,
                    AudioFormFactor.STEM_HALF_IN_EAR,
                    AudioFormFactor.BUD_BEAN_IN_EAR,
                    AudioFormFactor.BUD_OPEN_RING -> "TWS"
                    AudioFormFactor.OPEN_EAR_HOOK,
                    AudioFormFactor.OPEN_EAR_CLIP -> "Open-Ear"
                    AudioFormFactor.NECKBAND -> "Neckband"
                    AudioFormFactor.HEADSET_OVER_ON_EAR,
                    AudioFormFactor.HEADSET_OPEN_BACK -> "Headset"
                    AudioFormFactor.BONE_CONDUCTION -> "Bone"
                }
            }

            result.add(
                OrbitalDevicePlacement(
                    device = device,
                    orbitIndex = orbitIdx,
                    radiusPx = radiusPx,
                    angleDeg = angleDeg,
                    formFactor = formFactor,
                    categoryTag = categoryTag,
                ),
            )
        }
    }

    return result
}
