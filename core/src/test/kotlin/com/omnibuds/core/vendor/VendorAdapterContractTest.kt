package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Common contract tests run against every registered vendor
 * integration.
 *
 * Phase 41: a reusable harness. Concrete subclasses supply the
 * adapter under test. These tests prove the adapter honors the
 * shared lifecycle contract without needing a real device.
 *
 * Subclasses must use clearly synthetic or evidence-backed
 * adapters — never a guessed protocol.
 */
abstract class VendorAdapterContractTest {

    /** The adapter under test. */
    abstract fun adapter(): VendorAdapter

    /** Evidence backing this adapter's claims (may be empty). */
    abstract fun evidence(): List<VendorEvidence>

    @Test
    fun `adapter id is stable and non-blank`() {
        val id = adapter().adapterId
        assertTrue(id.isNotBlank())
        assertTrue(id == adapter().adapterId)
    }

    @Test
    fun `unobserved device never matches`() = runTest {
        val result = adapter().match(DeviceFingerprint())
        assertTrue(result is MatchResult.NotMatched)
    }

    @Test
    fun `matching is deterministic`() = runTest {
        val fp = DeviceFingerprint()
        val first = adapter().match(fp)
        val second = adapter().match(fp)
        assertTrue(first::class == second::class)
    }

    @Test
    fun `protocol definition has a stable id`() {
        assertTrue(adapter().protocol.protocolId.isNotBlank())
    }

    @Test
    fun `evidence level never exceeds its records`() {
        val level = VendorEvidenceAssessor.effectiveLevel(evidence())
        assertFalse(
            VendorEvidenceAssessor.isHardwareVerified(evidence()) &&
                level != com.omnibuds.core.state.VerificationLevel.HARDWARE_VERIFIED,
        )
    }

    @Test
    fun `empty evidence means inferred`() {
        if (evidence().isEmpty()) {
            assertTrue(
                VendorEvidenceAssessor.effectiveLevel(evidence()) ==
                    com.omnibuds.core.state.VerificationLevel.INFERRED,
            )
        }
    }
}

/**
 * Contract tests for the null adapter: the honest default.
 */
class NullVendorAdapterContractTest : VendorAdapterContractTest() {
    override fun adapter(): VendorAdapter = NullVendorAdapter()
    override fun evidence(): List<VendorEvidence> = emptyList()
}
