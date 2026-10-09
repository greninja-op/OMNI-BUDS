package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.audio.QualityMode
import com.omnibuds.core.device.DeviceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 13 (§8–§17): model invariants for AudioQualityState and
 * QualityProfile.
 */
class AudioQualityModelTest {

    private val device = DeviceIdentity(displayName = "Test Buds")
    private val evidence = CodecEvidence(
        source = CodecEvidenceSource.ANDROID_FRAMEWORK,
        confidence = EvidenceConfidence.UNKNOWN,
        observedAtMillis = 1000L,
    )

    @Test
    fun `default state is fully unknown`() {
        val state = AudioQualityState(device = device, evidence = evidence)
        assertEquals(Codec.UNKNOWN, state.activeCodec)
        assertEquals(Codec.UNKNOWN, state.negotiatedCodec)
        assertNull(state.activeSampleRateHz)
        assertNull(state.activeBitDepth)
        assertEquals(CodecBitrate.Unknown, state.observedBitrate)
        assertEquals(ChannelMode.UNKNOWN, state.channelMode)
        assertEquals(QualityMode.UNKNOWN, state.observedQualityMode)
        assertEquals(AdaptiveState.UNKNOWN, state.adaptiveState)
        assertFalse(state.isCurrent)
        assertFalse(state.hasConflict)
    }

    @Test
    fun `connected inactive route with unknown codec is valid`() {
        val state = AudioQualityState(
            device = device,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = false,
            transportConnected = true,
            negotiationState = NegotiationState.IDLE,
            evidence = evidence,
        )
        assertEquals(Codec.UNKNOWN, state.activeCodec)
        // No exception: this combination is legal.
    }

    @Test
    fun `negative timestamp is rejected`() {
        assertThrows<IllegalArgumentException> {
            AudioQualityState(device = device, evidence = evidence, timestampMillis = -1L)
        }
    }

    @Test
    fun `negative sample rate is rejected`() {
        assertThrows<IllegalArgumentException> {
            AudioQualityState(
                device = device,
                evidence = evidence,
                supportedSampleRatesHz = listOf(-44100),
            )
        }
    }

    @Test
    fun `isCurrent only when freshness is current`() {
        val current = AudioQualityState(
            device = device,
            evidence = evidence,
            freshness = CodecFreshness.CURRENT,
        )
        assertTrue(current.isCurrent)
        val stale = current.copy(freshness = CodecFreshness.STALE)
        assertFalse(stale.isCurrent)
    }

    @Test
    fun `toProfile captures the point in time`() {
        val state = AudioQualityState(
            device = device,
            transport = AudioTransportKind.CLASSIC_A2DP,
            activeCodec = Codec.LDAC,
            codecState = CodecState.ACTIVE,
            activeSampleRateHz = 48000,
            activeBitDepth = 24,
            observedBitrate = CodecBitrate.Exact(660_000L),
            channelMode = ChannelMode.STEREO,
            observedQualityMode = QualityMode.SOUND_QUALITY_PRIORITY,
            adaptiveState = AdaptiveState.FIXED,
            evidence = evidence,
            freshness = CodecFreshness.CURRENT,
            timestampMillis = 5000L,
        )
        val profile = state.toProfile()
        assertEquals(Codec.LDAC, profile.codec)
        assertEquals(48000, profile.sampleRateHz)
        assertEquals(24, profile.bitDepth)
        assertEquals(5000L, profile.timestampMillis)
    }

    @Test
    fun `observability never becomes unsupported`() {
        // NOT_OBSERVABLE is a visibility fact, not a support claim.
        val state = AudioQualityState(
            device = device,
            evidence = evidence,
            observability = CodecObservability.NOT_OBSERVABLE,
        )
        assertEquals(CodecObservability.NOT_OBSERVABLE, state.observability)
    }

    @Test
    fun `negotiation session requires a session id`() {
        assertThrows<IllegalArgumentException> {
            NegotiationSession.begin(
                sessionId = "",
                device = device,
                transport = AudioTransportKind.CLASSIC_A2DP,
                nowMillis = 1000L,
                evidence = evidence,
            )
        }
    }

    @Test
    fun `session is active until completed`() {
        val session = NegotiationSession.begin(
            sessionId = "neg-1",
            device = device,
            transport = AudioTransportKind.CLASSIC_A2DP,
            nowMillis = 1000L,
            evidence = evidence,
        )
        assertTrue(session.isActive)
        val completed = session.copy(
            completedAtMillis = 2000L,
            finalState = NegotiationState.NEGOTIATED,
            negotiatedCodec = Codec.LDAC,
        )
        assertTrue(!completed.isActive)
    }
}
