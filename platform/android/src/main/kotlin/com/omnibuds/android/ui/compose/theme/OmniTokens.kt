package com.omnibuds.android.ui.compose.theme

import androidx.compose.ui.unit.dp

/**
 * OmniBuds design tokens (UI redesign, v1.8). One scale for spacing,
 * radius and motion so every screen is built from the same geometry.
 * Colors live in [OmniBrand]; typography stays on the Material 3 scale
 * with brand weights applied at call sites.
 */
object OmniTokens {
    // Spacing scale (dp)
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    // Corner radius scale (dp)
    val RadiusSm = 12.dp
    val RadiusMd = 20.dp
    val RadiusLg = 28.dp
    val RadiusFull = 999.dp

    // Motion (ms) — restrained, functional, always reduced-motion gated
    // by rememberMotionEnabled() at call sites.
    const val MotionFastMs = 160
    const val MotionMediumMs = 260
    const val MotionSlowMs = 420
}
