package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unknown must stay unknown in the quality snapshot (REQ-P1-007, REQ-P1-010,
 * AUD-QUAL-001/002/005, Phase 1 prompt section 19).
 */
class AudioTransportStateTest {

    /**
     * Nothing has been read, so nothing is asserted: no 96 kHz, no 24-bit, no
     * 328 kbps, no zero, no default codec and no "A2DP".
     */
    @Test
    fun unobservedStateCarriesNoFabricatedNumbers() {
        val snapshot = AudioTransportState.unobserved()

        assertNull(snapshot.sampleRateHz)
        assertNotEquals(96_000, snapshot.sampleRateHz)
        assertNotEquals(48_000, snapshot.sampleRateHz)
        assertNull(snapshot.bitsPerSample)
        assertNotEquals(24, snapshot.bitsPerSample)
        assertNotEquals(16, snapshot.bitsPerSample)
        assertNull(snapshot.bitrateKbps)
        assertNotEquals(328, snapshot.bitrateKbps)
        assertNotEquals(330_000, snapshot.bitrateKbps)

        assertNull(snapshot.codec)
        assertEquals(AudioTransportKind.UNKNOWN, snapshot.transport)
        assertEquals(CodecState.UNKNOWN, snapshot.state)
        assertEquals(ChannelMode.UNKNOWN, snapshot.channelMode)
        assertEquals(QualityMode.UNKNOWN, snapshot.qualityMode)
        assertFalse(snapshot.isActive)
        assertFalse(snapshot.isFullyObserved)
    }

    /** A partial read is a valid result: what was read is kept, what was not stays unknown. */
    @Test
    fun partiallyReportedStateKeepsItsUnknownsUnknown() {
        val reported = AudioTransportState(
            transport = AudioTransportKind.CLASSIC_A2DP,
            codec = Codec.LDAC,
            state = CodecState.NEGOTIATED,
            sampleRateHz = 44_100,
            bitsPerSample = null,
            bitrateKbps = null,
            channelMode = ChannelMode.UNKNOWN,
            qualityMode = QualityMode.UNKNOWN,
        )

        assertEquals(44_100, reported.sampleRateHz)
        assertNull(reported.bitsPerSample)
        assertNull(reported.bitrateKbps)
        assertEquals(ChannelMode.UNKNOWN, reported.channelMode)
        assertEquals(QualityMode.UNKNOWN, reported.qualityMode)
        assertFalse(reported.isFullyObserved)
        // Negotiated is not active, however much of the rest has been read (AUD-QUAL-004).
        assertFalse(reported.isActive)
    }

    /** Out-of-range measurements are rejected rather than stored as plausible-looking junk. */
    @Test
    fun outOfRangeMeasurementsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            audioState(sampleRateHz = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(sampleRateHz = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(bitsPerSample = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(bitsPerSample = 7)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(bitsPerSample = 33)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(bitrateKbps = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            audioState(bitrateKbps = -328)
        }
    }

    /** In-range values, including the edges of the bit-depth window, are accepted. */
    @Test
    fun inRangeMeasurementsAreAccepted() {
        assertEquals(8, audioState(bitsPerSample = 8).bitsPerSample)
        assertEquals(32, audioState(bitsPerSample = 32).bitsPerSample)
        assertEquals(1, audioState(sampleRateHz = 1).sampleRateHz)
        assertEquals(96_000, audioState(sampleRateHz = 96_000).sampleRateHz)
    }

    /** `isFullyObserved` requires every field, so one gap anywhere makes it false. */
    @Test
    fun isFullyObservedIsFalseUntilEveryFieldIsPresent() {
        val complete = AudioTransportState(
            transport = AudioTransportKind.CLASSIC_A2DP,
            codec = Codec.LDAC,
            state = CodecState.ACTIVE,
            sampleRateHz = 96_000,
            bitsPerSample = 24,
            bitrateKbps = 999,
            channelMode = ChannelMode.STEREO,
            qualityMode = QualityMode.SOUND_QUALITY_PRIORITY,
        )
        assertTrue(complete.isFullyObserved)

        assertFalse(complete.copy(transport = AudioTransportKind.UNKNOWN).isFullyObserved)
        assertFalse(complete.copy(codec = null).isFullyObserved)
        assertFalse(complete.copy(codec = Codec.UNKNOWN).isFullyObserved)
        assertFalse(complete.copy(state = CodecState.UNKNOWN).isFullyObserved)
        assertFalse(complete.copy(sampleRateHz = null).isFullyObserved)
        assertFalse(complete.copy(bitsPerSample = null).isFullyObserved)
        assertFalse(complete.copy(bitrateKbps = null).isFullyObserved)
        assertFalse(complete.copy(channelMode = ChannelMode.UNKNOWN).isFullyObserved)
        assertFalse(complete.copy(qualityMode = QualityMode.UNKNOWN).isFullyObserved)
    }

    /** Completeness is not verification: a full snapshot is still only as good as its source. */
    @Test
    fun activeStateIsTheOnlyOneThatClaimsToCarryAudio() {
        val active = audioState(state = CodecState.ACTIVE)
        val negotiated = audioState(state = CodecState.NEGOTIATED)
        val enabled = audioState(state = CodecState.ENABLED)

        assertTrue(active.isActive)
        assertFalse(negotiated.isActive)
        assertFalse(enabled.isActive)
    }

    /** A snapshot read for a call session must not bleed into the media session's record (AUD-XPORT-004). */
    @Test
    fun transportFamilyIsPartOfTheRecord() {
        val a2dp = audioState(transport = AudioTransportKind.CLASSIC_A2DP)
        val leAudio = audioState(transport = AudioTransportKind.LE_AUDIO, codec = Codec.LC3)

        assertNotEquals(a2dp, leAudio)
        assertEquals(CodecFamily.LE_AUDIO, leAudio.codec?.family)
        assertEquals(CodecFamily.CLASSIC_A2DP, a2dp.codec?.family)
    }

    /** Builds an otherwise complete classic-A2DP snapshot with the named measurement replaced. */
    private fun audioState(
        transport: AudioTransportKind = AudioTransportKind.CLASSIC_A2DP,
        codec: Codec? = Codec.AAC,
        state: CodecState = CodecState.ACTIVE,
        sampleRateHz: Int? = 48_000,
        bitsPerSample: Int? = 16,
        bitrateKbps: Int? = 250,
        channelMode: ChannelMode = ChannelMode.STEREO,
        qualityMode: QualityMode = QualityMode.BALANCED,
    ): AudioTransportState = AudioTransportState(
        transport = transport,
        codec = codec,
        state = state,
        sampleRateHz = sampleRateHz,
        bitsPerSample = bitsPerSample,
        bitrateKbps = bitrateKbps,
        channelMode = channelMode,
        qualityMode = qualityMode,
    )
}
