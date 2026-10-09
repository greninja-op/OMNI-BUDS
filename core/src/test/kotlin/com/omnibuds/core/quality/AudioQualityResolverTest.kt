package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecMetadata
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.codec.CodecRuntimeState
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.codec.CodecControlState
import com.omnibuds.core.codec.CodecSnapshot
import com.omnibuds.core.device.DeviceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-018, OB-P13-REQ-020, §42): the deterministic resolver.
 */
class AudioQualityResolverTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private fun observedEvidence() = CodecEvidence(
        source = CodecEvidenceSource.ANDROID_FRAMEWORK,
        confidence = EvidenceConfidence.OBSERVED,
        observedAtMillis = 1000L,
    )

    private fun capability(codec: Codec, state: CodecState) = CodecCapability(
        codec = codec,
        state = state,
        configurable = false,
        evidence = observedEvidence(),
        observability = CodecObservability.OBSERVABLE,
        metadata = CodecMetadata(sampleRateHz = 48000, bitsPerSample = 16),
    )

    private fun snapshot(vararg caps: CodecCapability) = CodecSnapshot(
        schemaVersion = 1,
        timestampMillis = 1000L,
        deviceId = device,
        transport = AudioTransportKind.CLASSIC_A2DP,
        capabilities = caps.toList(),
        runtimeState = CodecRuntimeState(
            codec = Codec.LDAC,
            state = CodecState.ACTIVE,
            freshness = CodecFreshness.CURRENT,
        ),
        observability = CodecObservability.OBSERVABLE,
        limitations = emptyList(),
        diagnostics = emptyList(),
    )

    @Test
    fun `resolves active codec from snapshot`() {
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(capability(Codec.LDAC, CodecState.ACTIVE)),
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
        assertEquals(Codec.LDAC, state.activeCodec)
        assertEquals(NegotiationState.ACTIVE, state.negotiationState)
        assertTrue(state.isCurrent)
        assertFalse(state.hasConflict)
    }

    @Test
    fun `negotiated codec is separate from active codec`() {
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(
                capability(Codec.LDAC, CodecState.NEGOTIATED),
                capability(Codec.AAC, CodecState.ACTIVE),
            ),
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
        assertEquals(Codec.LDAC, state.negotiatedCodec)
        assertEquals(Codec.AAC, state.activeCodec)
    }

    @Test
    fun `verified control state outranks snapshot on conflict`() {
        val control = CodecControlState(
            confirmedCodec = Codec.AAC,
            freshness = CodecFreshness.CURRENT,
            updatedAtMillis = 1500L,
        )
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(capability(Codec.LDAC, CodecState.ACTIVE)),
            controlState = control,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
        // Verified runtime observation wins; the conflict is flagged.
        assertEquals(Codec.AAC, state.activeCodec)
        assertTrue(state.hasConflict)
    }

    @Test
    fun `unknown snapshot yields unknown state`() {
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = null,
            controlState = null,
            transport = null,
            routeActive = null,
            transportConnected = null,
            nowMillis = 2000L,
        )
        assertEquals(Codec.UNKNOWN, state.activeCodec)
        assertEquals(Codec.UNKNOWN, state.negotiatedCodec)
        assertEquals(NegotiationState.UNKNOWN, state.negotiationState)
        assertNull(state.activeSampleRateHz)
        assertFalse(state.isCurrent)
    }

    @Test
    fun `disconnected transport yields disconnected negotiation`() {
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(capability(Codec.LDAC, CodecState.ACTIVE)),
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = false,
            transportConnected = false,
            nowMillis = 2000L,
        )
        assertEquals(NegotiationState.DISCONNECTED, state.negotiationState)
    }

    @Test
    fun `connected but inactive route is not active`() {
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(capability(Codec.AAC, CodecState.NEGOTIATED)),
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = false,
            transportConnected = true,
            nowMillis = 2000L,
        )
        // NEGOTIATED codec + inactive route → NEGOTIATED, never ACTIVE.
        assertEquals(NegotiationState.NEGOTIATED, state.negotiationState)
        assertEquals(Codec.AAC, state.negotiatedCodec)
    }

    @Test
    fun `stale snapshot yields stale negotiation`() {
        val stale = snapshot(capability(Codec.LDAC, CodecState.ACTIVE)).copy(
            runtimeState = CodecRuntimeState(
                codec = Codec.LDAC,
                state = CodecState.ACTIVE,
                freshness = CodecFreshness.STALE,
            ),
        )
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = stale,
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
        assertEquals(NegotiationState.STALE, state.negotiationState)
        assertFalse(state.isCurrent)
        // The observation is preserved, not deleted.
        assertEquals(Codec.LDAC, state.activeCodec)
    }

    @Test
    fun `parameters only from observed confidence`() {
        val inferred = CodecCapability(
            codec = Codec.LDAC,
            state = CodecState.ACTIVE,
            configurable = false,
            evidence = observedEvidence().copy(confidence = EvidenceConfidence.INFERRED),
            observability = CodecObservability.OBSERVABLE,
            metadata = CodecMetadata(sampleRateHz = 96000),
        )
        val state = AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot(inferred),
            controlState = null,
            transport = AudioTransportKind.CLASSIC_A2DP,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
        // Inferred metadata must not populate the active fields.
        assertNull(state.activeSampleRateHz)
    }
}
