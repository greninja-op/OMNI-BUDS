package com.omnibuds.core.protocol

import com.omnibuds.core.protocol.ProtocolIdentification.MatchEvidence
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [ProtocolIdentification] and its [MatchEvidence], which is the difference between a
 * protocol being identified and a guess being recorded (PROTO-ID-002/003, PROTO-DB-002,
 * SEC-RES-004, master section 53).
 *
 * Tier T1.
 */
class ProtocolIdentificationTest {

    @Test
    fun theFallbackAnswerIsExpressibleAndIsNotAMatch() {
        val fallback = ProtocolIdentification(
            protocolId = ProtocolIdentification.NO_PROTOCOL_MATCHED,
            matchedBy = MatchEvidence.FALLBACK_UNKNOWN,
            confidence = VerificationLevel.INFERRED,
        )

        assertFalse(fallback.isIdentified)
        assertFalse(fallback.isDefinitive)
    }

    @Test
    fun theUnmatchedFactoryCarriesNoIdentification() {
        val unmatched = ProtocolIdentification.unmatched()

        assertEquals(MatchEvidence.FALLBACK_UNKNOWN, unmatched.matchedBy)
        assertFalse(unmatched.isIdentified)
        assertEquals(VerificationLevel.INFERRED, unmatched.confidence)
    }

    @Test
    fun aFallbackCannotBorrowTheConfidenceOfARealMatch() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolIdentification(
                protocolId = "example-vendor.test-protocol",
                matchedBy = MatchEvidence.FALLBACK_UNKNOWN,
                confidence = VerificationLevel.HARDWARE_VERIFIED,
            )
        }
        // The same id with real evidence is a different, and legal, statement.
        val matched = ProtocolIdentification(
            protocolId = "example-vendor.test-protocol",
            matchedBy = MatchEvidence.EXACT_FINGERPRINT,
            confidence = VerificationLevel.HARDWARE_VERIFIED,
        )
        assertTrue(matched.isIdentified)
    }

    @Test
    fun partialEvidenceIdentifiesNothingDefinitively() {
        val partial = identification(MatchEvidence.PARTIAL_FINGERPRINT)
        val discovered = identification(MatchEvidence.SERVICE_DISCOVERY)
        val exact = identification(MatchEvidence.EXACT_FINGERPRINT)

        assertTrue(partial.isIdentified)
        assertFalse(partial.isDefinitive)
        assertTrue(discovered.isIdentified)
        assertFalse(discovered.isDefinitive)
        assertTrue(exact.isDefinitive)
    }

    @Test
    fun aBlankProtocolIdCannotStandForAnUnknownProtocol() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolIdentification("", MatchEvidence.EXACT_FINGERPRINT, VerificationLevel.INFERRED)
        }
    }

    @Test
    fun theSameEvidenceAndDifferentConfidenceAreDifferentClaims() {
        val inferred = identification(MatchEvidence.SERVICE_DISCOVERY, VerificationLevel.INFERRED)
        val verified = identification(MatchEvidence.SERVICE_DISCOVERY, VerificationLevel.HARDWARE_VERIFIED)

        assertEquals(inferred.protocolId, verified.protocolId)
        assertEquals(inferred.matchedBy, verified.matchedBy)
        assertNotEquals(inferred, verified)
    }

    private fun identification(
        evidence: MatchEvidence,
        confidence: VerificationLevel = VerificationLevel.IMPLEMENTED,
    ): ProtocolIdentification = ProtocolIdentification(
        protocolId = "example-vendor.test-protocol",
        matchedBy = evidence,
        confidence = confidence,
    )
}
