package com.omnibuds.android.ui.compose.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibuds.android.hardware.capability.BrandSchema
import com.omnibuds.android.ui.compose.model.AudioFormFactor
import com.omnibuds.android.ui.compose.theme.OmniBrand

/**
 * Adaptive Dynamic Battery Ring Layout Engine.
 *
 * Implements 3 dynamic layout states driven by live hardware telemetry & form-factor:
 * - State A: Full Split Telemetry (Separate Left Bud, Right Bud, and Charging Case rings)
 * - State B: Combined TWS Telemetry (1 Dual-Bud ring with paired L+R glyphs facing each other + Case ring)
 * - State C: Single-Unit Headset / Neckband (1 Prominent centered ring, Case and Split rings hidden)
 */
@Composable
fun AdaptiveBatteryRings(
    formFactor: AudioFormFactor,
    leftPercent: Int?,
    rightPercent: Int?,
    casePercent: Int?,
    devicePercent: Int?,
    leftCharging: Boolean,
    rightCharging: Boolean,
    caseCharging: Boolean,
    deviceCharging: Boolean,
    caseIsLive: Boolean,
    lastKnown: Boolean,
    modifier: Modifier = Modifier,
    forceCombinedView: Boolean = false,
    supportsCaseBatteryWhenUndocked: Boolean = false,
    brandSchema: BrandSchema = BrandSchema.GENERIC,
    onChangeStyleClick: (() -> Unit)? = null,
    onToggleViewMode: (() -> Unit)? = null,
) {
    val isRealme = brandSchema == BrandSchema.REALME_LINK
    val isSingleUnit = !formFactor.isBinauralSplit
    val isCombinedTws = forceCombinedView || (!isSingleUnit && leftPercent == null && rightPercent == null && devicePercent != null)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Form Factor Header / Style Pill ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onChangeStyleClick != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onChangeStyleClick() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Style: ${formFactor.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniBrand.Accent,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (!isSingleUnit && onToggleViewMode != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { onToggleViewMode() },
                ) {
                    Text(
                        text = if (forceCombinedView) "Switch to Split" else "Switch to Combined",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniBrand.Accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        when {
            // STATE C: Single-Unit Headset / Neckband / Bone Conduction
            isSingleUnit -> {
                val effectivePercent = devicePercent ?: leftPercent ?: rightPercent ?: 85
                val effectiveCharging = deviceCharging || leftCharging || rightCharging
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SingleBatteryRing(
                        formFactor = formFactor,
                        side = GlyphSide.LEFT,
                        label = formFactor.displayName,
                        badgeText = "Connected",
                        percent = effectivePercent,
                        charging = effectiveCharging,
                        lastKnown = lastKnown,
                        ringSize = 104.dp,
                    )
                }
            }

            // STATE B: Combined TWS Telemetry (Paired L+R inside 1 Ring + Case)
            isCombinedTws -> {
                val combinedPercent = devicePercent ?: maxOf(leftPercent ?: 0, rightPercent ?: 0).takeIf { it > 0 } ?: 100
                val combinedCharging = deviceCharging || leftCharging || rightCharging

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // Combined Earbuds Ring
                    SingleBatteryRing(
                        formFactor = formFactor,
                        side = GlyphSide.PAIR,
                        label = "Earbuds",
                        badgeText = "L + R",
                        percent = combinedPercent,
                        charging = combinedCharging,
                        lastKnown = lastKnown,
                        ringSize = 92.dp,
                    )

                    // Case Ring (if hasCase)
                    if (formFactor.hasCase) {
                        if (casePercent != null && casePercent in 1..100) {
                            SingleBatteryRing(
                                formFactor = formFactor,
                                side = GlyphSide.CASE,
                                label = "Case",
                                badgeText = "Case",
                                subText = if (caseIsLive || !isRealme) null else "Dock 1 bud in case to sync",
                                percent = casePercent,
                                charging = caseCharging,
                                lastKnown = !caseIsLive || lastKnown,
                                ringSize = 92.dp,
                            )
                        } else if (isRealme && supportsCaseBatteryWhenUndocked) {
                            UnavailableRingSlot(
                                formFactor = formFactor,
                                side = GlyphSide.CASE,
                                label = "Case",
                                badgeText = "Case",
                                message = "Dock 1 bud in case to read",
                                ringSize = 92.dp,
                            )
                        }
                    }
                }
            }

            // STATE A: Full Split Telemetry (Left + Right + Case)
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // LEFT BUD
                    if (leftPercent != null) {
                        SingleBatteryRing(
                            formFactor = formFactor,
                            side = GlyphSide.LEFT,
                            label = "Left",
                            badgeText = "L",
                            percent = leftPercent,
                            charging = leftCharging,
                            lastKnown = lastKnown,
                            ringSize = 84.dp,
                        )
                    } else {
                        UnavailableRingSlot(
                            formFactor = formFactor,
                            side = GlyphSide.LEFT,
                            label = "Left",
                            badgeText = "L",
                            message = if (leftCharging) "In Case • Charging" else "In Case / Offline",
                            ringSize = 84.dp,
                        )
                    }

                    // RIGHT BUD
                    if (rightPercent != null) {
                        SingleBatteryRing(
                            formFactor = formFactor,
                            side = GlyphSide.RIGHT,
                            label = "Right",
                            badgeText = "R",
                            percent = rightPercent,
                            charging = rightCharging,
                            lastKnown = lastKnown,
                            ringSize = 84.dp,
                        )
                    } else {
                        UnavailableRingSlot(
                            formFactor = formFactor,
                            side = GlyphSide.RIGHT,
                            label = "Right",
                            badgeText = "R",
                            message = if (rightCharging) "In Case • Charging" else "In Case / Offline",
                            ringSize = 84.dp,
                        )
                    }

                    // CHARGING CASE
                    if (formFactor.hasCase) {
                        if (casePercent != null && casePercent in 1..100) {
                            SingleBatteryRing(
                                formFactor = formFactor,
                                side = GlyphSide.CASE,
                                label = "Case",
                                badgeText = "Case",
                                subText = if (caseIsLive || !isRealme) null else "Dock 1 bud in case to sync",
                                percent = casePercent,
                                charging = caseCharging,
                                lastKnown = !caseIsLive || lastKnown,
                                ringSize = 84.dp,
                            )
                        } else if (isRealme && supportsCaseBatteryWhenUndocked) {
                            UnavailableRingSlot(
                                formFactor = formFactor,
                                side = GlyphSide.CASE,
                                label = "Case",
                                badgeText = "Case",
                                message = "Dock 1 bud in case to read",
                                ringSize = 84.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBatteryRing(
    formFactor: AudioFormFactor,
    side: GlyphSide,
    label: String,
    percent: Int,
    charging: Boolean,
    lastKnown: Boolean,
    badgeText: String? = null,
    subText: String? = null,
    ringSize: Dp = 84.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 650),
        label = "batteryProgress",
    )

    val ringColor = when {
        lastKnown -> OmniBrand.Disabled
        percent <= 10 -> OmniBrand.Error
        percent <= 20 -> OmniBrand.Warning
        else -> Color(0xFF22E55E) // Crisp Apple green
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ringSize + 8.dp),
        ) {
            // Track & Progress Ring
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(ringSize),
                strokeWidth = 6.dp,
                color = ringColor,
                trackColor = Color(0xFF2C2F36),
            )

            // Vector Adaptive Audio Glyph
            AdaptiveAudioGlyph(
                formFactor = formFactor,
                side = side,
                size = ringSize * 0.52f,
            )

            // Top Lightning Bolt Charging Cutout Badge
            if (charging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22E55E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = OmniIcons.Bolt,
                        contentDescription = "Charging",
                        tint = Color.Black,
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
                color = Color(0xFF22E55E),
            )
        } else if (subText != null) {
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UnavailableRingSlot(
    formFactor: AudioFormFactor,
    side: GlyphSide,
    label: String,
    badgeText: String,
    message: String,
    ringSize: Dp = 84.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(ringSize + 8.dp),
        ) {
            CircularProgressIndicator(
                progress = { 0f },
                modifier = Modifier.size(ringSize),
                strokeWidth = 6.dp,
                color = Color(0xFF2C2F36),
                trackColor = Color(0xFF2C2F36),
            )
            AdaptiveAudioGlyph(
                formFactor = formFactor,
                side = side,
                size = ringSize * 0.52f,
                shellColor = OmniBrand.Disabled,
                accentColor = OmniBrand.Disabled,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(bottom = 3.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = OmniBrand.Disabled,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = OmniBrand.Disabled,
            textAlign = TextAlign.Center,
        )
    }
}

