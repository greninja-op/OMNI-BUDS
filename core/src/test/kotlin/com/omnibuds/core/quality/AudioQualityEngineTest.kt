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
import com.omnibuds.core.codec.CodecSnapshot
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 13 (§37–§45): the negotiation engine — events, sessions, dedup,
 * multi-device isolation, stale/disconnect behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioQualityEngineTest {

    private val deviceA = DeviceIdentity(displayName = "Buds A")
    private val deviceB = DeviceIdentity(displayName = "Buds B")
    private var now = 1000L

    private fun engine() = AudioQualityEngine(
        dispatcher = UnconfinedTestDispatcher(),
        clockMillis = { now },
    )

    private fun capability(codec: Codec, state: CodecState) = CodecCapability(
        codec = codec,
        state = state,
        configurable = false,
        evidence = CodecEvidence(
            source = CodecEvidenceSource.ANDROID_FRAMEWORK,
            confidence = EvidenceConfidence.OBSERVED,
            observedAtMillis = now,
        ),
        observability = CodecObservability.OBSERVABLE,
    )

    private fun snapshot(codec: Codec, state: CodecState) = CodecSnapshot(
        schemaVersion = 1,
        timestampMillis = now,
        deviceId = deviceA,
        transport = AudioTransportKind.CLASSIC_A2DP,
        capabilities = listOf(capability(codec, state)),
        runtimeState = CodecRuntimeState(
            codec = codec,
            state = state,
            freshness = CodecFreshness.CURRENT,
        ),
        observability = CodecObservability.OBSERVABLE,
        limitations = emptyList(),
        diagnostics = emptyList(),
    )

    @Test
    fun `ingesting transport creates a state`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, false, true)
        val state = engine.states.first()[deviceA]
        assertNotNull(state)
        assertEquals(AudioTransportKind.CLASSIC_A2DP, state!!.transport)
        assertEquals(NegotiationState.IDLE, state.negotiationState)
    }

    @Test
    fun `codec snapshot drives negotiation to active`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        val state = engine.states.first()[deviceA]!!
        assertEquals(Codec.LDAC, state.activeCodec)
        assertEquals(NegotiationState.ACTIVE, state.negotiationState)
    }

    @Test
    fun `duplicate ingestion emits no duplicate state`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        val first = engine.states.first()[deviceA]
        // Same observations again — the state must be identical.
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        val second = engine.states.first()[deviceA]
        assertEquals(first, second)
    }

    @Test
    fun `codec change produces CodecChanged event`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.AAC, CodecState.ACTIVE))
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        // The timeline is the synchronous record of emitted events.
        val change = engine.timelineFor(deviceA)
            .filterIsInstance<NegotiationEvent.CodecChanged>()
            .lastOrNull()
        assertNotNull(change)
        assertEquals(Codec.AAC, change!!.previousCodec)
        assertEquals(Codec.LDAC, change.newCodec)
    }

    @Test
    fun `disconnect terminates the session and marks disconnected`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ENABLED))
        assertNotNull(engine.sessionFor(deviceA))
        engine.onDisconnected(deviceA)
        val state = engine.states.first()[deviceA]!!
        assertEquals(NegotiationState.DISCONNECTED, state.negotiationState)
        // The session is terminated, not left running.
        val session = engine.sessionFor(deviceA)
        assertTrue(session == null || !session.isActive)
    }

    @Test
    fun `multi-device states are isolated`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        engine.onTransportUpdate(deviceB, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(
            deviceB,
            snapshot(Codec.AAC, CodecState.ACTIVE).copy(deviceId = deviceB),
        )
        val states = engine.states.first()
        assertEquals(Codec.LDAC, states[deviceA]!!.activeCodec)
        assertEquals(Codec.AAC, states[deviceB]!!.activeCodec)
        // Disconnect A — B must be unaffected.
        engine.onDisconnected(deviceA)
        val after = engine.states.first()
        assertEquals(NegotiationState.DISCONNECTED, after[deviceA]!!.negotiationState)
        assertEquals(Codec.AAC, after[deviceB]!!.activeCodec)
        assertEquals(NegotiationState.ACTIVE, after[deviceB]!!.negotiationState)
    }

    @Test
    fun `stale marking preserves the observation`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        engine.onBecameStale(deviceA, "route changed")
        val state = engine.states.first()[deviceA]!!
        assertEquals(NegotiationState.STALE, state.negotiationState)
        // The codec is still recorded — stale, not deleted.
        assertEquals(Codec.LDAC, state.activeCodec)
    }

    @Test
    fun `negotiation session completes on negotiated`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, false, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ENABLED))
        val active = engine.sessionFor(deviceA)
        assertNotNull(active)
        assertTrue(active!!.isActive)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.NEGOTIATED))
        val completed = engine.sessionFor(deviceA)
        assertNotNull(completed)
        assertEquals(Codec.LDAC, completed!!.negotiatedCodec)
    }

    @Test
    fun `timeline is bounded`() = runTest {
        val engine = AudioQualityEngine(
            dispatcher = UnconfinedTestDispatcher(),
            clockMillis = { now },
            maxTimelineEvents = 4,
        )
        repeat(6) { i ->
            now += 100
            engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, i % 2 == 0, true)
        }
        val timeline = engine.timelineFor(deviceA)
        assertTrue(timeline.size <= 4)
    }

    @Test
    fun `reconnect starts from fresh state`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, true, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.LDAC, CodecState.ACTIVE))
        engine.onDisconnected(deviceA)
        // Reconnect with no codec observation yet: the old LDAC must not
        // masquerade as current.
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, false, true)
        engine.onCodecSnapshot(deviceA, null)
        val state = engine.states.first()[deviceA]!!
        assertEquals(Codec.UNKNOWN, state.activeCodec)
    }

    @Test
    fun `negotiated but inactive route is not active`() = runTest {
        val engine = engine()
        engine.onTransportUpdate(deviceA, AudioTransportKind.CLASSIC_A2DP, false, true)
        engine.onCodecSnapshot(deviceA, snapshot(Codec.AAC, CodecState.NEGOTIATED))
        val state = engine.states.first()[deviceA]!!
        assertEquals(NegotiationState.NEGOTIATED, state.negotiationState)
        assertEquals(Codec.AAC, state.negotiatedCodec)
    }
}
