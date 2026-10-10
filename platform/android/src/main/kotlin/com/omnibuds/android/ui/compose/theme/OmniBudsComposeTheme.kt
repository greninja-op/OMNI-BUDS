package com.omnibuds.android.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.omnibuds.android.presentation.theme.AndroidColors
import com.omnibuds.android.presentation.theme.AndroidThemeMode

private fun parseColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val colorInt = clean.toLong(16)
    return if (clean.length == 6) {
        Color(colorInt or 0x00000000FF000000L)
    } else {
        Color(colorInt)
    }
}

val OmniBudsDarkColorScheme = darkColorScheme(
    primary = parseColor(AndroidColors.Dark.primary),
    onPrimary = parseColor(AndroidColors.Dark.onPrimary),
    primaryContainer = parseColor(AndroidColors.Dark.primaryContainer),
    onPrimaryContainer = parseColor(AndroidColors.Dark.onPrimaryContainer),
    surface = parseColor(AndroidColors.Dark.surface),
    onSurface = parseColor(AndroidColors.Dark.onSurface),
    surfaceVariant = parseColor(AndroidColors.Dark.surfaceVariant),
    onSurfaceVariant = parseColor(AndroidColors.Dark.onSurfaceVariant),
    background = parseColor(AndroidColors.Dark.background),
    onBackground = parseColor(AndroidColors.Dark.onBackground),
    outline = parseColor(AndroidColors.Dark.outline),
    outlineVariant = parseColor(AndroidColors.Dark.outlineVariant),
)

val OmniBudsLightColorScheme = lightColorScheme(
    primary = parseColor(AndroidColors.Light.primary),
    onPrimary = parseColor(AndroidColors.Light.onPrimary),
    primaryContainer = parseColor(AndroidColors.Light.primaryContainer),
    onPrimaryContainer = parseColor(AndroidColors.Light.onPrimaryContainer),
    surface = parseColor(AndroidColors.Light.surface),
    onSurface = parseColor(AndroidColors.Light.onSurface),
    surfaceVariant = parseColor(AndroidColors.Light.surfaceVariant),
    onSurfaceVariant = parseColor(AndroidColors.Light.onSurfaceVariant),
    background = parseColor(AndroidColors.Light.background),
    onBackground = parseColor(AndroidColors.Light.onBackground),
    outline = parseColor(AndroidColors.Light.outline),
    outlineVariant = parseColor(AndroidColors.Light.outlineVariant),
)

val OmniBudsHighContrastColorScheme = darkColorScheme(
    primary = parseColor(AndroidColors.HighContrast.primary),
    onPrimary = parseColor(AndroidColors.HighContrast.onPrimary),
    primaryContainer = parseColor(AndroidColors.HighContrast.primaryContainer),
    onPrimaryContainer = parseColor(AndroidColors.HighContrast.onPrimaryContainer),
    surface = parseColor(AndroidColors.HighContrast.surface),
    onSurface = parseColor(AndroidColors.HighContrast.onSurface),
    surfaceVariant = parseColor(AndroidColors.HighContrast.surfaceVariant),
    onSurfaceVariant = parseColor(AndroidColors.HighContrast.onSurfaceVariant),
    background = parseColor(AndroidColors.HighContrast.background),
    onBackground = parseColor(AndroidColors.HighContrast.onBackground),
    outline = parseColor(AndroidColors.HighContrast.outline),
    outlineVariant = parseColor(AndroidColors.HighContrast.outlineVariant),
)

@Composable
fun OmniBudsTheme(
    themeMode: AndroidThemeMode = AndroidThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AndroidThemeMode.SYSTEM -> isSystemInDarkTheme()
        AndroidThemeMode.DARK -> true
        AndroidThemeMode.LIGHT -> false
        AndroidThemeMode.HIGH_CONTRAST -> true
    }

    val colorScheme = when {
        themeMode == AndroidThemeMode.HIGH_CONTRAST -> OmniBudsHighContrastColorScheme
        darkTheme -> OmniBudsDarkColorScheme
        else -> OmniBudsLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
