package com.omnibuds.core.verification

import com.omnibuds.core.config.ConfigurationValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 18: evidence evaluation tests.
 */
class EvidenceEvaluatorTest {

    private val value = ConfigurationValue.ModeValue("anc-on", "ANC On")
    private val other = ConfigurationValue.ModeValue("anc-off", "ANC Off")

    private fun evidence(
        type: EvidenceType,
        observed: ConfigurationValue? = value,
        sessionId: String? = "sess-1",
        stale: Boolean = false,
    ) = VerificationEvidence(
        evidenceId = "e-${type.name}-${System.nanoTime()}",
        verificationId = VerificationId.of("vrf-test"),
        deviceKey = "device-key",
        targetKey = "anc",
        expectedValue = value,
        observedValue = observed,
        evidenceType = type,
        source = "test-protocol",
        protocolVersion = "1.0",
        timestampMillis = 1000L,
        sessionId = sessionId,
        connectionGeneration = 1L,
        isStale = stale,
        correlationId = null,
    )

    @Test
    fun `no evidence yields UNKNOWN`() {
        val result = EvidenceEvaluator.evaluate(emptyList(), "sess-1")
        assertEquals(PersistenceScope.UNKNOWN, result.strongestScope)
    }

    @Test
    fun `local preference is not hardware evidence`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.LOCAL_PREFERENCE_STORED)),
            "sess-1",
        )
        assertEquals(PersistenceScope.UNKNOWN, result.strongestScope)
    }

    @Test
    fun `acknowledgement proves session-only at best`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.COMMAND_ACKNOWLEDGEMENT)),
            "sess-1",
        )
        assertEquals(PersistenceScope.SESSION_ONLY, result.strongestScope)
    }

    @Test
    fun `device read-back proves session-only`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.DEVICE_READ_BACK)),
            "sess-1",
        )
        assertEquals(PersistenceScope.SESSION_ONLY, result.strongestScope)
    }

    @Test
    fun `reconnect read-back proves connection persistence`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(
                evidence(EvidenceType.DEVICE_READ_BACK),
                evidence(EvidenceType.RECONNECT_READ_BACK, sessionId = "sess-2"),
            ),
            "sess-2",
        )
        assertEquals(PersistenceScope.CONNECTION_PERSISTENT, result.strongestScope)
    }

    @Test
    fun `stale evidence cannot establish current state`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.DEVICE_READ_BACK, stale = true)),
            "sess-1",
        )
        assertEquals(PersistenceScope.UNKNOWN, result.strongestScope)
    }

    @Test
    fun `cross-session evidence cannot establish current state`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.DEVICE_READ_BACK, sessionId = "sess-old")),
            "sess-new",
        )
        assertEquals(PersistenceScope.UNKNOWN, result.strongestScope)
    }

    @Test
    fun `conflicting evidence is flagged`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(
                evidence(EvidenceType.DEVICE_READ_BACK, observed = value),
                evidence(EvidenceType.DEVICE_READ_BACK, observed = other),
            ),
            "sess-1",
        )
        assertTrue(result.hasConflicts)
    }

    @Test
    fun `consistent evidence has no conflicts`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(
                evidence(EvidenceType.DEVICE_READ_BACK, observed = value),
                evidence(EvidenceType.RECONNECT_READ_BACK, observed = value, sessionId = "sess-2"),
            ),
            "sess-2",
        )
        assertFalse(result.hasConflicts)
    }

    @Test
    fun `power-cycle read-back proves reboot persistence`() {
        val result = EvidenceEvaluator.evaluate(
            listOf(evidence(EvidenceType.POWER_CYCLE_READ_BACK)),
            "sess-1",
        )
        assertEquals(PersistenceScope.DEVICE_REBOOT_PERSISTENT, result.strongestScope)
    }
}
