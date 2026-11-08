package com.omnibuds.android.ui.compose.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.omnibuds.android.presentation.theme.AndroidThemeMode

/**
 * OmniBuds brand theme (UI redesign, v1.8).
 *
 * A fixed product identity — pure black, charcoal surfaces, one
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
    val Background = Color(0xFF000000) // pure black
    val Surface = Color(0xFF17171B) // primary surface
    val SurfaceVariant = Color(0xFF222228) // secondary surface
    val SurfaceElevated = Color(0xFF26262C) // elevated charcoal
    val SurfaceHighest = Color(0xFF2E2E35)
    val Accent = Color(0xFFE5FF36) // fluorescent yellow
    val OnAccent = Color(0xFF0A0A0A)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA1A1AA)
    val Border = Color(0xFF303036)
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.BLACK
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = OmniBudsBrandColorScheme,
        content = content,
    )
}
