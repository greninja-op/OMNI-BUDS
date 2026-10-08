package com.omnibuds.android.bluetooth.audio.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 11 §37: the Android source degrades gracefully.
 *
 * - Empty platform read → every codec UNKNOWN + NOT_OBSERVABLE (never
 *   "unsupported", never a throw).
 * - Supported list → SUPPORTED rung with OBSERVED confidence; unlisted codecs
 *   stay UNKNOWN (not UNSUPPORTED — the list is local capabilities, not a
 *   negative claim about the peer).
 * - Runtime state → null (no public API exposes the active codec).
 */
class AndroidCodecObservationSourceTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private class FakeHandle(
        var ids: List<RawCodecInfo> = emptyList(),
    ) : CodecObservationHandle {
        override suspend fun readLocalSupportedCodecIds(): List<RawCodecInfo> = ids
    }

    private fun supportedId(platformId: Long) = RawCodecInfo(
        platformCodecId = platformId,
        idKind = CodecIdKind.CODEC_ID,
        isLocallySupported = true,
    )

    @Test
    fun emptyReadMeansUnobservableNotUnsupported() = runTest {
        val source = AndroidCodecObservationSource(FakeHandle())
        val caps = source.readCapabilities(device)

        // Every known codec is present but UNKNOWN — the platform exposed
        // nothing, so nothing is claimed.
        assertEquals(Codec.entries.size, caps.size)
        caps.forEach { cap ->
            assertEquals(CodecState.UNKNOWN, cap.state, "${cap.codec} must be UNKNOWN")
            assertEquals(
                CodecObservability.NOT_OBSERVABLE, cap.observability,
                "${cap.codec} must be NOT_OBSERVABLE",
            )
            assertTrue(
                !cap.supportsAtLeast(CodecState.SUPPORTED),
                "${cap.codec} must not read as supported",
            )
        }
    }

    @Test
    fun supportedListMapsToSupportedRung() = runTest {
        // CODEC_ID_SBC=0, CODEC_ID_AAC=2, CODEC_ID_LDAC=-1442763265
        val source = AndroidCodecObservationSource(
            FakeHandle(listOf(supportedId(0L), supportedId(2L), supportedId(-1442763265L))),
        )
        val caps = source.readCapabilities(device).associateBy { it.codec }

        listOf(Codec.SBC, Codec.AAC, Codec.LDAC).forEach { codec ->
            val cap = caps[codec]!!
            assertEquals(CodecState.SUPPORTED, cap.state)
            assertEquals(EvidenceConfidence.OBSERVED, cap.evidence.confidence)
            assertEquals(CodecObservability.OBSERVABLE, cap.observability)
        }
    }

    @Test
    fun unlistedCodecsStayUnknownNotUnsupported() = runTest {
        val source = AndroidCodecObservationSource(
            FakeHandle(listOf(supportedId(0L))),
        )
        val caps = source.readCapabilities(device).associateBy { it.codec }

        // SBC reported; LDAC not in the list → UNKNOWN, not UNSUPPORTED.
        // The list is local phone capabilities, not a negative peer claim.
        assertEquals(CodecState.SUPPORTED, caps[Codec.SBC]?.state)
        assertEquals(CodecState.UNKNOWN, caps[Codec.LDAC]?.state)
        assertTrue(caps[Codec.LDAC]?.state != CodecState.UNSUPPORTED)
    }

    @Test
    fun untranslatableIdsAreDroppedWithoutFailing() = runTest {
        val source = AndroidCodecObservationSource(
            FakeHandle(listOf(supportedId(0L), supportedId(999_999L))),
        )
        val caps = source.readCapabilities(device)
        // The bogus id maps to null and is dropped; SBC still reported.
        assertEquals(Codec.SBC, caps.first { it.state == CodecState.SUPPORTED }.codec)
    }

    @Test
    fun runtimeStateIsNullWhenUnexposed() = runTest {
        val source = AndroidCodecObservationSource(
            FakeHandle(listOf(supportedId(0L))),
        )
        // No public API exposes the active codec → null ("unobserved").
        assertNull(source.readRuntimeState(device))
    }

    @Test
    fun allNineRequiredCodecsAreRepresented() = runTest {
        val source = AndroidCodecObservationSource(FakeHandle())
        val codecs = source.readCapabilities(device).map { it.codec }.toSet()
        listOf(
            Codec.SBC, Codec.AAC, Codec.APTX, Codec.APTX_HD,
            Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS, Codec.LDAC,
            Codec.LC3, Codec.UNKNOWN,
        ).forEach { codec ->
            assertTrue(codec in codecs, "${codec.name} must be represented")
        }
    }
}
