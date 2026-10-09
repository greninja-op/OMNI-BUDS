package com.omnibuds.core.audio.quality

/**
 * Timing-measurement abstraction for audio-adjacent latencies.
 *
 * Phase 33: distinguishes application-observed timings (command dispatch,
 * vendor-control response, state-observation delay) from real acoustic
 * latency — which this framework never claims to measure.
 */
interface MonotonicClock {
    /** Current monotonic time in nanoseconds. */
    fun nowNanos(): Long
}

/** Production clock backed by System.nanoTime. */
object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

/** Deterministic fake clock for tests. */
class FakeClock(var timeNanos: Long = 0L) : MonotonicClock {
    override fun nowNanos(): Long = timeNanos
    fun advance(millis: Long) { timeNanos += millis * 1_000_000L }
}

/** What was being timed. Never collapsed into one generic "latency". */
enum class TimingCategory {
    COMMAND_DISPATCH,
    VENDOR_CONTROL_RESPONSE,
    STATE_OBSERVATION_DELAY,
    CONNECTION_SETUP,
    CODEC_STATE_UPDATE,
}

/** A single completed or incomplete timing measurement. */
data class TimingMeasurement(
    val category: TimingCategory,
    val startNanos: Long?,
    val endNanos: Long?,
) {
    /** Elapsed milliseconds, or null when start/end is missing. */
    val elapsedMillis: Double?
        get() = if (startNanos != null && endNanos != null && endNanos >= startNanos) {
            (endNanos - startNanos) / 1_000_000.0
        } else {
            null
        }

    val isComplete: Boolean get() = elapsedMillis != null
}

/** Measures elapsed time between two events on the given clock. */
class TimingMeasurer(private val clock: MonotonicClock) {
    private var startNanos: Long? = null

    fun start() { startNanos = clock.nowNanos() }
    fun reset() { startNanos = null }

    fun stop(category: TimingCategory): TimingMeasurement {
        val s = startNanos
        val e = if (s != null) clock.nowNanos() else null
        startNanos = null
        return TimingMeasurement(category, s, e)
    }
}
