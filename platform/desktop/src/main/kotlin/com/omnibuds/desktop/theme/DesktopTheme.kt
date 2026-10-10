package com.omnibuds.desktop.theme

import com.omnibuds.core.presentation.theme.OmniBudsColors
import com.omnibuds.core.presentation.theme.OmniBudsShapes
import com.omnibuds.core.presentation.theme.OmniBudsSpacing

/**
 * Display theme modes supported by the OmniBuds desktop interface.
 */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    HIGH_CONTRAST,
}

/**
 * Layout density modes for desktop screens.
 */
enum class UiDensity {
    COMFORTABLE,
    COMPACT,
}

/**
 * Cohesive color palette for desktop controls and state presentation.
 * Directly adapts unified core design tokens (com.omnibuds.core.presentation.theme.OmniBudsColors).
 * Adheres to WCAG AA/AAA contrast ratios against surfaces.
 */
data class DesktopColors(
    val background: String,
    val surface: String,
    val surfaceElevated: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val primary: String,
    val onPrimary: String,
    val border: String,
    val focusRing: String,
    // Status colors
    val statusAvailable: String,
    val statusWarning: String,
    val statusError: String,
    val statusNeutral: String,
    val statusActive: String,
) {
    companion object {
        fun fromCore(core: OmniBudsColors): DesktopColors = DesktopColors(
            background = core.background,
            surface = core.surface,
            surfaceElevated = core.surfaceElevated,
            onBackground = core.onBackground,
            onSurface = core.onSurface,
            onSurfaceVariant = core.onSurfaceVariant,
            primary = core.primary,
            onPrimary = core.onPrimary,
            border = core.outline,
            focusRing = core.focusRing,
            statusAvailable = core.statusAvailable,
            statusWarning = core.statusWarning,
            statusError = core.statusError,
            statusNeutral = core.statusNeutral,
            statusActive = core.statusActive,
        )

        val Dark: DesktopColors = fromCore(OmniBudsColors.Dark)
        val Light: DesktopColors = fromCore(OmniBudsColors.Light)
        val HighContrast: DesktopColors = fromCore(OmniBudsColors.HighContrast)
    }
}

/**
 * Clean desktop typography tokens.
 */
data class DesktopTypography(
    val titleLarge: String = "20px/600 Inter, sans-serif",
    val titleMedium: String = "16px/600 Inter, sans-serif",
    val bodyLarge: String = "14px/400 Inter, sans-serif",
    val bodyMedium: String = "13px/400 Inter, sans-serif",
    val labelSmall: String = "11px/500 Inter, sans-serif",
    val codeMonospace: String = "12px/400 'JetBrains Mono', monospace",
)

/**
 * 4dp desktop spacing grid tokens backed by unified core tokens.
 */
object DesktopSpacing {
    const val space4 = OmniBudsSpacing.space4
    const val space8 = OmniBudsSpacing.space8
    const val space12 = OmniBudsSpacing.space12
    const val space16 = OmniBudsSpacing.space16
    const val space20 = OmniBudsSpacing.space20
    const val space24 = OmniBudsSpacing.space24
    const val space32 = OmniBudsSpacing.space32
    const val space48 = OmniBudsSpacing.space48
}

/**
 * Corner radius shapes for desktop components backed by unified core tokens.
 */
object DesktopShapes {
    const val radiusNone = OmniBudsShapes.radiusNone
    const val radiusSmall = OmniBudsShapes.radiusSmall
    const val radiusMedium = OmniBudsShapes.radiusMedium
    const val radiusLarge = OmniBudsShapes.radiusLarge
}

/**
 * Active theme bundle.
 */
data class DesktopTheme(
    val mode: ThemeMode = ThemeMode.DARK,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val colors: DesktopColors = DesktopColors.Dark,
    val typography: DesktopTypography = DesktopTypography(),
) {
    companion object {
        fun resolve(mode: ThemeMode, density: UiDensity = UiDensity.COMFORTABLE): DesktopTheme {
            val colors = when (mode) {
                ThemeMode.LIGHT -> DesktopColors.Light
                ThemeMode.HIGH_CONTRAST -> DesktopColors.HighContrast
                ThemeMode.DARK, ThemeMode.SYSTEM -> DesktopColors.Dark
            }
            return DesktopTheme(mode = mode, density = density, colors = colors)
        }
    }
}
