package com.omnibuds.core.audio.quality

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Offline signal-integrity analysis for synthetic fixtures.
 *
 * Phase 33: test-only, deterministic, offline. Every metric documents
 * its input, units, formula, range, empty/invalid handling, and
 * tolerance. Results never represent hardware quality.
 */
object SignalAnalysis {

    /** Full-scale reference: samples are normalized to [-1.0, 1.0]. */
    const val FULL_SCALE = 1.0

    /** Silence threshold: below this RMS the signal counts as silent. */
    const val SILENCE_THRESHOLD = 1e-4

    /** Clipping threshold: |sample| at or above this counts as clipped. */
    const val CLIP_THRESHOLD = 0.999

    /** Numerical tolerance for floating-point comparisons. */
    const val TOLERANCE = 1e-9

    /** Peak absolute amplitude. Range [0, 1]. Empty input -> 0. */
    fun peak(samples: DoubleArray): Double {
        var p = 0.0
        for (s in samples) {
            val a = abs(s)
            if (a > p) p = a
        }
        return p
    }

    /** RMS amplitude. Range [0, 1]. Empty input -> 0. */
    fun rms(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size)
    }

    /** True when RMS is below the silence threshold. */
    fun isSilent(samples: DoubleArray): Boolean = rms(samples) < SILENCE_THRESHOLD

    /** Number of samples at or beyond the clipping threshold. */
    fun clippingCount(samples: DoubleArray): Int =
        samples.count { abs(it) >= CLIP_THRESHOLD }

    /** Clipping ratio in [0, 1]. Empty input -> 0. */
    fun clippingRatio(samples: DoubleArray): Double =
        if (samples.isEmpty()) 0.0 else clippingCount(samples).toDouble() / samples.size

    /**
     * Counts abrupt discontinuities: adjacent-sample jumps exceeding
     * [threshold]. For a clean sine at low frequency this is 0.
     */
    fun discontinuityCount(samples: DoubleArray, threshold: Double = 0.5): Int {
        require(threshold > 0) { "threshold must be positive" }
        var count = 0
        for (i in 1 until samples.size) {
            if (abs(samples[i] - samples[i - 1]) > threshold) count++
        }
        return count
    }

    /** True when both channels carry identical data (dual mono). */
    fun channelsIdentical(fixture: AudioFixture): Boolean {
        if (fixture.channelCount != 2) return false
        val left = fixture.channel(0)
        val right = fixture.channel(1)
        return left.indices.all { abs(left[it] - right[it]) <= TOLERANCE }
    }

    /**
     * Sample-by-sample comparison against expected data.
     * Returns the number of mismatched samples beyond [tolerance].
     */
    fun mismatchCount(
        actual: DoubleArray,
        expected: DoubleArray,
        tolerance: Double = TOLERANCE,
    ): Int {
        if (actual.size != expected.size) return maxOf(actual.size, expected.size)
        return actual.indices.count { abs(actual[it] - expected[it]) > tolerance }
    }
}
