package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.codec.CodecRuntimeState
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.codec.CodecSnapshot
import com.omnibuds.core.device.DeviceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-027): LE Audio / Classic / HFP separation.
 */
class TransportSeparationTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private fun resolveFor(transport: AudioTransportKind, codec: Codec): AudioQualityState {
        val snapshot = CodecSnapshot(
            schemaVersion = 1,
            timestampMillis = 1000L,
            deviceId = device,
            transport = transport,
            capabilities = listOf(
                CodecCapability(
                    codec = codec,
                    state = CodecState.ACTIVE,
                    configurable = false,
                    evidence = CodecEvidence(
                        source = CodecEvidenceSource.ANDROID_FRAMEWORK,
                        confidence = EvidenceConfidence.OBSERVED,
                        observedAtMillis = 1000L,
                    ),
                    observability = CodecObservability.OBSERVABLE,
                ),
            ),
            runtimeState = CodecRuntimeState(
                codec = codec,
                state = CodecState.ACTIVE,
                freshness = CodecFreshness.CURRENT,
            ),
            observability = CodecObservability.OBSERVABLE,
            limitations = emptyList(),
            diagnostics = emptyList(),
        )
        return AudioQualityResolver.resolve(
            device = device,
            codecSnapshot = snapshot,
            controlState = null,
            transport = transport,
            routeActive = true,
            transportConnected = true,
            nowMillis = 2000L,
        )
    }

    @Test
    fun `LC3 resolves under LE Audio`() {
        val state = resolveFor(AudioTransportKind.LE_AUDIO, Codec.LC3)
        assertEquals(AudioTransportKind.LE_AUDIO, state.transport)
        assertEquals(Codec.LC3, state.activeCodec)
    }

    @Test
    fun `LDAC resolves under classic A2DP`() {
        val state = resolveFor(AudioTransportKind.CLASSIC_A2DP, Codec.LDAC)
        assertEquals(AudioTransportKind.CLASSIC_A2DP, state.transport)
        assertEquals(Codec.LDAC, state.activeCodec)
    }

    @Test
    fun `HFP transport does not claim media codecs`() {
        // HFP is communication audio; the resolver reports what it is given
        // and never promotes it to a media codec.
        val state = resolveFor(AudioTransportKind.HFP, Codec.UNKNOWN)
        assertEquals(AudioTransportKind.HFP, state.transport)
        assertEquals(Codec.UNKNOWN, state.activeCodec)
    }

    @Test
    fun `transports are distinct values`() {
        // A type-level guarantee: the three families cannot be confused.
        val kinds = setOf(
            AudioTransportKind.LE_AUDIO,
            AudioTransportKind.CLASSIC_A2DP,
            AudioTransportKind.HFP,
        )
        assertEquals(3, kinds.size)
    }
}
