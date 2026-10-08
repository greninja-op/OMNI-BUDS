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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 11 §39: the capability states are fundamentally distinct.
 *
 * SUPPORTED ≠ ACTIVE, AVAILABLE ≠ ACTIVE, ENABLED ≠ NEGOTIATED,
 * NEGOTIATED ≠ ACTIVE, CONFIGURABLE ≠ CONFIGURED. The [CodecState] ladder
 * makes each rung a distinct value, and `configurable` is orthogonal.
 */
class CodecCapabilityStateTest {

    @Test
    fun supportedIsNotActive() {
        val cap = CodecCapability(Codec.LDAC, CodecState.SUPPORTED, configurable = false)
        assertFalse(cap.isActive)
        assertTrue(cap.supportsAtLeast(CodecState.SUPPORTED))
        assertFalse(cap.supportsAtLeast(CodecState.ACTIVE))
    }

    @Test
    fun availableIsNotActive() {
        val cap = CodecCapability(Codec.LDAC, CodecState.AVAILABLE, configurable = false)
        assertFalse(cap.isActive)
        assertTrue(cap.isUsable)
    }

    @Test
    fun enabledIsNotNegotiated() {
        val cap = CodecCapability(Codec.LDAC, CodecState.ENABLED, configurable = false)
        assertTrue(cap.supportsAtLeast(CodecState.ENABLED))
        assertFalse(cap.supportsAtLeast(CodecState.NEGOTIATED))
    }

    @Test
    fun negotiatedIsNotActive() {
        // Negotiation without a verified active read renders as
        // "negotiated; active state not verified" — never as active.
        val cap = CodecCapability(Codec.LDAC, CodecState.NEGOTIATED, configurable = false)
        assertFalse(cap.isActive)
        assertTrue(cap.supportsAtLeast(CodecState.NEGOTIATED))
    }

    @Test
    fun configurableIsOrthogonalToActive() {
        // Active AND configurable, and active AND read-only, are both
        // expressible — writability is not a degree of activity.
        val activeConfigurable =
            CodecCapability(Codec.LDAC, CodecState.ACTIVE, configurable = true)
        val activeReadOnly =
            CodecCapability(Codec.LDAC, CodecState.ACTIVE, configurable = false)
        assertTrue(activeConfigurable.isActive && activeConfigurable.configurable)
        assertTrue(activeReadOnly.isActive && !activeReadOnly.configurable)
    }

    @Test
    fun unknownIsNotUnsupported() {
        val unknown = CodecCapability.unknown(Codec.LDAC)
        val unsupported = CodecCapability.unsupported(Codec.LDAC)
        assertEquals(CodecState.UNKNOWN, unknown.state)
        assertEquals(CodecState.UNSUPPORTED, unsupported.state)
        // Neither satisfies any positive rung.
        assertFalse(unknown.supportsAtLeast(CodecState.SUPPORTED))
        assertFalse(unsupported.supportsAtLeast(CodecState.SUPPORTED))
    }

    @Test
    fun enumMembershipIsNotASupportClaim() {
        // RULE 1: a codec existing in the enum is not a support claim.
        // A freshly constructed capability for every codec is UNKNOWN.
        Codec.entries.forEach { codec ->
            val cap = CodecCapability.unknown(codec)
            assertEquals(CodecState.UNKNOWN, cap.state, "${codec.name} must start UNKNOWN")
            assertFalse(cap.isActive, "${codec.name} must not be active without evidence")
        }
    }

    @Test
    fun evidenceDefaultsToUnknown() {
        val cap = CodecCapability(Codec.LDAC, CodecState.SUPPORTED, configurable = false)
        assertEquals(CodecEvidenceSource.UNKNOWN, cap.evidence.source)
        assertEquals(EvidenceConfidence.UNKNOWN, cap.evidence.confidence)
        assertEquals(CodecObservability.UNKNOWN, cap.observability)
    }

    @Test
    fun activeRequiresTheActiveRung() {
        // isActive is exact: only CodecState.ACTIVE, never negotiated||enabled.
        listOf(
            CodecState.UNKNOWN, CodecState.UNSUPPORTED, CodecState.SUPPORTED,
            CodecState.AVAILABLE, CodecState.ENABLED, CodecState.NEGOTIATED,
        ).forEach { state ->
            val cap = CodecCapability(Codec.LDAC, state, configurable = false)
            assertFalse(cap.isActive, "$state must not report active")
        }
        assertTrue(CodecCapability(Codec.LDAC, CodecState.ACTIVE, false).isActive)
    }
}
