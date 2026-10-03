package com.omnibuds.core.capability

import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one dimension Phase 8 adds (§7, ADR-P8-002) and the provenance record it carries (§8, ADR-P8-006).
 *
 * The load-bearing properties are that an *unknown* availability is never an *unavailable* one (the same
 * honesty rule that keeps `CapabilityState.UNKNOWN` apart from `UNSUPPORTED`), and that a weak kind of
 * evidence cannot be written at a verification rung it did not earn (ADR-P8-003). Tier T1.
 */
class CapabilityEvidenceTest {

    @Test
    fun unknownAvailabilityIsNotUnavailable() {
        // "nobody looked" and "it is currently unusable" must not collapse (prompt section 7, section 53 rule).
        assertFalse(CapabilityAvailability.UNKNOWN.isAvailableNow())
        assertFalse(CapabilityAvailability.UNAVAILABLE.isAvailableNow())
        assertEquals(4, CapabilityAvailability.entries.size)
    }

    @Test
    fun availableAndTemporarilyUnavailableBothCountAsReachableNow() {
        // TEMPORARILY_UNAVAILABLE is a supported feature mid-calibration or behind an open case: a control may
        // still be surfaced for it, unlike a plain UNAVAILABLE whose prerequisite is off.
        assertTrue(CapabilityAvailability.AVAILABLE.isAvailableNow())
        assertTrue(CapabilityAvailability.TEMPORARILY_UNAVAILABLE.isAvailableNow())
    }

    @Test
    fun inferredEvidenceCannotClaimMoreThanInferredVerification() {
        // A reasoned-from-similarity record is a hypothesis; the ceiling is enforced so a fixture cannot write
        // an inference as though a device confirmed it (prompt section 8: an implemented parser is not hardware).
        assertFailsWith<IllegalArgumentException> {
            CapabilityEvidence(
                kind = EvidenceKind.INFERRED_MODEL,
                source = "similar model table",
                atEpochMillis = null,
                protocolId = null,
                protocolVersion = null,
                verification = VerificationLevel.HARDWARE_VERIFIED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityEvidence(
                kind = EvidenceKind.UNKNOWN_OR_INCOMPLETE,
                source = "no conclusive answer",
                atEpochMillis = null,
                protocolId = null,
                protocolVersion = null,
                verification = VerificationLevel.IMPLEMENTED,
            )
        }
    }

    @Test
    fun explicitDeviceResponseMayCarryHardwareVerification() {
        val record = CapabilityEvidence(
            kind = EvidenceKind.EXPLICIT_DEVICE_RESPONSE,
            source = "gatt read 0x2a19-equivalent",
            atEpochMillis = 1_700_000_000_000,
            protocolId = "test-protocol",
            protocolVersion = "1",
            verification = VerificationLevel.HARDWARE_VERIFIED,
            detail = "device answered the read",
            limitation = null,
        )

        assertEquals(VerificationLevel.HARDWARE_VERIFIED, record.verification)
        assertTrue(record.isEstablishing)
        assertEquals(1_700_000_000_000, record.atEpochMillis)
    }

    @Test
    fun anUnknownRecordEstablishesNothingAndNamesNoProtocolBlankly() {
        val unknown = CapabilityEvidence.unknown("battery descriptor absent")

        assertEquals(EvidenceKind.UNKNOWN_OR_INCOMPLETE, unknown.kind)
        assertEquals(VerificationLevel.INFERRED, unknown.verification)
        assertFalse(unknown.isEstablishing, "an unknown record is honest about establishing nothing")
        assertNull(unknown.protocolId)
        assertNull(unknown.atEpochMillis)
    }

    @Test
    fun aBlankSourceOrBlankProtocolIdIsRefusedNotStoredAsMeaningless() {
        // An absent binding (null) and a meaningless binding (blank) must not be two ways to say the same thing
        // (FeatureCapability enforces the same rule for protocolId).
        assertFailsWith<IllegalArgumentException> {
            CapabilityEvidence(
                kind = EvidenceKind.DEVICE_FEATURE_FLAG,
                source = "   ",
                atEpochMillis = null,
                protocolId = null,
                protocolVersion = null,
                verification = VerificationLevel.IMPLEMENTED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityEvidence(
                kind = EvidenceKind.DEVICE_FEATURE_FLAG,
                source = "flag bit",
                atEpochMillis = null,
                protocolId = "",
                protocolVersion = null,
                verification = VerificationLevel.IMPLEMENTED,
            )
        }
    }
}
