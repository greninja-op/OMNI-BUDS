package com.omnibuds.android.presentation.theme

import com.omnibuds.core.presentation.accessibility.AccessibilitySpec
import com.omnibuds.core.presentation.theme.OmniBudsColors
import com.omnibuds.core.presentation.theme.OmniBudsElevation
import com.omnibuds.core.presentation.theme.OmniBudsShapes
import com.omnibuds.core.presentation.theme.OmniBudsSpacing

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
 * Directly adapts unified core design tokens (com.omnibuds.core.presentation.theme.OmniBudsColors).
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
        fun fromCore(core: OmniBudsColors): AndroidColors = AndroidColors(
            background = core.background,
            surface = core.surface,
            surfaceElevated = core.surfaceElevated,
            surfaceVariant = core.surfaceVariant,
            onBackground = core.onBackground,
            onSurface = core.onSurface,
            onSurfaceVariant = core.onSurfaceVariant,
            primary = core.primary,
            onPrimary = core.onPrimary,
            primaryContainer = core.primaryContainer,
            onPrimaryContainer = core.onPrimaryContainer,
            outline = core.outline,
            outlineVariant = core.outlineVariant,
            statusAvailable = core.statusAvailable,
            statusWarning = core.statusWarning,
            statusError = core.statusError,
            statusNeutral = core.statusNeutral,
            statusActive = core.statusActive,
        )

        val Dark: AndroidColors = fromCore(OmniBudsColors.Dark)
        val Light: AndroidColors = fromCore(OmniBudsColors.Light)
        val HighContrast: AndroidColors = fromCore(OmniBudsColors.HighContrast)
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
 * Backed by unified core spacing tokens.
 */
object AndroidSpacing {
    const val noneDp: Int = OmniBudsSpacing.space0
    const val xsDp: Int = OmniBudsSpacing.space4
    const val smDp: Int = OmniBudsSpacing.space8
    const val mdDp: Int = OmniBudsSpacing.space16
    const val lgDp: Int = OmniBudsSpacing.space24
    const val xlDp: Int = OmniBudsSpacing.space32
    const val xxlDp: Int = OmniBudsSpacing.space48
}

/**
 * Elevation tokens for depth hierarchy.
 * Backed by unified core elevation tokens.
 */
object AndroidElevation {
    const val level0Dp: Int = OmniBudsElevation.level0
    const val level1Dp: Int = OmniBudsElevation.level1
    const val level2Dp: Int = OmniBudsElevation.level2
    const val level3Dp: Int = OmniBudsElevation.level3
    const val level4Dp: Int = OmniBudsElevation.level4
    const val level5Dp: Int = OmniBudsElevation.level5
}

/**
 * Corner radius shapes in density-independent pixels.
 * Backed by unified core shape tokens.
 */
object AndroidShapes {
    const val noneDp: Int = OmniBudsShapes.radiusNone
    const val smallDp: Int = OmniBudsShapes.radiusMedium
    const val mediumDp: Int = OmniBudsShapes.radiusLarge
    const val largeDp: Int = OmniBudsShapes.radiusExtraLarge
    const val extraLargeDp: Int = 28
    const val pillDp: Int = OmniBudsShapes.radiusPill
}

/**
 * Touch target sizing requirements.
 * Per WCAG 2.5.5 and Android accessibility guidelines, minimum touch target is 48 dp x 48 dp.
 */
object AndroidTouchTargets {
    const val minTouchTargetDp: Int = AccessibilitySpec.MIN_TOUCH_TARGET_DP
    const val recommendedTouchTargetDp: Int = 56
}
