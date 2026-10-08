package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.QualityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * OB-P12-REQ-002, OB-P12-REQ-007, OB-P12-REQ-008, OB-P12-REQ-020:
 * the transaction lifecycle and requested-vs-confirmed separation.
 */
class CodecControlEngineTest {

    private val device = testDevice()

    private fun engineWithControl(
        codec: Codec = Codec.LDAC,
        adapter: FakeAdapter = FakeAdapter(),
    ): Pair<CodecControlEngine, FakeAdapter> {
        val resolver = FakeResolver(mapOf(codec to fullControlCapability(codec)))
        return CodecControlEngine(
            resolver = resolver,
            adapter = adapter,
            liveness = FakeLiveness(),
            clockMillis = { 1_000L },
        ) to adapter
    }

    @Test
    fun `select with selectable=false returns NotSelectable without touching adapter`() = runTest {
        val adapter = FakeAdapter()
        val engine = controlEngine(adapter = adapter)
        val result = engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))

        assertIs<CodecOperationResult.NotSelectable>(result)
        assertEquals(Codec.LDAC, result.codec)
        assertTrue(adapter.applied.isEmpty(), "adapter must not be touched when precheck refuses")
    }

    @Test
    fun `select with verifiable capability and matching observation verifies`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LDAC),
        )
        val (engine, _) = engineWithControl(adapter = adapter)
        val op = CodecOperation.select(
            device, Codec.LDAC, "op-1",
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        val result = engine.execute(op)

        assertIs<CodecOperationResult.Verified>(result)
        val state = engine.states.value[device]!!
        assertEquals(Codec.LDAC, state.confirmedCodec)
        assertEquals(Codec.LDAC, state.requestedCodec)
    }

    @Test
    fun `request LDAC but observation still AAC is VerificationFailed, confirmed stays AAC`() = runTest {
        // OB-P12-REQ-002: the canonical "do not claim success" scenario.
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.AAC),
        )
        val (engine, _) = engineWithControl(adapter = adapter)
        val op = CodecOperation.select(
            device, Codec.LDAC, "op-1",
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        val result = engine.execute(op)

        assertIs<CodecOperationResult.VerificationFailed>(result)
        assertEquals(Codec.LDAC, result.requestedCodec)
        assertEquals(Codec.AAC, result.observedCodec)
        val state = engine.states.value[device]!!
        assertEquals(Codec.LDAC, state.requestedCodec)
        // Confirmed never advanced: it is NOT LDAC.
        assertTrue(
            state.confirmedCodec != Codec.LDAC,
            "confirmed state must not become LDAC without verification",
        )
    }

    @Test
    fun `NONE verification strategy caps at AppliedUnverified, never confirms`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LDAC),
        )
        val (engine, _) = engineWithControl(adapter = adapter)
        val op = CodecOperation.select(
            device, Codec.LDAC, "op-1",
            verificationStrategy = CodecVerificationStrategy.NONE,
        )
        val result = engine.execute(op)

        assertIs<CodecOperationResult.AppliedUnverified>(result)
        val state = engine.states.value[device]!!
        assertEquals(Codec.LDAC, state.requestedCodec)
        assertEquals(
            Codec.UNKNOWN, state.confirmedCodec,
            "unverified applies never advance confirmed state",
        )
    }

    @Test
    fun `non-NONE strategy without verifiable capability is rejected in precheck`() = runTest {
        val resolver = FakeResolver(
            mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC).copy(verifiable = false)),
        )
        val engine = CodecControlEngine(resolver, FakeAdapter(), FakeLiveness(), clockMillis = { 1_000L })
        val op = CodecOperation.select(
            device, Codec.LDAC, "op-1",
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        val result = engine.execute(op)
        assertIs<CodecOperationResult.Rejected>(result)
    }

    @Test
    fun `configure with configurable=false returns NotConfigurable`() = runTest {
        val adapter = FakeAdapter()
        val engine = controlEngine(adapter = adapter)
        val config = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.BALANCED)
        val result = engine.execute(CodecOperation.configure(device, config, "op-1"))

        assertIs<CodecOperationResult.NotConfigurable>(result)
        assertTrue(adapter.applied.isEmpty())
    }

    @Test
    fun `configure with configurable=true and verified observation commits`() = runTest {
        val config = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.BALANCED)
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.BALANCED),
        )
        val (engine, _) = engineWithControl(adapter = adapter)
        val op = CodecOperation.configure(
            device, config, "op-1",
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        val result = engine.execute(op)

        assertIs<CodecOperationResult.Verified>(result)
        val state = engine.states.value[device]!!
        assertEquals(QualityMode.BALANCED, state.confirmedConfiguration?.qualityMode)
    }

    @Test
    fun `disconnected device returns DeviceDisconnected`() = runTest {
        val adapter = FakeAdapter()
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(connected = false),
            clockMillis = { 1_000L },
        )
        val result = engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))
        assertIs<CodecOperationResult.DeviceDisconnected>(result)
        assertTrue(adapter.applied.isEmpty())
    }

    @Test
    fun `adapter DeviceDisconnected mid-transaction returns DeviceDisconnected`() = runTest {
        val adapter = FakeAdapter(applyOutcome = CodecApplyOutcome.DeviceDisconnected)
        val (engine, _) = engineWithControl(adapter = adapter)
        val result = engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))
        assertIs<CodecOperationResult.DeviceDisconnected>(result)
    }

    @Test
    fun `LC3 over CLASSIC_A2DP is rejected`() = runTest {
        val (engine, _) = engineWithControl(codec = Codec.LC3)
        val op = CodecOperation.select(
            device, Codec.LC3, "op-1",
            expectedTransport = AudioTransportKind.CLASSIC_A2DP,
        )
        val result = engine.execute(op)
        assertIs<CodecOperationResult.Rejected>(result)
    }

    @Test
    fun `configure AAC with qualityMode is rejected - field not supported`() = runTest {
        val resolver = FakeResolver(mapOf(Codec.AAC to fullControlCapability(Codec.AAC)))
        val adapter = FakeAdapter()
        val engine = CodecControlEngine(resolver, adapter, FakeLiveness(), clockMillis = { 1_000L })
        val config = CodecConfiguration(Codec.AAC, qualityMode = QualityMode.BALANCED)
        val result = engine.execute(CodecOperation.configure(device, config, "op-1"))
        assertIs<CodecOperationResult.Rejected>(result)
        assertTrue(adapter.applied.isEmpty(), "invalid config must not reach the adapter")
    }

    @Test
    fun `unsupported codec returns Unsupported`() = runTest {
        val resolver = FakeResolver(
            mapOf(Codec.LDAC to androidRealityCapability(Codec.LDAC, supported = false)),
        )
        val engine = CodecControlEngine(resolver, FakeAdapter(), FakeLiveness(), clockMillis = { 1_000L })
        val result = engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))
        assertIs<CodecOperationResult.Unsupported>(result)
    }

    @Test
    fun `adapter NotAvailable maps to NotSelectable for select`() = runTest {
        val adapter = FakeAdapter(applyOutcome = CodecApplyOutcome.NotAvailable("no API"))
        val (engine, _) = engineWithControl(adapter = adapter)
        val result = engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))
        // Precheck refuses first (selectable=false in fullControl? no — fullControl has
        // selectable=true, so it reaches the adapter which reports NotAvailable).
        assertIs<CodecOperationResult.NotSelectable>(result)
    }

    @Test
    fun `refresh observes and returns Applied without changing confirmed`() = runTest {
        val adapter = FakeAdapter(observed = CodecConfiguration(Codec.AAC))
        val (engine, _) = engineWithControl(adapter = adapter)
        val result = engine.execute(CodecOperation.refresh(device, Codec.AAC, "op-1"))
        assertIs<CodecOperationResult.Applied>(result)
        val state = engine.states.value[device]!!
        assertEquals(Codec.AAC, state.observedCodec)
        assertEquals(Codec.UNKNOWN, state.confirmedCodec)
    }

    @Test
    fun `invalidate marks state stale`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LDAC),
        )
        val (engine, _) = engineWithControl(adapter = adapter)
        val op = CodecOperation.select(
            device, Codec.LDAC, "op-1",
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        engine.execute(op)
        engine.invalidate(device)
        val state = engine.states.value[device]!!
        assertEquals(com.omnibuds.core.audio.CodecFreshness.STALE, state.freshness)
        // The confirmed codec is still recorded, but flagged stale — never shown as current.
        assertEquals(Codec.LDAC, state.confirmedCodec)
    }
}
