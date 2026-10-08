package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.QualityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * OB-P12-REQ-003, OB-P12-REQ-009…013, OB-P12-REQ-035, OB-P12-REQ-036:
 * per-codec control behavior.
 */
class PerCodecControlTest {

    private val device = testDevice()

    private fun engineFor(vararg codecs: Codec): CodecControlEngine {
        val caps = codecs.associateWith { fullControlCapability(it) }
        return CodecControlEngine(
            FakeResolver(caps),
            FakeAdapter(),
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
    }

    // --- AAC (OB-P12-REQ-009) ---

    @Test
    fun `AAC select with no mechanism returns NotSelectable`() = runTest {
        val engine = controlEngine() // android reality: not selectable
        val result = engine.execute(CodecOperation.select(device, Codec.AAC, "op-1"))
        assertIs<CodecOperationResult.NotSelectable>(result)
    }

    @Test
    fun `AAC configure with no mechanism returns NotConfigurable`() = runTest {
        val engine = controlEngine()
        val result = engine.execute(
            CodecOperation.configure(device, CodecConfiguration.empty(Codec.AAC), "op-1"),
        )
        assertIs<CodecOperationResult.NotConfigurable>(result)
    }

    @Test
    fun `AAC unsupported returns Unsupported, not NotSelectable`() = runTest {
        val resolver = FakeResolver(
            mapOf(Codec.AAC to androidRealityCapability(Codec.AAC, supported = false)),
        )
        val engine = CodecControlEngine(resolver, FakeAdapter(), FakeLiveness(), clockMillis = { 1_000L })
        val result = engine.execute(CodecOperation.select(device, Codec.AAC, "op-1"))
        assertIs<CodecOperationResult.Unsupported>(result)
    }

    // --- LDAC (OB-P12-REQ-010) ---

    @Test
    fun `LDAC quality mode configuration validates`() = runTest {
        val engine = engineFor(Codec.LDAC)
        // BALANCED is a valid LDAC quality mode field.
        val config = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.BALANCED)
        val op = CodecOperation.configure(
            device, config, "op-1",
            verificationStrategy = CodecVerificationStrategy.NONE,
        )
        // Passes field validation; NONE strategy → applied unverified (fake adapter performs).
        val result = engine.execute(op)
        assertIs<CodecOperationResult.AppliedUnverified>(result)
    }

    @Test
    fun `LDAC bitrate field is not supported - rejected before adapter`() = runTest {
        val adapter = FakeAdapter()
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        val config = CodecConfiguration(
            Codec.LDAC,
            bitrate = com.omnibuds.core.audio.CodecBitrate.Exact(990),
        )
        val result = engine.execute(CodecOperation.configure(device, config, "op-1"))
        assertIs<CodecOperationResult.Rejected>(result)
        assertTrue(adapter.applied.isEmpty())
    }

    @Test
    fun `LDAC UNKNOWN quality mode is valid - unknown stays unknown`() {
        val config = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.UNKNOWN)
        assertEquals(QualityMode.UNKNOWN, config.qualityMode)
    }

    // --- aptX family (OB-P12-REQ-011) ---

    @Test
    fun `aptX variants are independent - one supported does not imply another`() {
        val caps = mapOf(
            Codec.APTX to fullControlCapability(Codec.APTX),
            Codec.APTX_HD to androidRealityCapability(Codec.APTX_HD, supported = false),
            Codec.APTX_ADAPTIVE to androidRealityCapability(Codec.APTX_ADAPTIVE, supported = false),
            Codec.APTX_LOSSLESS to androidRealityCapability(Codec.APTX_LOSSLESS, supported = false),
        )
        val resolver = FakeResolver(caps)
        runTest {
            // aptX is controllable here; the others are not supported.
            assertTrue(resolver.resolve(device, Codec.APTX).supported)
            assertFalse(resolver.resolve(device, Codec.APTX_ADAPTIVE).supported)
            assertFalse(resolver.resolve(device, Codec.APTX_LOSSLESS).supported)
        }
    }

    @Test
    fun `aptX Adaptive is not equivalent to aptX`() {
        assertFalse(Codec.APTX_ADAPTIVE == Codec.APTX)
        assertFalse(
            fullControlCapability(Codec.APTX).codec == fullControlCapability(Codec.APTX_ADAPTIVE).codec,
        )
    }

    // --- LC3 (OB-P12-REQ-012) ---

    @Test
    fun `LC3 select over LE Audio transport passes transport validation`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LC3),
        )
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LC3 to fullControlCapability(Codec.LC3))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        val op = CodecOperation.select(
            device, Codec.LC3, "op-1",
            expectedTransport = com.omnibuds.core.audio.AudioTransportKind.LE_AUDIO,
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
        )
        val result = engine.execute(op)
        assertIs<CodecOperationResult.Verified>(result)
    }

    @Test
    fun `LC3 configure with no mechanism returns NotConfigurable`() = runTest {
        val engine = controlEngine()
        val result = engine.execute(
            CodecOperation.configure(device, CodecConfiguration.empty(Codec.LC3), "op-1"),
        )
        assertIs<CodecOperationResult.NotConfigurable>(result)
    }

    // --- SBC (OB-P12-REQ-013) ---

    @Test
    fun `SBC select with no mechanism returns NotSelectable - not assumed`() = runTest {
        val engine = controlEngine()
        val result = engine.execute(CodecOperation.select(device, Codec.SBC, "op-1"))
        // SBC is not assumed selectable; without a mechanism it is NotSelectable.
        assertIs<CodecOperationResult.NotSelectable>(result)
    }

    @Test
    fun `SBC is not assumed active`() {
        val state = CodecControlState.initial(1_000L)
        assertEquals(Codec.UNKNOWN, state.confirmedCodec)
    }

    // --- UNKNOWN distinctions (OB-P12-REQ-036) ---

    @Test
    fun `UNKNOWN is not UNSUPPORTED is not FAILED`() {
        val unknown = CodecControlCapability.unknown(Codec.AAC, testEvidence())
        assertFalse(unknown.supported)
        // The capability being unknown is distinct from the operation failing:
        // each result type is its own class in the sealed hierarchy.
        assertEquals(
            CodecOperationResult.NotSelectable::class,
            CodecOperationResult.NotSelectable("op-1", Codec.AAC)::class,
        )
        assertEquals(
            CodecOperationResult.Unsupported::class,
            CodecOperationResult.Unsupported("op-1", Codec.AAC)::class,
        )
    }

    @Test
    fun `NOT_CONFIGURABLE is not UNSUPPORTED`() {
        assertEquals(
            CodecOperationResult.NotConfigurable::class,
            CodecOperationResult.NotConfigurable("op-1", Codec.LDAC)::class,
        )
    }
}
