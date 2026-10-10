package com.omnibuds.core.presentation.accessibility

/**
 * Universal accessibility specification and WCAG verification helpers for OmniBuds.
 */
object AccessibilitySpec {

    /** Minimum recommended touch target size on touch-first platforms (Android) per WCAG 2.5.5. */
    const val MIN_TOUCH_TARGET_DP: Int = 48

    /** Minimum target size for desktop mouse/pointer environments. */
    const val MIN_DESKTOP_TARGET_PX: Int = 24

    /** WCAG 2.1 Level AA minimum contrast ratio for normal text. */
    const val WCAG_AA_NORMAL_TEXT_CONTRAST: Double = 4.5

    /** WCAG 2.1 Level AA minimum contrast ratio for large text. */
    const val WCAG_AA_LARGE_TEXT_CONTRAST: Double = 3.0

    /** WCAG 2.1 Level AAA minimum contrast ratio for normal text. */
    const val WCAG_AAA_NORMAL_TEXT_CONTRAST: Double = 7.0

    /**
     * Calculates the relative luminance of a sRGB hex color string (#RRGGBB).
     */
    fun calculateLuminance(hexColor: String): Double {
        val clean = hexColor.removePrefix("#")
        require(clean.length == 6) { "Expected 6-character hex color #RRGGBB, was $hexColor" }

        val r = clean.substring(0, 2).toInt(16) / 255.0
        val g = clean.substring(2, 4).toInt(16) / 255.0
        val b = clean.substring(4, 6).toInt(16) / 255.0

        val rLinear = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gLinear = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bLinear = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Calculates contrast ratio between two hex colors: (L1 + 0.05) / (L2 + 0.05).
     */
    fun calculateContrastRatio(color1: String, color2: String): Double {
        val lum1 = calculateLuminance(color1)
        val lum2 = calculateLuminance(color2)
        val lighter = Math.max(lum1, lum2)
        val darker = Math.min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Checks if contrast between foreground and background passes WCAG AA for normal text.
     */
    fun passesWcagAa(foreground: String, background: String): Boolean =
        calculateContrastRatio(foreground, background) >= WCAG_AA_NORMAL_TEXT_CONTRAST

    /**
     * Checks if contrast between foreground and background passes WCAG AAA for normal text.
     */
    fun passesWcagAaa(foreground: String, background: String): Boolean =
        calculateContrastRatio(foreground, background) >= WCAG_AAA_NORMAL_TEXT_CONTRAST
}
