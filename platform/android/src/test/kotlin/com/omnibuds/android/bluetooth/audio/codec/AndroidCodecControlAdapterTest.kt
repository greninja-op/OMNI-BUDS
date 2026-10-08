package com.omnibuds.android.bluetooth.audio.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.codec.CodecApplyOutcome
import com.omnibuds.core.codec.CodecConfiguration
import com.omnibuds.core.codec.CodecControlCapabilityResolver
import com.omnibuds.core.codec.CodecOperation
import com.omnibuds.core.device.DeviceIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 12 §43, OB-P12-REQ-034, OB-P12-REQ-037: the Android control adapter
 * is honest — no public API exists for codec control, so every apply reports
 * NotAvailable and the resolver reports not selectable/configurable/verifiable.
 */
class AndroidCodecControlAdapterTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private class FakeHandle(
        var ids: List<RawCodecInfo> = emptyList(),
    ) : CodecObservationHandle {
        override suspend fun readLocalSupportedCodecIds(): List<RawCodecInfo> = ids
    }

    private fun adapter(): AndroidCodecControlAdapter {
        val source = AndroidCodecObservationSource(FakeHandle())
        return AndroidCodecControlAdapter(source)
    }

    private fun resolver(): CodecControlCapabilityResolver {
        val source = AndroidCodecObservationSource(FakeHandle())
        return AndroidCodecControlCapabilityResolver(source, apiLevel = 35)
    }

    @Test
    fun `apply always reports NotAvailable - never fake success`() = runTest {
        val adapter = adapter()
        val op = CodecOperation.select(device, Codec.LDAC, "op-1")
        val outcome = adapter.apply(op)
        assertIs<CodecApplyOutcome.NotAvailable>(outcome)
        assertTrue(outcome.reason.isNotBlank())
    }

    @Test
    fun `configure apply reports NotAvailable`() = runTest {
        val adapter = adapter()
        val op = CodecOperation.configure(
            device,
            CodecConfiguration(Codec.LDAC),
            "op-1",
        )
        val outcome = adapter.apply(op)
        assertIs<CodecApplyOutcome.NotAvailable>(outcome)
    }

    @Test
    fun `observeAfterApply returns null - active codec not observable`() = runTest {
        val adapter = adapter()
        val observed = adapter.observeAfterApply(device, Codec.LDAC)
        assertNull(observed, "no public API exposes the active codec")
    }

    @Test
    fun `resolver reports not selectable, not configurable, not verifiable`() = runTest {
        val resolver = resolver()
        val cap = resolver.resolve(device, Codec.LDAC)
        assertEquals(Codec.LDAC, cap.codec)
        assertFalse(cap.selectable, "no public API selects a codec")
        assertFalse(cap.configurable, "no public API configures a codec")
        assertFalse(cap.verifiable, "no observable active codec to verify against")
        assertFalse(cap.controllable)
    }

    @Test
    fun `resolver honors all codecs independently`() = runTest {
        val resolver = resolver()
        listOf(
            Codec.SBC, Codec.AAC, Codec.APTX, Codec.APTX_HD,
            Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS, Codec.LDAC, Codec.LC3,
        ).forEach { codec ->
            val cap = resolver.resolve(device, codec)
            assertFalse(cap.selectable, "$codec must not be selectable")
            assertFalse(cap.configurable, "$codec must not be configurable")
        }
    }

    @Test
    fun `resolver evidence names the platform limitation`() = runTest {
        val resolver = resolver()
        val cap = resolver.resolve(device, Codec.AAC)
        assertTrue(cap.evidence.detail?.isNotBlank() == true)
    }
}
