@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

enum class EarChannel {
    LEFT,
    RIGHT,
}

enum class HeadphoneAccommodationPreset(val label: String) {
    OFF("Off"),
    BALANCED_TONE("Balanced Tone"),
    VOCAL_RANGE("Vocal Range"),
    BRIGHTNESS("Brightness"),
}

enum class AccommodationIntensity(val label: String, val boostDb: Float) {
    SLIGHT("Slight", 2.0f),
    MODERATE("Moderate", 4.0f),
    STRONG("Strong", 6.0f),
}

/**
 * Golden Sound & Apple Headphone Accommodations Pure-Tone Audiogram Test Engine.
 *
 * Measures hearing threshold response across 7 ISO test frequencies:
 * 125Hz, 250Hz, 500Hz, 1000Hz, 2000Hz, 4000Hz, 8000Hz for Left & Right ears.
 * Generates an individualized DSP compensation curve.
 */
class HearingTestManager {

    companion object {
        private const val TAG = "HearingTestManager"
        val TEST_FREQUENCIES = intArrayOf(125, 250, 500, 1000, 2000, 4000, 8000)
        private const val SAMPLE_RATE = 44100
    }

    private var currentTrack: AudioTrack? = null

    // Threshold offsets relative to normal hearing: key = "LEFT:1000", value = dB threshold
    private val leftThresholds = mutableMapOf<Int, Float>()
    private val rightThresholds = mutableMapOf<Int, Float>()

    var activePreset: HeadphoneAccommodationPreset = HeadphoneAccommodationPreset.OFF
    var activeIntensity: AccommodationIntensity = AccommodationIntensity.MODERATE

    /**
     * Plays a calibrated pure sine tone at [freqHz] into [ear] at [volumeDb] for [durationMs].
     */
    suspend fun playTestTone(
        freqHz: Int,
        ear: EarChannel,
        volumeDb: Float = -24f, // -60dB (quiet) to 0dB (full scale)
        durationMs: Int = 1200,
    ) = withContext(Dispatchers.IO) {
        stopTone()
        try {
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples * 2) // Stereo short PCM

            // Convert volumeDb to linear amplitude (0.0 to 1.0)
            val amplitude = 10.0.pow(volumeDb / 20.0).coerceIn(0.0001, 0.95)
            val maxShort = Short.MAX_VALUE.toDouble() * amplitude

            // Apply 50ms fade-in and 50ms fade-out envelope to avoid audible clicks
            val fadeSamples = (SAMPLE_RATE * 0.05).toInt()

            for (i in 0 until numSamples) {
                val time = i.toDouble() / SAMPLE_RATE
                val rawSine = sin(2.0 * PI * freqHz * time)

                val envelope = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                    else -> 1.0
                }

                val sample = (rawSine * maxShort * envelope).toInt().toShort()

                // Interleaved stereo PCM: [L, R, L, R...]
                val leftIdx = i * 2
                val rightIdx = leftIdx + 1
                if (ear == EarChannel.LEFT) {
                    buffer[leftIdx] = sample
                    buffer[rightIdx] = 0
                } else {
                    buffer[leftIdx] = 0
                    buffer[rightIdx] = sample
                }
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            currentTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(buffer.size * 2, minBufferSize))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            currentTrack?.write(buffer, 0, buffer.size)
            currentTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play pure tone: ${e.message}")
        }
    }

    fun stopTone() {
        try {
            currentTrack?.stop()
            currentTrack?.release()
        } catch (_: Exception) {}
        currentTrack = null
    }

    fun recordHearingResult(ear: EarChannel, freqHz: Int, heardDb: Float) {
        if (ear == EarChannel.LEFT) {
            leftThresholds[freqHz] = heardDb
        } else {
            rightThresholds[freqHz] = heardDb
        }
    }

    /**
     * Computes the 10-band EQ compensation curve from test results and active Apple Accommodations.
     * Can generate an individualized curve per ear channel.
     */
    fun computeCompensationGains(ear: EarChannel? = null): FloatArray {
        // Standard 10 band center frequencies:
        // [31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1000Hz, 2000Hz, 4000Hz, 8000Hz, 16000Hz]
        val curve = FloatArray(10) { 0f }

        // 1. Calculate hearing deficit from recorded audiogram
        for (i in TEST_FREQUENCIES.indices) {
            val freq = TEST_FREQUENCIES[i]
            val left = leftThresholds[freq]
            val right = rightThresholds[freq]

            val threshold = when (ear) {
                EarChannel.LEFT -> left ?: right ?: -36f
                EarChannel.RIGHT -> right ?: left ?: -36f
                null -> if (left != null || right != null) ((left ?: -36f) + (right ?: -36f)) / 2f else null
            }

            if (threshold != null) {
                // If threshold is worse than -36dB (e.g. -18dB), apply proportional compensation
                val deficit = (threshold - (-36f)).coerceIn(0f, 15f)
                val compensationDb = (deficit * 0.4f).coerceAtMost(6.0f)

                // Map test freq to 10-band indices
                val targetBand = when (freq) {
                    125 -> 2
                    250 -> 3
                    500 -> 4
                    1000 -> 5
                    2000 -> 6
                    4000 -> 7
                    8000 -> 8
                    else -> 5
                }
                curve[targetBand] += compensationDb
            }
        }

        // 2. Apply Apple Headphone Accommodations Presets
        val boost = activeIntensity.boostDb
        when (activePreset) {
            HeadphoneAccommodationPreset.BALANCED_TONE -> {
                for (b in 3..8) curve[b] += boost * 0.6f
            }
            HeadphoneAccommodationPreset.VOCAL_RANGE -> {
                curve[4] += boost * 0.6f // 500Hz
                curve[5] += boost        // 1kHz
                curve[6] += boost        // 2kHz
                curve[7] += boost * 0.5f // 4kHz
            }
            HeadphoneAccommodationPreset.BRIGHTNESS -> {
                curve[6] += boost * 0.4f // 2kHz
                curve[7] += boost * 0.8f // 4kHz
                curve[8] += boost        // 8kHz
                curve[9] += boost * 0.9f // 16kHz
            }
            HeadphoneAccommodationPreset.OFF -> { /* No preset boost */ }
        }

        return curve
    }
}
