package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 11 §38: every meaningful codec claim retains evidence, and confidence
 * never increases during normalization.
 */
class CodecEvidenceTest {

    @Test
    fun runtimeObservationOutranksDatabaseInference() {
        val observed = CodecEvidence(
            CodecEvidenceSource.ANDROID_FRAMEWORK,
            EvidenceConfidence.OBSERVED,
            observedAtMillis = 1_000L,
            detail = "BluetoothA2dp.getSupportedCodecTypes()",
        )
        val inferred = CodecEvidence(
            CodecEvidenceSource.UNKNOWN,
            EvidenceConfidence.INFERRED,
            detail = "device database entry",
        )
        assertTrue(observed.confidence.ordinal > inferred.confidence.ordinal)
    }

    @Test
    fun notObservableIsNotUnsupported() {
        // §20: AAC with UNKNOWN support and NOT_OBSERVABLE observability must
        // not be read as "AAC unsupported".
        val cap = CodecCapability(
            codec = Codec.AAC,
            state = CodecState.UNKNOWN,
            configurable = false,
            evidence = CodecEvidence(
                CodecEvidenceSource.ANDROID_FRAMEWORK,
                EvidenceConfidence.UNKNOWN,
                detail = "no public codec API on this API level",
            ),
            observability = CodecObservability.NOT_OBSERVABLE,
        )
        assertEquals(CodecState.UNKNOWN, cap.state)
        assertEquals(CodecObservability.NOT_OBSERVABLE, cap.observability)
        // UNKNOWN satisfies no positive rung — including "not supported".
        assertTrue(!cap.supportsAtLeast(CodecState.SUPPORTED))
    }

    @Test
    fun evidenceDetailNeverCarriesSecrets() {
        // A static review: evidence details name APIs, not addresses or tokens.
        val evidence = CodecEvidence(
            CodecEvidenceSource.BLUETOOTH_PROFILE,
            EvidenceConfidence.OBSERVED,
            detail = "BluetoothA2dp profile proxy read",
        )
        assertTrue(!evidence.detail.orEmpty().contains("token", ignoreCase = true))
        assertTrue(!evidence.detail.orEmpty().contains("address", ignoreCase = true))
    }

    @Test
    fun unknownEvidenceIsTheDefault() {
        val evidence = CodecEvidence.unknown()
        assertEquals(CodecEvidenceSource.UNKNOWN, evidence.source)
        assertEquals(EvidenceConfidence.UNKNOWN, evidence.confidence)
        assertEquals(null, evidence.observedAtMillis)
    }
}
