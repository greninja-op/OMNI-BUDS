package com.omnibuds.core.vendor

import com.omnibuds.core.state.VerificationLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun evidence(
    level: VerificationLevel = VerificationLevel.LAB_TESTED,
    reviewedDate: String? = "2026-10-09",
) = VendorEvidence(
    integrationId = "vendor.x",
    source = "test source",
    scope = "test scope",
    level = level,
    reviewedDate = reviewedDate,
)

class VendorEvidenceTest {

    @Test
    fun `valid evidence accepted`() {
        val e = evidence()
        assertEquals("vendor.x", e.integrationId)
    }

    @Test
    fun `blank fields rejected`() {
        assertThrows<IllegalArgumentException> {
            evidence().copy(integrationId = " ")
        }
        assertThrows<IllegalArgumentException> {
            evidence().copy(source = "")
        }
    }

    @Test
    fun `hardware evidence requires review date`() {
        assertThrows<IllegalArgumentException> {
            evidence(
                level = VerificationLevel.HARDWARE_VERIFIED,
                reviewedDate = null,
            )
        }
    }

    @Test
    fun `empty evidence is inferred`() {
        assertEquals(
            VerificationLevel.INFERRED,
            VendorEvidenceAssessor.effectiveLevel(emptyList()),
        )
    }

    @Test
    fun `effective level is the max`() {
        val records = listOf(
            evidence(level = VerificationLevel.INFERRED),
            evidence(level = VerificationLevel.LAB_TESTED),
        )
        assertEquals(
            VerificationLevel.LAB_TESTED,
            VendorEvidenceAssessor.effectiveLevel(records),
        )
    }

    @Test
    fun `hardware verified only with dated record`() {
        assertTrue(
            VendorEvidenceAssessor.isHardwareVerified(
                listOf(evidence(level = VerificationLevel.HARDWARE_VERIFIED)),
            ),
        )
        assertFalse(
            VendorEvidenceAssessor.isHardwareVerified(
                listOf(evidence(level = VerificationLevel.LAB_TESTED)),
            ),
        )
        assertFalse(VendorEvidenceAssessor.isHardwareVerified(emptyList()))
    }
}
