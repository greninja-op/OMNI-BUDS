package com.omnibuds.desktop.theme

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
        val Dark = DesktopColors(
            background = "#121316",
            surface = "#1A1C20",
            surfaceElevated = "#242830",
            onBackground = "#E6EDF3",
            onSurface = "#E0E4E8",
            onSurfaceVariant = "#9CA3AF",
            primary = "#38BDF8",
            onPrimary = "#031525",
            border = "#333A42",
            focusRing = "#0284C7",
            statusAvailable = "#22C55E",
            statusWarning = "#F59E0B",
            statusError = "#EF4444",
            statusNeutral = "#6B7280",
            statusActive = "#38BDF8",
        )

        val Light = DesktopColors(
            background = "#F8FAFC",
            surface = "#FFFFFF",
            surfaceElevated = "#F1F5F9",
            onBackground = "#0F172A",
            onSurface = "#1E293B",
            onSurfaceVariant = "#64748B",
            primary = "#0284C7",
            onPrimary = "#FFFFFF",
            border = "#E2E8F0",
            focusRing = "#0369A1",
            statusAvailable = "#16A34A",
            statusWarning = "#D97706",
            statusError = "#DC2626",
            statusNeutral = "#64748B",
            statusActive = "#0284C7",
        )

        val HighContrast = DesktopColors(
            background = "#000000",
            surface = "#0A0A0A",
            surfaceElevated = "#171717",
            onBackground = "#FFFFFF",
            onSurface = "#FFFFFF",
            onSurfaceVariant = "#E5E5E5",
            primary = "#00E5FF",
            onPrimary = "#000000",
            border = "#FFFFFF",
            focusRing = "#FFFF00",
            statusAvailable = "#00FF00",
            statusWarning = "#FFFF00",
            statusError = "#FF0033",
            statusNeutral = "#CCCCCC",
            statusActive = "#00E5FF",
        )
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
 * 4dp desktop spacing grid tokens.
 */
object DesktopSpacing {
    const val space4 = 4
    const val space8 = 8
    const val space12 = 12
    const val space16 = 16
    const val space20 = 20
    const val space24 = 24
    const val space32 = 32
    const val space48 = 48
}

/**
 * Corner radius shapes for desktop components.
 */
object DesktopShapes {
    const val radiusNone = 0
    const val radiusSmall = 4
    const val radiusMedium = 8
    const val radiusLarge = 12
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
