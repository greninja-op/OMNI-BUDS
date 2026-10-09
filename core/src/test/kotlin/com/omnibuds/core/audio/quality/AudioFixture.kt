package com.omnibuds.core.audio.quality

import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic synthetic PCM fixture.
 *
 * Phase 33: test-only. OmniBuds never processes audio samples in
 * production; these fixtures verify the *framework's own* analysis
 * utilities, never real earbud quality.
 */
data class AudioFixture(
    val fixtureId: String,
    val fixtureVersion: Int,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int,
    /** Interleaved samples, normalized to [-1.0, 1.0]. */
    val samples: DoubleArray,
    val provenance: String,
) {
    init {
        require(sampleRateHz > 0) { "sample rate must be positive" }
        require(channelCount in 1..2) { "channel count must be 1 or 2" }
        require(bitDepth in setOf(16, 24, 32)) { "bit depth must be 16, 24, or 32" }
        require(samples.size % channelCount == 0) { "samples must align to channels" }
    }

    val frameCount: Int get() = samples.size / channelCount

    fun channel(c: Int): DoubleArray =
        DoubleArray(frameCount) { f -> samples[f * channelCount + c] }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFixture) return false
        return fixtureId == other.fixtureId && fixtureVersion == other.fixtureVersion &&
            sampleRateHz == other.sampleRateHz && channelCount == other.channelCount &&
            bitDepth == other.bitDepth && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var r = fixtureId.hashCode()
        r = 31 * r + fixtureVersion
        r = 31 * r + sampleRateHz
        r = 31 * r + channelCount
        r = 31 * r + bitDepth
        r = 31 * r + samples.contentHashCode()
        return r
    }
}

/**
 * Deterministic fixture generators. All parameters fixed; no randomness.
 */
object AudioFixtures {

    private const val VERSION = 1
    private const val RATE = 48000
    private const val FRAMES = 4800 // 100 ms at 48 kHz

    fun silence(): AudioFixture = AudioFixture(
        fixtureId = "silence",
        fixtureVersion = VERSION,
        sampleRateHz = RATE,
        channelCount = 2,
        bitDepth = 16,
        samples = DoubleArray(FRAMES * 2),
        provenance = "synthetic: all zeros",
    )

    fun sineWave(
        frequencyHz: Double = 1000.0,
        amplitude: Double = 0.5,
    ): AudioFixture {
        val samples = DoubleArray(FRAMES * 2)
        for (f in 0 until FRAMES) {
            val v = amplitude * sin(2.0 * PI * frequencyHz * f / RATE)
            samples[f * 2] = v
            samples[f * 2 + 1] = v
        }
        return AudioFixture(
            fixtureId = "sine-1khz",
            fixtureVersion = VERSION,
            sampleRateHz = RATE,
            channelCount = 2,
            bitDepth = 16,
            samples = samples,
            provenance = "synthetic: $frequencyHz Hz sine, amplitude $amplitude",
        )
    }

    fun leftOnly(): AudioFixture {
        val samples = DoubleArray(FRAMES * 2)
        for (f in 0 until FRAMES) {
            samples[f * 2] = 0.5 * sin(2.0 * PI * 1000.0 * f / RATE)
            samples[f * 2 + 1] = 0.0
        }
        return AudioFixture(
            fixtureId = "left-only",
            fixtureVersion = VERSION,
            sampleRateHz = RATE,
            channelCount = 2,
            bitDepth = 16,
            samples = samples,
            provenance = "synthetic: left channel sine, right channel silence",
        )
    }

    fun clipped(): AudioFixture {
        val samples = DoubleArray(FRAMES * 2) { 1.0 } // full-scale DC = clipped
        return AudioFixture(
            fixtureId = "clipped",
            fixtureVersion = VERSION,
            sampleRateHz = RATE,
            channelCount = 2,
            bitDepth = 16,
            samples = samples,
            provenance = "synthetic: full-scale constant (clipped by definition)",
        )
    }

    fun discontinuity(): AudioFixture {
        val samples = DoubleArray(FRAMES * 2) { f ->
            val frame = f / 2
            if (frame < FRAMES / 2) 0.5 else -0.5
        }
        return AudioFixture(
            fixtureId = "discontinuity",
            fixtureVersion = VERSION,
            sampleRateHz = RATE,
            channelCount = 2,
            bitDepth = 16,
            samples = samples,
            provenance = "synthetic: abrupt 0.5 to -0.5 step mid-buffer",
        )
    }

    fun impulse(): AudioFixture {
        val samples = DoubleArray(FRAMES * 2)
        samples[0] = 1.0
        samples[1] = 1.0
        return AudioFixture(
            fixtureId = "impulse",
            fixtureVersion = VERSION,
            sampleRateHz = RATE,
            channelCount = 2,
            bitDepth = 16,
            samples = samples,
            provenance = "synthetic: single full-scale impulse at frame 0",
        )
    }
}
