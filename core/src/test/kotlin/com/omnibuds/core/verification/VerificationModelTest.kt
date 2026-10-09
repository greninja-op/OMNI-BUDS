package com.omnibuds.core.verification

import com.omnibuds.core.capability.CoreFeature
import com.omnibuds.core.config.ConfigurationValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 18 §13.1: domain model tests.
 */
class VerificationModelTest {

    private val value = ConfigurationValue.ModeValue("anc-on", "ANC On")

    @Test
    fun `verification ids are unique`() {
        val a = VerificationId.new()
        val b = VerificationId.new()
        assertNotEquals(a.value, b.value)
    }

    @Test
    fun `blank verification id refused`() {
        try {
            VerificationId.of("  ")
            assertTrue(false, "should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `stages and outcomes are separate concepts`() {
        // 12 stages, 7 outcomes — different enums.
        assertTrue(VerificationStage.entries.size >= 10)
        assertEquals(7, VerificationOutcome.entries.size)
    }

    @Test
    fun `persistence scopes are ordered`() {
        val ordered = listOf(
            PersistenceScope.SESSION_ONLY,
            PersistenceScope.CONTROL_SESSION_PERSISTENT,
            PersistenceScope.CONNECTION_PERSISTENT,
            PersistenceScope.APPLICATION_RESTART_PERSISTENT,
            PersistenceScope.DEVICE_REBOOT_PERSISTENT,
            PersistenceScope.FIRMWARE_PERSISTENT,
        )
        // Ordinals increase with strength.
        for (i in 0 until ordered.size - 1) {
            assertTrue(ordered[i + 1].strongerThan(ordered[i]))
        }
        // UNKNOWN is never stronger.
        assertFalse(PersistenceScope.UNKNOWN.strongerThan(PersistenceScope.SESSION_ONLY))
        assertTrue(PersistenceScope.SESSION_ONLY.strongerThan(PersistenceScope.UNKNOWN))
    }

    @Test
    fun `application status has six states`() {
        assertEquals(6, ApplicationStatus.entries.size)
    }

    @Test
    fun `record creation starts at CREATED`() {
        val plan = VerificationPlan.writeOnly(CoreFeature.ANC, value)
        val record = VerificationRecord.create("device-key", plan, 1000L)
        assertEquals(VerificationStage.CREATED, record.stage)
        assertNull(record.outcome)
        assertFalse(record.isTerminal)
        assertEquals(ApplicationStatus.NOT_ATTEMPTED, record.applicationStatus)
        assertEquals(PersistenceScope.UNKNOWN, record.provenScope)
    }

    @Test
    fun `terminal record requires COMPLETED stage`() {
        val plan = VerificationPlan.writeOnly(CoreFeature.ANC, value)
        val base = VerificationRecord.create("device-key", plan, 1000L)
        // Outcome without COMPLETED → refused.
        try {
            base.copy(outcome = VerificationOutcome.VERIFIED)
            assertTrue(false, "should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `write-only plan has no read-back stages`() {
        val plan = VerificationPlan.writeOnly(CoreFeature.ANC, value)
        assertFalse(VerificationStage.INITIAL_READ_BACK in plan.stages)
        assertFalse(plan.supportsReadBack)
    }

    @Test
    fun `plan rejects read-back without support`() {
        try {
            VerificationPlan(
                targetFeature = CoreFeature.ANC,
                expectedValue = value,
                requestedScope = PersistenceScope.SESSION_ONLY,
                stages = listOf(VerificationStage.INITIAL_READ_BACK),
                supportsReadBack = false,
                supportsReconnectCheck = false,
                supportsRestartCheck = false,
                supportsPowerCycleCheck = false,
                ackTimeoutMillis = 1000L,
                readBackTimeoutMillis = 1000L,
                maxRetries = 0,
                retrySafe = false,
            )
            assertTrue(false, "should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
