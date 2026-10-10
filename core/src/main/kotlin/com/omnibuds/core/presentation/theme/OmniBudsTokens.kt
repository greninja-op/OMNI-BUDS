package com.omnibuds.core.presentation.theme

/**
 * Display theme modes supported by the OmniBuds user interface across all platforms.
 */
enum class OmniBudsThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    HIGH_CONTRAST,
}

/**
 * Centralized, WCAG AA/AAA compliant color palette for OmniBuds.
 * Colors are represented as standard CSS/hex strings to maintain multiplatform compatibility
 * and prevent protocol-hex literal conflicts.
 */
data class OmniBudsColors(
    val background: String,
    val surface: String,
    val surfaceElevated: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val outline: String,
    val outlineVariant: String,
    val focusRing: String,
    // Semantic hardware & operation status colors
    val statusAvailable: String,
    val statusWarning: String,
    val statusError: String,
    val statusNeutral: String,
    val statusActive: String,
) {
    companion object {
        val Dark = OmniBudsColors(
            background = "#121316",
            surface = "#1A1C20",
            surfaceElevated = "#242830",
            surfaceVariant = "#2E333D",
            onBackground = "#E6EDF3",
            onSurface = "#E0E4E8",
            onSurfaceVariant = "#9CA3AF",
            primary = "#38BDF8",
            onPrimary = "#031525",
            primaryContainer = "#0C4A6E",
            onPrimaryContainer = "#E0F2FE",
            outline = "#374151",
            outlineVariant = "#1F2937",
            focusRing = "#0284C7",
            statusAvailable = "#22C55E",
            statusWarning = "#F59E0B",
            statusError = "#EF4444",
            statusNeutral = "#6B7280",
            statusActive = "#38BDF8",
        )

        val Light = OmniBudsColors(
            background = "#F8FAFC",
            surface = "#FFFFFF",
            surfaceElevated = "#F1F5F9",
            surfaceVariant = "#E2E8F0",
            onBackground = "#0F172A",
            onSurface = "#1E293B",
            onSurfaceVariant = "#64748B",
            primary = "#0284C7",
            onPrimary = "#FFFFFF",
            primaryContainer = "#E0F2FE",
            onPrimaryContainer = "#0369A1",
            outline = "#CBD5E1",
            outlineVariant = "#E2E8F0",
            focusRing = "#0369A1",
            statusAvailable = "#16A34A",
            statusWarning = "#D97706",
            statusError = "#DC2626",
            statusNeutral = "#64748B",
            statusActive = "#0284C7",
        )

        val HighContrast = OmniBudsColors(
            background = "#000000",
            surface = "#0A0A0A",
            surfaceElevated = "#1A1A1A",
            surfaceVariant = "#262626",
            onBackground = "#FFFFFF",
            onSurface = "#FFFFFF",
            onSurfaceVariant = "#F0F0F0",
            primary = "#00E5FF",
            onPrimary = "#000000",
            primaryContainer = "#00363A",
            onPrimaryContainer = "#FFFFFF",
            outline = "#FFFFFF",
            outlineVariant = "#CCCCCC",
            focusRing = "#FFFF00",
            statusAvailable = "#00FF66",
            statusWarning = "#FFD700",
            statusError = "#FF3333",
            statusNeutral = "#B0B0B0",
            statusActive = "#00E5FF",
        )

        fun resolve(mode: OmniBudsThemeMode, systemIsDark: Boolean = true): OmniBudsColors = when (mode) {
            OmniBudsThemeMode.LIGHT -> Light
            OmniBudsThemeMode.HIGH_CONTRAST -> HighContrast
            OmniBudsThemeMode.DARK -> Dark
            OmniBudsThemeMode.SYSTEM -> if (systemIsDark) Dark else Light
        }
    }
}

/**
 * Standard typography scales in points / SP.
 */
data class OmniBudsTypographyScale(
    val sizeSp: Int,
    val lineHeightSp: Int,
    val isBold: Boolean = false,
)

object OmniBudsTypography {
    val displayLarge = OmniBudsTypographyScale(sizeSp = 32, lineHeightSp = 40, isBold = true)
    val displayMedium = OmniBudsTypographyScale(sizeSp = 28, lineHeightSp = 36, isBold = true)
    val headlineSmall = OmniBudsTypographyScale(sizeSp = 24, lineHeightSp = 32, isBold = true)
    val titleLarge = OmniBudsTypographyScale(sizeSp = 20, lineHeightSp = 26, isBold = true)
    val titleMedium = OmniBudsTypographyScale(sizeSp = 16, lineHeightSp = 22, isBold = true)
    val titleSmall = OmniBudsTypographyScale(sizeSp = 14, lineHeightSp = 20, isBold = true)
    val bodyLarge = OmniBudsTypographyScale(sizeSp = 16, lineHeightSp = 24, isBold = false)
    val bodyMedium = OmniBudsTypographyScale(sizeSp = 14, lineHeightSp = 20, isBold = false)
    val bodySmall = OmniBudsTypographyScale(sizeSp = 12, lineHeightSp = 16, isBold = false)
    val labelLarge = OmniBudsTypographyScale(sizeSp = 14, lineHeightSp = 20, isBold = true)
    val labelMedium = OmniBudsTypographyScale(sizeSp = 12, lineHeightSp = 16, isBold = true)
    val labelSmall = OmniBudsTypographyScale(sizeSp = 11, lineHeightSp = 14, isBold = false)
    val codeMonospace = OmniBudsTypographyScale(sizeSp = 12, lineHeightSp = 16, isBold = false)
}

/**
 * 4dp base spacing grid tokens.
 */
object OmniBudsSpacing {
    const val space0: Int = 0
    const val space4: Int = 4
    const val space8: Int = 8
    const val space12: Int = 12
    const val space16: Int = 16
    const val space20: Int = 20
    const val space24: Int = 24
    const val space32: Int = 32
    const val space48: Int = 48
}

/**
 * Corner radius shapes in density-independent units.
 */
object OmniBudsShapes {
    const val radiusNone: Int = 0
    const val radiusSmall: Int = 4
    const val radiusMedium: Int = 8
    const val radiusLarge: Int = 12
    const val radiusExtraLarge: Int = 16
    const val radiusPill: Int = 999
}

/**
 * Surface elevation tokens.
 */
object OmniBudsElevation {
    const val level0: Int = 0
    const val level1: Int = 1
    const val level2: Int = 3
    const val level3: Int = 6
    const val level4: Int = 8
    const val level5: Int = 12
}

/**
 * Standard animation duration tokens (in milliseconds).
 */
object OmniBudsMotion {
    const val durationFastMs: Long = 150L
    const val durationMediumMs: Long = 300L
    const val durationSlowMs: Long = 500L
}

/**
 * Semantic iconography enumeration and descriptive labels.
 */
enum class OmniBudsIcon(val description: String) {
    DEVICE_HEADPHONES("Over-ear headphones"),
    DEVICE_EARBUDS("In-ear earbuds"),
    DEVICE_CASE("Charging case"),
    DEVICE_UNKNOWN("Bluetooth audio device"),
    BLUETOOTH("Bluetooth"),
    BATTERY_FULL("Battery full"),
    BATTERY_HALF("Battery medium"),
    BATTERY_LOW("Battery low"),
    BATTERY_CHARGING("Battery charging"),
    BATTERY_ALERT("Battery critical"),
    ANC("Active noise cancellation"),
    TRANSPARENCY("Transparency mode"),
    NORMAL_MODE("Normal mode (ANC off)"),
    EQUALIZER("Equalizer preset"),
    SPATIAL_AUDIO("Spatial audio"),
    MULTIPOINT("Multipoint multi-device connection"),
    WEAR_DETECTION("In-ear wear detection"),
    TOUCH_GESTURES("Touch control gestures"),
    SETTINGS("Application settings"),
    DIAGNOSTICS("Diagnostic events"),
    REFRESH("Refresh status"),
    ERROR("Error warning"),
    CHECK("Operation success"),
    INFO("Information details"),
}
