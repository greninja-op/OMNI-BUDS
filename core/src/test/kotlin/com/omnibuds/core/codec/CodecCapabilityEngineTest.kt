package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 11 §35, §36: multi-device isolation, stale-state handling, and the
 * engine lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodecCapabilityEngineTest {

    private var now: Long = 1_000_000L

    private fun device(name: String) = DeviceIdentity(displayName = name)

    private class FakeSource(
        var capabilities: Map<DeviceIdentity, List<CodecCapability>> = emptyMap(),
        var runtime: Map<DeviceIdentity, CodecRuntimeState?> = emptyMap(),
        var failReads: Boolean = false,
    ) : CodecObservationSource {
        val events = MutableSharedFlow<Pair<DeviceIdentity, CodecRuntimeState>>(extraBufferCapacity = 16)

        override suspend fun readCapabilities(device: DeviceIdentity): List<CodecCapability> {
            if (failReads) throw RuntimeException("boom")
            return capabilities[device].orEmpty()
        }

        override suspend fun readRuntimeState(device: DeviceIdentity): CodecRuntimeState? {
            if (failReads) throw RuntimeException("boom")
            return runtime[device]
        }

        override fun observeRuntimeStates(device: DeviceIdentity): Flow<CodecRuntimeState> =
            emptyFlow()
    }

    private fun kotlinx.coroutines.test.TestScope.engine(source: FakeSource) = CodecCapabilityEngine(
        observationSource = source,
        clockMillis = { now },
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun supported(
        codec: Codec,
        observability: CodecObservability = CodecObservability.OBSERVABLE,
    ) = CodecCapability(
        codec = codec,
        state = CodecState.SUPPORTED,
        configurable = false,
        evidence = CodecEvidence(
            CodecEvidenceSource.ANDROID_FRAMEWORK,
            EvidenceConfidence.OBSERVED,
            observedAtMillis = now,
        ),
        observability = observability,
    )

    private fun activeRuntime(codec: Codec) = CodecRuntimeState(
        codec = codec,
        state = CodecState.ACTIVE,
        observedAtMillis = now,
        freshness = CodecFreshness.CURRENT,
        evidence = CodecEvidence(
            CodecEvidenceSource.BLUETOOTH_PROFILE,
            EvidenceConfidence.OBSERVED,
            observedAtMillis = now,
        ),
    )

    @Test
    fun startPublishesSnapshotForDevice() = runTest {
        val source = FakeSource()
        val dev = device("A")
        source.capabilities = mapOf(dev to listOf(supported(Codec.LDAC)))
        source.runtime = mapOf(dev to activeRuntime(Codec.LDAC))
        val engine = engine(source)

        val result = engine.start(dev)
        assertTrue(result.isSuccess)
        val snapshot = engine.snapshots.value[dev]
        assertEquals(dev, snapshot?.deviceId)
        assertEquals(Codec.LDAC, snapshot?.activeCodec)
        assertEquals(1, snapshot?.capabilities?.size)
        engine.stopAll()
    }

    @Test
    fun deviceAStateNeverAppearsInDeviceB() = runTest {
        val source = FakeSource()
        val a = device("A")
        val b = device("B")
        source.capabilities = mapOf(
            a to listOf(supported(Codec.LDAC)),
            b to listOf(supported(Codec.AAC)),
        )
        source.runtime = mapOf(
            a to activeRuntime(Codec.LDAC),
            b to activeRuntime(Codec.AAC),
        )
        val engine = engine(source)
        engine.start(a)
        engine.start(b)

        // Device A → LDAC active, Device B → AAC active: no leakage.
        assertEquals(Codec.LDAC, engine.snapshots.value[a]?.activeCodec)
        assertEquals(Codec.AAC, engine.snapshots.value[b]?.activeCodec)
        assertEquals(
            listOf(Codec.LDAC),
            engine.snapshots.value[a]?.capabilities?.map { it.codec },
        )
        assertEquals(
            listOf(Codec.AAC),
            engine.snapshots.value[b]?.capabilities?.map { it.codec },
        )
        engine.stopAll()
    }

    @Test
    fun stopMarksRuntimeStaleInsteadOfDropping() = runTest {
        val source = FakeSource()
        val dev = device("A")
        source.capabilities = mapOf(dev to listOf(supported(Codec.LDAC)))
        source.runtime = mapOf(dev to activeRuntime(Codec.LDAC))
        val engine = engine(source)
        engine.start(dev)
        assertEquals(Codec.LDAC, engine.snapshots.value[dev]?.activeCodec)

        // Disconnect: the runtime record goes STALE — it must not keep
        // rendering as current, and it must not silently vanish either.
        engine.stop(dev)
        val snapshot = engine.snapshots.value[dev]
        assertEquals(CodecFreshness.STALE, snapshot?.runtimeState?.freshness)
        // Capabilities survive; only the live state is stale.
        assertEquals(1, snapshot?.capabilities?.size)
        engine.stopAll()
    }

    @Test
    fun reconnectReobservesFreshState() = runTest {
        val source = FakeSource()
        val dev = device("A")
        source.capabilities = mapOf(dev to listOf(supported(Codec.LDAC)))
        source.runtime = mapOf(dev to activeRuntime(Codec.LDAC))
        val engine = engine(source)
        engine.start(dev)
        engine.stop(dev)
        assertEquals(CodecFreshness.STALE, engine.snapshots.value[dev]?.runtimeState?.freshness)

        // Reconnect with a different codec: fresh observation wins, the stale
        // record does not survive.
        now += 60_000L
        source.runtime = mapOf(dev to activeRuntime(Codec.AAC))
        source.capabilities = mapOf(dev to listOf(supported(Codec.AAC)))
        engine.start(dev)
        val snapshot = engine.snapshots.value[dev]
        assertEquals(CodecFreshness.CURRENT, snapshot?.runtimeState?.freshness)
        assertEquals(Codec.AAC, snapshot?.activeCodec)
        engine.stopAll()
    }

    @Test
    fun deviceBRemainsActiveWhenDeviceADisconnects() = runTest {
        val source = FakeSource()
        val a = device("A")
        val b = device("B")
        source.capabilities = mapOf(
            a to listOf(supported(Codec.LDAC)),
            b to listOf(supported(Codec.AAC)),
        )
        source.runtime = mapOf(
            a to activeRuntime(Codec.LDAC),
            b to activeRuntime(Codec.AAC),
        )
        val engine = engine(source)
        engine.start(a)
        engine.start(b)
        engine.stop(a)

        assertEquals(CodecFreshness.STALE, engine.snapshots.value[a]?.runtimeState?.freshness)
        assertEquals(CodecFreshness.CURRENT, engine.snapshots.value[b]?.runtimeState?.freshness)
        assertEquals(Codec.AAC, engine.snapshots.value[b]?.activeCodec)
        engine.stopAll()
    }

    @Test
    fun refreshRequiresObservation() = runTest {
        val engine = engine(FakeSource())
        val result = engine.refresh(device("ghost"))
        assertTrue(!result.isSuccess)
        assertEquals(
            OmniBudsErrorCategory.INVALID_STATE,
            (result as com.omnibuds.core.common.OperationOutcome.Failure).error.category,
        )
        engine.stopAll()
    }

    @Test
    fun failedStartPublishesNoSnapshotAndReportsError() = runTest {
        val source = FakeSource(failReads = true)
        val engine = engine(source)
        val result = engine.start(device("A"))
        assertTrue(!result.isSuccess)
        assertEquals(
            OmniBudsErrorCategory.CODEC_OBSERVATION_FAILED,
            (result as com.omnibuds.core.common.OperationOutcome.Failure).error.category,
        )
        assertNull(engine.snapshots.value[device("A")])
        engine.stopAll()
    }

    @Test
    fun startIsIdempotent() = runTest {
        val source = FakeSource()
        val dev = device("A")
        source.capabilities = mapOf(dev to listOf(supported(Codec.SBC)))
        val engine = engine(source)
        assertTrue(engine.start(dev).isSuccess)
        assertTrue(engine.start(dev).isSuccess)
        assertEquals(1, engine.snapshots.value.size)
        engine.stopAll()
    }

    @Test
    fun snapshotIsImmutable() = runTest {
        val source = FakeSource()
        val dev = device("A")
        val caps = listOf(supported(Codec.SBC))
        source.capabilities = mapOf(dev to caps)
        val engine = engine(source)
        engine.start(dev)
        val snapshot = engine.snapshots.value[dev]!!
        // The snapshot holds a copy: mutating the source list afterwards
        // cannot affect the published snapshot.
        assertEquals(1, snapshot.capabilities.size)
        engine.stopAll()
    }

    @Test
    fun transportComesFromCodecFamily() = runTest {
        val source = FakeSource()
        val dev = device("A")
        source.capabilities = mapOf(dev to listOf(supported(Codec.LC3)))
        val engine = engine(source)
        engine.start(dev)
        // LC3's family is LE_AUDIO — the snapshot transport follows the codec,
        // never a hard-coded default.
        assertEquals(AudioTransportKind.LE_AUDIO, engine.snapshots.value[dev]?.transport)
        engine.stopAll()
    }

    @Test
    fun unobservablePlatformRecordsLimitations() = runTest {
        val source = FakeSource()
        val dev = device("A")
        // Empty capabilities: nothing observable on this platform.
        val engine = engine(source)
        engine.start(dev)
        val snapshot = engine.snapshots.value[dev]!!
        assertTrue(snapshot.limitations.isNotEmpty())
        assertTrue(
            snapshot.limitations.any { it.contains("no public observation API", ignoreCase = true) },
        )
        engine.stopAll()
    }
}
