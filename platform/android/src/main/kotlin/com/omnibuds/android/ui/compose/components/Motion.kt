package com.omnibuds.android.ui.compose.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether full motion is appropriate on this device right now.
 *
 * Follows the system animator duration scale: when the user (or the
 * device, on very low-end hardware profiles) has animations reduced or
 * disabled, OmniBuds renders state changes instantly instead of
 * animating. All animation in the app is transform/alpha-only and gates
 * on this, so nothing ever taxes the hardware with layout work.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        } catch (_: Exception) {
            true
        }
    }
}
