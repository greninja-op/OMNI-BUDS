package com.omnibuds.android.hardware.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Controller managing specialized hardware interactions for Non-TWS form factors:
 * 1. Neckband Magnetic Hall-Switch auto-play / instant-pause
 * 2. Neckband Collar Vibration motor alerts & pulse testing
 * 3. Over-Ear Speak-to-Chat voice activity detection & timeout
 * 4. Over-Ear Earcup clamping / atmospheric pressure calibration
 * 5. Auto Power-Off / Standby Timer
 */
class NonTwsHardwareController {

    companion object {
        private const val TAG = "NonTwsController"
    }

    enum class SpeakToChatSensitivity(val label: String) {
        AUTO("Auto"),
        HIGH("High"),
        LOW("Low"),
    }

    enum class SpeakToChatPauseTime(val label: String, val durationMs: Long) {
        SHORT_5S("Short (5s)", 5_000L),
        STANDARD_15S("Standard (15s)", 15_000L),
        LONG_30S("Long (30s)", 30_000L),
        DONT_CLOSE("Don't Close Automatically", -1L),
    }

    enum class AutoStandbyTime(val label: String, val minutes: Int) {
        NEVER("Never turn off", 0),
        MIN_5("5 Minutes", 5),
        MIN_15("15 Minutes", 15),
        MIN_30("30 Minutes", 30),
        HOUR_1("1 Hour", 60),
        HOUR_3("3 Hours", 180),
    }

    // --- Neckband Controls ---
    var isAutoPlayOnSeparateEnabled: Boolean = true
    var isInstantPauseOnClaspEnabled: Boolean = true
    var isCollarVibrationEnabled: Boolean = true

    var onMediaPlayRequest: (() -> Unit)? = null
    var onMediaPauseRequest: (() -> Unit)? = null

    // --- Over-Ear Controls ---
    var isSpeakToChatEnabled: Boolean = false
    var speakToChatSensitivity: SpeakToChatSensitivity = SpeakToChatSensitivity.AUTO
    var speakToChatPauseTime: SpeakToChatPauseTime = SpeakToChatPauseTime.STANDARD_15S

    var autoStandbyTime: AutoStandbyTime = AutoStandbyTime.NEVER

    var isClampingOptimizerRunning: Boolean = false
        private set

    /**
     * Handles Bluetooth A2DP / ACL reconnect or magnet unclasps.
     */
    fun onMagneticSeparated() {
        if (isAutoPlayOnSeparateEnabled) {
            Log.i(TAG, "Magnets separated: requesting auto-resume within 400ms")
            onMediaPlayRequest?.invoke()
        }
    }

    /**
     * Handles Bluetooth disconnect or magnet snaps.
     */
    fun onMagneticClasped() {
        if (isInstantPauseOnClaspEnabled) {
            Log.i(TAG, "Magnets clasped: requesting instant pause")
            onMediaPauseRequest?.invoke()
        }
    }

    /**
     * Vibrates the neckband collar motor or test pulse via phone vibrator.
     */
    fun triggerVibrationPulse(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 150, 100, 250)
                    val amplitudes = intArrayOf(0, 200, 0, 255)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(400)
                }
            }
            Log.i(TAG, "Collar vibration alert pulse triggered")
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    /**
     * Simulates atmospheric pressure / clamping optimizer calibration.
     */
    suspend fun runClampingOptimizer(onProgress: (Float) -> Unit, onComplete: () -> Unit) {
        isClampingOptimizerRunning = true
        try {
            for (step in 1..20) {
                kotlinx.coroutines.delay(100)
                onProgress(step / 20f)
            }
            onComplete()
        } finally {
            isClampingOptimizerRunning = false
        }
    }
}

