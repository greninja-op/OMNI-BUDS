package com.omnibuds.android.presentation.theme

/**
 * Display theme modes supported by the OmniBuds Android user interface.
 */
enum class AndroidThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    HIGH_CONTRAST,
}

/**
 * Material 3 window size classes for responsive layout across Android form factors
 * (phones, foldables, tablets).
 */
enum class AndroidWindowSizeClass {
    COMPACT, // < 600 dp (typical phones in portrait)
    MEDIUM,  // 600 dp - 839 dp (foldables, small tablets, phones in landscape)
    EXPANDED // >= 840 dp (large tablets, desktop/multi-window)
    ;

    companion object {
        fun fromWidthDp(widthDp: Int): AndroidWindowSizeClass = when {
            widthDp < 600 -> COMPACT
            widthDp < 840 -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Cohesive color palette for Android controls and hardware state presentation.
 * Adheres to WCAG AA / AAA contrast ratios against dark and light surfaces.
 */
data class AndroidColors(
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
    // Hardware & system status colors
    val statusAvailable: String,
    val statusWarning: String,
    val statusError: String,
    val statusNeutral: String,
    val statusActive: String,
) {
    companion object {
        val Dark = AndroidColors(
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
            statusAvailable = "#22C55E",
            statusWarning = "#F59E0B",
            statusError = "#EF4444",
            statusNeutral = "#6B7280",
            statusActive = "#38BDF8",
        )

        val Light = AndroidColors(
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
            statusAvailable = "#16A34A",
            statusWarning = "#D97706",
            statusError = "#DC2626",
            statusNeutral = "#64748B",
            statusActive = "#0284C7",
        )

        val HighContrast = AndroidColors(
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
            statusAvailable = "#00FF66",
            statusWarning = "#FFD700",
            statusError = "#FF3333",
            statusNeutral = "#B0B0B0",
            statusActive = "#00E5FF",
        )
    }
}

/**
 * Standard typography scale definitions for Android presentation.
 */
data class TypographyStyle(
    val sizeSp: Int,
    val lineHeightSp: Int,
    val isBold: Boolean = false,
)

object AndroidTypography {
    val displayMedium = TypographyStyle(sizeSp = 28, lineHeightSp = 36, isBold = true)
    val headlineSmall = TypographyStyle(sizeSp = 24, lineHeightSp = 32, isBold = true)
    val titleLarge = TypographyStyle(sizeSp = 20, lineHeightSp = 26, isBold = true)
    val titleMedium = TypographyStyle(sizeSp = 16, lineHeightSp = 22, isBold = true)
    val titleSmall = TypographyStyle(sizeSp = 14, lineHeightSp = 20, isBold = true)
    val bodyLarge = TypographyStyle(sizeSp = 16, lineHeightSp = 24, isBold = false)
    val bodyMedium = TypographyStyle(sizeSp = 14, lineHeightSp = 20, isBold = false)
    val bodySmall = TypographyStyle(sizeSp = 12, lineHeightSp = 16, isBold = false)
    val labelLarge = TypographyStyle(sizeSp = 14, lineHeightSp = 20, isBold = true)
    val labelMedium = TypographyStyle(sizeSp = 12, lineHeightSp = 16, isBold = true)
    val labelSmall = TypographyStyle(sizeSp = 11, lineHeightSp = 14, isBold = false)
}

/**
 * Spacing grid tokens (8-point grid with 4-point half steps) in density-independent pixels.
 */
object AndroidSpacing {
    const val noneDp: Int = 0
    const val xsDp: Int = 4
    const val smDp: Int = 8
    const val mdDp: Int = 16
    const val lgDp: Int = 24
    const val xlDp: Int = 32
    const val xxlDp: Int = 48
}

/**
 * Elevation tokens for depth hierarchy.
 */
object AndroidElevation {
    const val level0Dp: Int = 0
    const val level1Dp: Int = 1
    const val level2Dp: Int = 3
    const val level3Dp: Int = 6
    const val level4Dp: Int = 8
    const val level5Dp: Int = 12
}

/**
 * Corner radius shapes in density-independent pixels.
 */
object AndroidShapes {
    const val noneDp: Int = 0
    const val smallDp: Int = 8
    const val mediumDp: Int = 12
    const val largeDp: Int = 16
    const val extraLargeDp: Int = 28
    const val pillDp: Int = 999
}

/**
 * Touch target sizing requirements.
 * Per WCAG 2.5.5 and Android accessibility guidelines, minimum touch target is 48 dp x 48 dp.
 */
object AndroidTouchTargets {
    const val minTouchTargetDp: Int = 48
    const val recommendedTouchTargetDp: Int = 56
}
