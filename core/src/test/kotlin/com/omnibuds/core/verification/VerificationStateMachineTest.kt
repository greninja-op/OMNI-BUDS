package com.omnibuds.core.verification

import com.omnibuds.core.capability.CoreFeature
import com.omnibuds.core.config.ConfigurationValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 18 §13.1/13.2: state machine and workflow tests.
 */
class VerificationStateMachineTest {

    private val value = ConfigurationValue.ModeValue("anc-on", "ANC On")
    private var now = 1000L

    private fun plan() = VerificationPlan(
        targetFeature = CoreFeature.ANC,
        expectedValue = value,
        requestedScope = PersistenceScope.CONNECTION_PERSISTENT,
        stages = listOf(
            VerificationStage.ELIGIBILITY_CHECK,
            VerificationStage.APPLY_REQUESTED,
            VerificationStage.APPLY_ACKNOWLEDGED,
            VerificationStage.INITIAL_READ_BACK,
            VerificationStage.RECONNECT_CHECK,
            VerificationStage.EVALUATING_EVIDENCE,
            VerificationStage.COMPLETED,
        ),
        supportsReadBack = true,
        supportsReconnectCheck = true,
        supportsRestartCheck = false,
        supportsPowerCycleCheck = false,
        ackTimeoutMillis = 5000L,
        readBackTimeoutMillis = 5000L,
        maxRetries = 0,
        retrySafe = false,
    )

    private fun fresh() = VerificationRecord.create("device-key", plan(), now)

    private fun step(record: VerificationRecord, event: VerificationEvent): VerificationRecord {
        now += 100
        return VerificationStateMachine.transition(record, event, now)
    }

    @Test
    fun `happy path - apply, ack, read-back match`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        assertEquals(ApplicationStatus.REQUESTED, r.applicationStatus)
        r = step(r, VerificationEvent.Acknowledged("corr-1"))
        assertEquals(ApplicationStatus.ACKNOWLEDGED, r.applicationStatus)
        r = step(r, VerificationEvent.ReadBackReceived(value, false, "sess-1"))
        assertEquals(ApplicationStatus.READ_BACK_CONFIRMED, r.applicationStatus)
        assertEquals(PersistenceScope.SESSION_ONLY, r.provenScope)
    }

    @Test
    fun `ineligible yields UNSUPPORTED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(false, "no read-back support"))
        assertEquals(VerificationOutcome.UNSUPPORTED, r.outcome)
        assertTrue(r.isTerminal)
    }

    @Test
    fun `rejected command yields FAILED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Rejected("device rejected"))
        assertEquals(VerificationOutcome.FAILED, r.outcome)
        assertEquals(ApplicationStatus.REJECTED, r.applicationStatus)
    }

    @Test
    fun `read-back mismatch yields NOT_VERIFIED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Acknowledged(null))
        val wrong = ConfigurationValue.ModeValue("anc-off", "ANC Off")
        r = step(r, VerificationEvent.ReadBackReceived(wrong, false, "sess-1"))
        assertEquals(VerificationOutcome.NOT_VERIFIED, r.outcome)
    }

    @Test
    fun `timeout yields INCONCLUSIVE not FAILED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.TimedOut("acknowledgement"))
        // Ambiguous — never success, never failure.
        assertEquals(VerificationOutcome.INCONCLUSIVE, r.outcome)
    }

    @Test
    fun `disconnect during operation yields INCONCLUSIVE`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.DeviceDisconnected("sess-1"))
        assertEquals(VerificationOutcome.INCONCLUSIVE, r.outcome)
    }

    @Test
    fun `disconnect after confirmation does not un-confirm`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Acknowledged(null))
        r = step(r, VerificationEvent.ReadBackReceived(value, false, "sess-1"))
        r = step(r, VerificationEvent.DeviceDisconnected("sess-1"))
        // Already confirmed; still not terminal, confirmation stands.
        assertEquals(ApplicationStatus.READ_BACK_CONFIRMED, r.applicationStatus)
    }

    @Test
    fun `reconnect with matching read-back proves connection persistence`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Acknowledged(null))
        r = step(r, VerificationEvent.ReadBackReceived(value, false, "sess-1"))
        r = step(r, VerificationEvent.LifecycleBoundaryObserved(LifecycleBoundary.DISCONNECT_RECONNECT))
        r = step(r, VerificationEvent.PostBoundaryReadBack(LifecycleBoundary.DISCONNECT_RECONNECT, value, "sess-2"))
        assertEquals(PersistenceScope.CONNECTION_PERSISTENT, r.provenScope)
    }

    @Test
    fun `reconnect with different read-back yields NOT_VERIFIED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Acknowledged(null))
        r = step(r, VerificationEvent.ReadBackReceived(value, false, "sess-1"))
        r = step(r, VerificationEvent.LifecycleBoundaryObserved(LifecycleBoundary.DISCONNECT_RECONNECT))
        val wrong = ConfigurationValue.ModeValue("anc-off", "ANC Off")
        r = step(r, VerificationEvent.PostBoundaryReadBack(LifecycleBoundary.DISCONNECT_RECONNECT, wrong, "sess-2"))
        assertEquals(VerificationOutcome.NOT_VERIFIED, r.outcome)
    }

    @Test
    fun `cancelled yields CANCELLED`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.Cancelled)
        assertEquals(VerificationOutcome.CANCELLED, r.outcome)
    }

    @Test
    fun `terminal record cannot transition`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(false, "nope"))
        try {
            step(r, VerificationEvent.Cancelled)
            assertTrue(false, "should throw")
        } catch (e: IllegalStateException) {
            assertTrue(true)
        }
    }

    @Test
    fun `malformed read-back yields INCONCLUSIVE`() {
        var r = fresh()
        r = step(r, VerificationEvent.EligibilityDetermined(true, "ok"))
        r = step(r, VerificationEvent.ApplyRequested)
        r = step(r, VerificationEvent.Acknowledged(null))
        r = step(r, VerificationEvent.ReadBackReceived(null, true, "sess-1"))
        assertEquals(VerificationOutcome.INCONCLUSIVE, r.outcome)
    }
}
