package com.omnibuds.core.access

import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 21: capability-state tests.
 */
class CapabilityStateTest {

    private val supported = DeviceAccessState(
        classification = DeviceClassification.KNOWN_DEVICE_SUPPORTED,
        identityConfidence = IdentityConfidence.HIGH,
        protocolVerified = true,
        writeAuthorized = false,
    )

    @Test
    fun `unknown preserved when discovery incomplete`() {
        val record = CapabilityAccessRecord.unknown("anc")
        assertEquals(CapabilityState.UNKNOWN, record.support)
        assertEquals(CapabilityAccess.UNKNOWN, record.access)
    }

    @Test
    fun `unknown support cannot carry positive access`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityAccessRecord(
                capabilityId = "anc",
                support = CapabilityState.UNKNOWN,
                access = CapabilityAccess.READ_WRITE,
                writePathVerified = false,
                firmwareCompatible = true,
                evidence = emptyList(),
            )
        }
    }

    @Test
    fun `read-only and writable access distinct`() {
        val readOnly = CapabilityAccessRecord(
            capabilityId = "anc",
            support = CapabilityState.READ_ONLY,
            access = CapabilityAccess.READ_ONLY,
            writePathVerified = false,
            firmwareCompatible = true,
            evidence = listOf("device reports anc state"),
        )
        val writeDenied = CapabilityAccessEvaluator.canWrite(supported, readOnly)
        assertFalse(writeDenied.allowed)

        val read = CapabilityAccessEvaluator.canRead(supported, readOnly)
        assertTrue(read.allowed)
    }

    @Test
    fun `read-write requires verified write path`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityAccessRecord(
                capabilityId = "anc",
                support = CapabilityState.SUPPORTED_PERSISTENT,
                access = CapabilityAccess.READ_WRITE,
                writePathVerified = false, // violates rule 9
                firmwareCompatible = true,
                evidence = emptyList(),
            )
        }
    }

    @Test
    fun `unsupported cannot be writable`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityAccessRecord(
                capabilityId = "anc",
                support = CapabilityState.UNSUPPORTED,
                access = CapabilityAccess.WRITE_ONLY,
                writePathVerified = true,
                firmwareCompatible = true,
                evidence = emptyList(),
            )
        }
    }

    @Test
    fun `verified write path allows write on supported device`() {
        val record = CapabilityAccessRecord(
            capabilityId = "anc",
            support = CapabilityState.SUPPORTED_PERSISTENT,
            access = CapabilityAccess.READ_WRITE,
            writePathVerified = true,
            firmwareCompatible = true,
            evidence = listOf("read-back verified"),
        )
        val decision = CapabilityAccessEvaluator.canWrite(supported, record)
        assertTrue(decision.allowed)
    }

    @Test
    fun `firmware incompatibility denies`() {
        val record = CapabilityAccessRecord(
            capabilityId = "anc",
            support = CapabilityState.SUPPORTED_PERSISTENT,
            access = CapabilityAccess.READ_WRITE,
            writePathVerified = true,
            firmwareCompatible = false,
            evidence = listOf("read-back verified"),
        )
        val decision = CapabilityAccessEvaluator.canWrite(supported, record)
        assertFalse(decision.allowed)
        assertEquals(DenialReason.FIRMWARE_INCOMPATIBLE, decision.reason)
    }
}
