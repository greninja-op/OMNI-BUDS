package com.omnibuds.core.validation

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
import com.omnibuds.core.quality.AudioQualityEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 14 (§21I–J): the validation engine — generations, dedup, isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidationEngineTest {

    private val deviceA = DeviceIdentity(displayName = "Buds A")
    private val deviceB = DeviceIdentity(displayName = "Buds B")
    private var now = 1000L

    private fun qualityEngine() = AudioQualityEngine(
        dispatcher = UnconfinedTestDispatcher(),
        clockMillis = { now },
    )

    private fun engine(quality: AudioQualityEngine) = AudioPathValidationEngine(
        qualityEngine = quality,
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

    private fun snapshot(device: DeviceIdentity, codec: Codec, state: CodecState) = CodecSnapshot(
        timestampMillis = now,
        deviceId = device,
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

    private suspend fun seedQuality(
        quality: AudioQualityEngine,
        device: DeviceIdentity,
        codec: Codec,
    ) {
        quality.onTransportUpdate(device, AudioTransportKind.CLASSIC_A2DP, true, true)
        quality.onCodecSnapshot(device, snapshot(device, codec, CodecState.ACTIVE))
    }

    @Test
    fun `validate produces a snapshot`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        seedQuality(quality, deviceA, Codec.AAC)
        val snapshot = engine.validate(deviceA)
        assertNotNull(snapshot)
        assertEquals(deviceA, snapshot!!.device)
        assertEquals(Codec.AAC, snapshot.codec)
        assertTrue(snapshot.results.isNotEmpty())
    }

    @Test
    fun `obsolete generation is discarded`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        seedQuality(quality, deviceA, Codec.AAC)
        val gen0 = engine.generationFor(deviceA)
        engine.onDisconnected(deviceA)
        val gen1 = engine.generationFor(deviceA)
        assertTrue(gen1 > gen0)
        // A late validation tagged with the old generation is discarded.
        val stale = engine.validate(deviceA, expectedGeneration = gen0)
        assertNull(stale)
        // The current generation validates fine.
        val current = engine.validate(deviceA, expectedGeneration = gen1)
        assertNotNull(current)
    }

    @Test
    fun `duplicate validation does not re-emit`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        seedQuality(quality, deviceA, Codec.AAC)
        val first = engine.validate(deviceA)
        val second = engine.validate(deviceA)
        // Same observations → identical snapshot content (dedup by value).
        assertEquals(first, second)
        assertEquals(1, engine.snapshots.first().size)
    }

    @Test
    fun `multi-device snapshots are isolated`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        seedQuality(quality, deviceA, Codec.LDAC)
        seedQuality(quality, deviceB, Codec.AAC)
        val snapA = engine.validate(deviceA)!!
        val snapB = engine.validate(deviceB)!!
        assertEquals(Codec.LDAC, snapA.codec)
        assertEquals(Codec.AAC, snapB.codec)
        assertEquals(2, engine.snapshots.first().size)
        // Disconnect A — B's snapshot remains.
        engine.onDisconnected(deviceA)
        quality.onDisconnected(deviceA)
        val after = engine.snapshots.first()
        assertNotNull(after[deviceB])
    }

    @Test
    fun `lc3 over le audio validates`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        quality.onTransportUpdate(deviceA, AudioTransportKind.LE_AUDIO, true, true)
        quality.onCodecSnapshot(
            deviceA,
            snapshot(deviceA, Codec.LC3, CodecState.ACTIVE).copy(
                transport = AudioTransportKind.LE_AUDIO,
            ),
        )
        val snapshot = engine.validate(
            deviceA,
            transport = AudioTransportKind.LE_AUDIO,
        )!!
        val codecRule = snapshot.results.first { it.validationId == "P14-CODEC-001" }
        assertEquals(ValidationStatus.VALID, codecRule.status)
    }

    @Test
    fun `snapshot is immutable and coherent`() = runTest {
        val quality = qualityEngine()
        val engine = engine(quality)
        seedQuality(quality, deviceA, Codec.AAC)
        val snapshot = engine.validate(deviceA)!!
        // All results target the same device and generation.
        assertTrue(snapshot.results.all { it.device == deviceA })
        assertTrue(
            snapshot.results.all { it.sessionGeneration == snapshot.sessionGeneration },
        )
    }
}
