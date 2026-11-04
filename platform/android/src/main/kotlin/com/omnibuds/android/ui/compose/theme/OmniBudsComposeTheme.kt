package com.omnibuds.android.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.omnibuds.android.presentation.theme.AndroidThemeMode

/**
 * OmniBuds brand theme (UI redesign, v1.8).
 *
 * A fixed product identity — deep black, charcoal surfaces, one
 * fluorescent-yellow accent — replacing the system dynamic color that
 * made the app inherit the phone wallpaper's blue. The app is dark by
 * design now: [themeMode] is accepted for call-site compatibility but
 * the brand scheme is always applied.
 *
 * Accent discipline: OmniYellow marks selection, active hardware modes,
 * primary actions and the connected state. It is never decoration.
 * Battery keeps its restrained green; warnings amber; errors red.
 */
object OmniBrand {
    val Background = Color(0xFF050506) // deep black
    val Surface = Color(0xFF101013) // near-black charcoal
    val SurfaceVariant = Color(0xFF17171B) // card charcoal
    val SurfaceElevated = Color(0xFF1E1E23) // elevated charcoal
    val SurfaceHighest = Color(0xFF26262C)
    val Accent = Color(0xFFE9FF3C) // fluorescent yellow
    val OnAccent = Color(0xFF0A0A0A)
    val TextPrimary = Color(0xFFF5F5F0)
    val TextSecondary = Color(0xFF9C9CA3)
    val Border = Color(0xFF26262B)
    val Disabled = Color(0xFF55555C)
    val Success = Color(0xFF34A853)
    val Warning = Color(0xFFFFB020)
    val Error = Color(0xFFFF5449)
}

val OmniBudsBrandColorScheme = darkColorScheme(
    primary = OmniBrand.Accent,
    onPrimary = OmniBrand.OnAccent,
    primaryContainer = OmniBrand.SurfaceHighest,
    onPrimaryContainer = OmniBrand.TextPrimary,
    secondary = OmniBrand.TextSecondary,
    background = OmniBrand.Background,
    onBackground = OmniBrand.TextPrimary,
    surface = OmniBrand.Surface,
    onSurface = OmniBrand.TextPrimary,
    surfaceVariant = OmniBrand.SurfaceVariant,
    onSurfaceVariant = OmniBrand.TextSecondary,
    surfaceContainer = OmniBrand.SurfaceVariant,
    surfaceContainerHigh = OmniBrand.SurfaceElevated,
    surfaceContainerHighest = OmniBrand.SurfaceHighest,
    outline = OmniBrand.Border,
    outlineVariant = OmniBrand.Border,
    error = OmniBrand.Error,
    onError = OmniBrand.TextPrimary,
)

@Composable
fun OmniBudsTheme(
    @Suppress("UNUSED_PARAMETER") themeMode: AndroidThemeMode = AndroidThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OmniBudsBrandColorScheme,
        content = content,
    )
}
