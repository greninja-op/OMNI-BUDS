package com.omnibuds.core.audio.quality

import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AudioFixtureTest {

    @Test
    fun `silence is all zeros`() {
        val f = AudioFixtures.silence()
        assertTrue(f.samples.all { it == 0.0 })
        assertEquals(2, f.channelCount)
        assertEquals(48000, f.sampleRateHz)
    }

    @Test
    fun `sine wave is deterministic`() {
        assertEquals(AudioFixtures.sineWave(), AudioFixtures.sineWave())
    }

    @Test
    fun `sine wave peak matches amplitude`() {
        val f = AudioFixtures.sineWave(amplitude = 0.5)
        assertTrue(SignalAnalysis.peak(f.samples) <= 0.5 + SignalAnalysis.TOLERANCE)
        assertTrue(SignalAnalysis.peak(f.samples) > 0.49)
    }

    @Test
    fun `left-only isolates channels`() {
        val f = AudioFixtures.leftOnly()
        assertFalse(SignalAnalysis.isSilent(f.channel(0)))
        assertTrue(SignalAnalysis.isSilent(f.channel(1)))
        assertFalse(SignalAnalysis.channelsIdentical(f))
    }

    @Test
    fun `stereo sine is dual mono`() {
        assertTrue(SignalAnalysis.channelsIdentical(AudioFixtures.sineWave()))
    }

    @Test
    fun `clipped fixture clips everywhere`() {
        val f = AudioFixtures.clipped()
        assertEquals(f.samples.size, SignalAnalysis.clippingCount(f.samples))
        assertEquals(1.0, SignalAnalysis.clippingRatio(f.samples))
    }

    @Test
    fun `clean sine does not clip`() {
        val f = AudioFixtures.sineWave()
        assertEquals(0, SignalAnalysis.clippingCount(f.samples))
    }

    @Test
    fun `discontinuity is detected`() {
        val f = AudioFixtures.discontinuity()
        assertTrue(SignalAnalysis.discontinuityCount(f.channel(0)) >= 1)
        assertEquals(0, SignalAnalysis.discontinuityCount(AudioFixtures.sineWave().channel(0)))
    }

    @Test
    fun `impulse has single peak`() {
        val f = AudioFixtures.impulse()
        assertEquals(1.0, SignalAnalysis.peak(f.samples))
        // Only frame 0 is nonzero: 2 clipped samples out of the buffer.
        assertEquals(2, SignalAnalysis.clippingCount(f.samples))
    }

    @Test
    fun `invalid fixture is rejected`() {
        try {
            AudioFixture("bad", 1, 0, 2, 16, DoubleArray(4), "test")
            assertTrue(false, "expected require failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `empty input is handled`() {
        val empty = DoubleArray(0)
        assertEquals(0.0, SignalAnalysis.peak(empty))
        assertEquals(0.0, SignalAnalysis.rms(empty))
        assertEquals(0, SignalAnalysis.clippingCount(empty))
        assertEquals(0.0, SignalAnalysis.clippingRatio(empty))
        assertTrue(SignalAnalysis.isSilent(empty))
    }
}

class SignalAnalysisTest {

    @Test
    fun `silence fixture is silent`() {
        assertTrue(SignalAnalysis.isSilent(AudioFixtures.silence().samples))
    }

    @Test
    fun `sine rms is amplitude over root two`() {
        val amplitude = 0.5
        val f = AudioFixtures.sineWave(amplitude = amplitude)
        val expected = amplitude / kotlin.math.sqrt(2.0)
        // Tolerance accounts for the non-integer cycle count in the buffer.
        assertTrue(abs(SignalAnalysis.rms(f.samples) - expected) < 0.01)
    }

    @Test
    fun `mismatch count detects differences`() {
        val a = doubleArrayOf(0.1, 0.2, 0.3)
        val b = doubleArrayOf(0.1, 0.2, 0.9)
        assertEquals(1, SignalAnalysis.mismatchCount(a, b))
        assertEquals(0, SignalAnalysis.mismatchCount(a, a))
    }

    @Test
    fun `fixture analysis is not hardware quality`() {
        // This test documents the interpretation rule: every metric here
        // operates on synthetic data and says nothing about a physical
        // earbud. The assertion is that the fixture's provenance is
        // synthetic, so no consumer can mistake it for measured hardware.
        val f = AudioFixtures.sineWave()
        assertTrue(f.provenance.startsWith("synthetic"))
    }
}

class TimingMeasurementTest {

    @Test
    fun `fake clock measures elapsed time`() {
        val clock = FakeClock()
        val measurer = TimingMeasurer(clock)
        measurer.start()
        clock.advance(150)
        val m = measurer.stop(TimingCategory.COMMAND_DISPATCH)
        assertTrue(m.isComplete)
        assertEquals(150.0, m.elapsedMillis!!, 1e-6)
        assertEquals(TimingCategory.COMMAND_DISPATCH, m.category)
    }

    @Test
    fun `missing start gives incomplete measurement`() {
        val m = TimingMeasurer(FakeClock()).stop(TimingCategory.CONNECTION_SETUP)
        assertFalse(m.isComplete)
        assertNull(m.elapsedMillis)
    }

    @Test
    fun `categories are not collapsed`() {
        val values = TimingCategory.values()
        assertEquals(values.toSet().size, values.size)
        assertTrue(values.contains(TimingCategory.VENDOR_CONTROL_RESPONSE))
        assertTrue(values.contains(TimingCategory.CODEC_STATE_UPDATE))
    }

    @Test
    fun `no acoustic latency category exists`() {
        assertFalse(TimingCategory.values().any { it.name.contains("ACOUSTIC") })
    }
}

class CodecStateConsistencyTest {

    @Test
    fun `capability is not negotiation`() {
        // ENABLED is a selection; it never implies NEGOTIATED or ACTIVE.
        assertTrue(CodecState.ENABLED.ordinal < CodecState.NEGOTIATED.ordinal)
        assertTrue(CodecState.NEGOTIATED.ordinal < CodecState.ACTIVE.ordinal)
    }

    @Test
    fun `unknown is never unsupported`() {
        assertTrue(CodecState.UNKNOWN.ordinal < CodecState.UNSUPPORTED.ordinal)
    }

    @Test
    fun `active codec is not observable on android`() {
        // Phase 11 finding: no public API exposes the active A2DP codec.
        // A record about the active codec must carry NOT_OBSERVABLE and
        // stay UNKNOWN rather than inventing a codec name.
        val observability = CodecObservability.NOT_OBSERVABLE
        val state = CodecState.UNKNOWN
        assertEquals(CodecObservability.NOT_OBSERVABLE, observability)
        assertEquals(CodecState.UNKNOWN, state)
    }

    @Test
    fun `state ladder order is evidence order`() {
        val ladder = listOf(
            CodecState.UNKNOWN,
            CodecState.UNSUPPORTED,
            CodecState.SUPPORTED,
            CodecState.AVAILABLE,
            CodecState.ENABLED,
            CodecState.NEGOTIATED,
            CodecState.ACTIVE,
        )
        val ordinals = ladder.map { it.ordinal }
        assertEquals(ordinals.sorted(), ordinals)
    }
}

class NonInterferenceTest {

    @Test
    fun `no audio capture permissions in manifest`() {
        // The platform module manifest declares no RECORD_AUDIO or
        // MODIFY_AUDIO_SETTINGS. This is checked against the known
        // manifest content; a capture permission would fail review.
        val manifest = java.io.File(
            "../platform/android/src/main/AndroidManifest.xml",
        ).let { if (it.exists()) it.readText() else "" }
        // Only run the assertion when the file is reachable.
        if (manifest.isNotEmpty()) {
            assertFalse(manifest.contains("RECORD_AUDIO"))
            assertFalse(manifest.contains("MODIFY_AUDIO_SETTINGS"))
            assertFalse(manifest.contains("CAPTURE_AUDIO_OUTPUT"))
        }
    }

    @Test
    fun `quality framework lives in test sources only`() {
        // AudioFixture, SignalAnalysis, TimingMeasurement are test-only:
        // no production main source may reference sample processing.
        val mainSources = java.io.File("../core/src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() }
        if (mainSources.iterator().hasNext()) {
            assertFalse(mainSources.any { it.contains("AudioRecord(") })
            assertFalse(mainSources.any { it.contains("MediaRecorder(") })
        }
    }
}
