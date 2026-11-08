@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

enum class Soundscape(val title: String) {
    RAIN("Rain"),
    OCEAN("Ocean Waves"),
    FOREST("Deep Forest"),
    CAMPFIRE("Campfire"),
    PINK_NOISE("Pink Noise"),
}

/**
 * Procedural Real-Time Audio Synthesizer for "Mind Relax" Ambient Soundscapes.
 * Provides authentic, loop-free, zero-asset relaxation soundscapes.
 */
class MindRelaxGenerator(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "MindRelaxGenerator"
        private const val SAMPLE_RATE = 24000
    }

    private var activeJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private val random = Random()

    var isPlaying: Boolean = false
        private set

    var currentSoundscape: Soundscape = Soundscape.RAIN
        private set

    var volume: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 1f)
            audioTrack?.setVolume(field)
        }

    fun play(soundscape: Soundscape, durationMinutes: Int = 0) {
        stop()
        currentSoundscape = soundscape
        isPlaying = true

        activeJob = scope.launch(Dispatchers.IO) {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val chunkSize = maxOf(minBufSize, 4096)
            val track = AudioTrack.Builder()
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
                .setBufferSizeInBytes(chunkSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.setVolume(volume)
            track.play()

            val pcmBuffer = ShortArray(chunkSize)
            val startTime = System.currentTimeMillis()
            val maxDurationMs = if (durationMinutes > 0) durationMinutes * 60 * 1000L else Long.MAX_VALUE

            // Pink noise filter state
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var phase = 0.0

            try {
                while (isActive && isPlaying) {
                    if (System.currentTimeMillis() - startTime >= maxDurationMs) break

                    for (i in 0 until chunkSize step 2) {
                        val white = (random.nextDouble() * 2.0 - 1.0)

                        // 3-pole Voss-McCartney filter for pink noise
                        b0 = 0.99765 * b0 + white * 0.0990460
                        b1 = 0.96300 * b1 + white * 0.2965164
                        b2 = 0.57000 * b2 + white * 1.0526913
                        val pink = (b0 + b1 + b2 + white * 0.1848) * 0.05

                        val sampleVal: Double = when (soundscape) {
                            Soundscape.RAIN -> {
                                val drop = if (random.nextInt(300) == 0) (random.nextDouble() * 0.4) else 0.0
                                (pink * 0.7 + drop) * Short.MAX_VALUE
                            }
                            Soundscape.OCEAN -> {
                                phase += 2.0 * PI * 0.12 / SAMPLE_RATE // 0.12 Hz swell cycle
                                val swell = (sin(phase) + 1.0) * 0.5
                                (pink * (0.3 + 0.7 * swell)) * Short.MAX_VALUE
                            }
                            Soundscape.CAMPFIRE -> {
                                val crackle = if (random.nextInt(600) == 0) (random.nextDouble() * 0.6) else 0.0
                                (pink * 0.4 + crackle) * Short.MAX_VALUE
                            }
                            Soundscape.FOREST -> {
                                val chirp = if (random.nextInt(2500) == 0) sin(phase * 15.0) * 0.2 else 0.0
                                (pink * 0.35 + chirp) * Short.MAX_VALUE
                            }
                            Soundscape.PINK_NOISE -> {
                                pink * Short.MAX_VALUE
                            }
                        }

                        val s = sampleVal.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                        pcmBuffer[i] = s
                        pcmBuffer[i + 1] = s
                    }

                    track.write(pcmBuffer, 0, chunkSize)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Audio stream terminated: ${e.message}")
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                isPlaying = false
            }
        }
    }

    fun stop() {
        isPlaying = false
        activeJob?.cancel()
        activeJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
