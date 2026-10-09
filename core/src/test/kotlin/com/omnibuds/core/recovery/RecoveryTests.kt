package com.omnibuds.core.recovery

import com.omnibuds.core.common.RetryClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun failure(
    category: FailureCategory,
    mayHaveExecuted: Boolean = false,
) = FailureClassifier.classify(
    category = category,
    deviceId = "d1",
    sessionId = "s1",
    operationId = "op1",
    mayHaveExecuted = mayHaveExecuted,
    displayMessage = "test failure",
    correlationId = "corr-1",
)

private fun context(
    failure: ClassifiedFailure,
    attemptCount: Int = 0,
    maxAttempts: Int = 3,
    bluetoothEnabled: Boolean? = true,
    permissionGranted: Boolean? = true,
    backgroundRestricted: Boolean = false,
    cancelled: Boolean = false,
    sessionSuperseded: Boolean = false,
    authorizationValid: Boolean = true,
) = RecoveryContext(
    failure = failure,
    attemptCount = attemptCount,
    maxAttempts = maxAttempts,
    bluetoothEnabled = bluetoothEnabled,
    permissionGranted = permissionGranted,
    backgroundRestricted = backgroundRestricted,
    cancelled = cancelled,
    sessionSuperseded = sessionSuperseded,
    authorizationValid = authorizationValid,
)

class FailureClassifierTest {

    @Test
    fun `transient failures are safe to retry`() {
        for (c in listOf(
            FailureCategory.TRANSPORT_FAILURE,
            FailureCategory.CONNECTION_TIMEOUT,
            FailureCategory.PROTOCOL_TIMEOUT,
        )) {
            assertEquals(
                RetryClass.SAFE_TO_RETRY,
                FailureClassifier.retryClassFor(c),
                c.name,
            )
        }
    }

    @Test
    fun `ambiguous outcomes require re-read`() {
        assertEquals(
            RetryClass.RETRY_AFTER_REREAD,
            FailureClassifier.retryClassFor(FailureCategory.OPERATION_OUTCOME_UNKNOWN),
        )
        val f = failure(FailureCategory.OPERATION_OUTCOME_UNKNOWN)
        assertTrue(f.reconciliationRequired)
    }

    @Test
    fun `permission and permanent failures never retry`() {
        for (c in listOf(
            FailureCategory.PERMISSION_DENIED,
            FailureCategory.PROTOCOL_UNSUPPORTED,
            FailureCategory.OPERATION_REJECTED,
            FailureCategory.CANCELLED,
        )) {
            assertEquals(RetryClass.NEVER_RETRY, FailureClassifier.retryClassFor(c), c.name)
        }
    }

    @Test
    fun `timeout does not imply unsupported hardware`() {
        val f = failure(FailureCategory.CONNECTION_TIMEOUT)
        assertFalse(f.category == FailureCategory.PROTOCOL_UNSUPPORTED)
        assertFalse(f.category == FailureCategory.CAPABILITY_UNAVAILABLE)
    }

    @Test
    fun `permission error does not imply disconnection`() {
        val f = failure(FailureCategory.PERMISSION_DENIED)
        assertFalse(f.category == FailureCategory.DEVICE_DISCONNECTED)
    }

    @Test
    fun `metadata never carries payloads`() {
        val f = failure(FailureCategory.TRANSPORT_FAILURE)
        // The model has no field for raw payloads, secrets, or credentials.
        assertTrue(f.displayMessage.isNotBlank())
        assertFalse(f.displayMessage.contains("payload"))
    }
}

class RecoveryPolicyTest {

    @Test
    fun `cancellation always aborts`() {
        val c = context(failure(FailureCategory.TRANSPORT_FAILURE), cancelled = true)
        assertEquals(RecoveryDecision.ABORT_OPERATION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `superseded session aborts`() {
        val c = context(
            failure(FailureCategory.TRANSPORT_FAILURE),
            sessionSuperseded = true,
        )
        assertEquals(RecoveryDecision.ABORT_OPERATION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `ambiguous write reconciles instead of replaying`() {
        // A non-idempotent write whose outcome is unknown must never
        // be blindly replayed — even though the timeout was transient.
        val c = context(
            failure(FailureCategory.OPERATION_OUTCOME_UNKNOWN, mayHaveExecuted = true),
        )
        assertEquals(RecoveryDecision.RECONCILE_DEVICE_STATE, RecoveryPolicy.decide(c))
    }

    @Test
    fun `permission denied revalidates`() {
        val c = context(failure(FailureCategory.PERMISSION_DENIED))
        assertEquals(RecoveryDecision.REVALIDATE_PERMISSIONS, RecoveryPolicy.decide(c))
    }

    @Test
    fun `bluetooth off waits for adapter`() {
        val c = context(
            failure(FailureCategory.BLUETOOTH_DISABLED),
            bluetoothEnabled = false,
        )
        assertEquals(RecoveryDecision.WAIT_FOR_ADAPTER, RecoveryPolicy.decide(c))
    }

    @Test
    fun `disconnect retries connection when adapter is on`() {
        val c = context(failure(FailureCategory.DEVICE_DISCONNECTED))
        assertEquals(RecoveryDecision.RETRY_CONNECTION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `retry budget exhaustion requires user intervention`() {
        val c = context(
            failure(FailureCategory.DEVICE_BUSY),
            attemptCount = 3,
            maxAttempts = 3,
        )
        assertEquals(RecoveryDecision.REQUIRE_USER_INTERVENTION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `background restriction marks session unavailable`() {
        val c = context(
            failure(FailureCategory.DEVICE_BUSY),
            backgroundRestricted = true,
        )
        assertEquals(RecoveryDecision.MARK_SESSION_UNAVAILABLE, RecoveryPolicy.decide(c))
    }

    @Test
    fun `unsupported protocol aborts`() {
        val c = context(failure(FailureCategory.PROTOCOL_UNSUPPORTED))
        assertEquals(RecoveryDecision.ABORT_OPERATION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `stale state reconciles`() {
        val c = context(failure(FailureCategory.STALE_STATE))
        assertEquals(RecoveryDecision.RECONCILE_DEVICE_STATE, RecoveryPolicy.decide(c))
    }

    @Test
    fun `invalid authorization aborts retry`() {
        val c = context(
            failure(FailureCategory.DEVICE_BUSY),
            authorizationValid = false,
        )
        assertEquals(RecoveryDecision.ABORT_OPERATION, RecoveryPolicy.decide(c))
    }

    @Test
    fun `policy is deterministic`() {
        val c = context(failure(FailureCategory.TRANSPORT_FAILURE))
        assertEquals(RecoveryPolicy.decide(c), RecoveryPolicy.decide(c))
    }
}

class RecoveryStateMachineTest {

    @Test
    fun `happy path transitions`() {
        val m = RecoveryStateMachine()
        assertTrue(m.transition(RecoveryState.FAILURE_CLASSIFIED))
        assertTrue(m.transition(RecoveryState.RECOVERY_PLANNED))
        assertTrue(m.transition(RecoveryState.RETRY_SCHEDULED))
        assertTrue(m.transition(RecoveryState.RECOVERY_SUCCEEDED))
        assertTrue(m.reset())
        assertEquals(RecoveryState.IDLE, m.current)
    }

    @Test
    fun `illegal transitions are rejected`() {
        val m = RecoveryStateMachine()
        assertFalse(m.transition(RecoveryState.RECOVERY_SUCCEEDED))
        assertFalse(m.transition(RecoveryState.RECONNECTING))
        assertEquals(RecoveryState.IDLE, m.current)
    }

    @Test
    fun `retry can loop back to classification`() {
        val m = RecoveryStateMachine()
        m.transition(RecoveryState.FAILURE_CLASSIFIED)
        m.transition(RecoveryState.RECOVERY_PLANNED)
        m.transition(RecoveryState.RETRY_SCHEDULED)
        assertTrue(m.transition(RecoveryState.FAILURE_CLASSIFIED))
    }

    @Test
    fun `terminal states only reset to idle`() {
        val m = RecoveryStateMachine()
        m.transition(RecoveryState.FAILURE_CLASSIFIED)
        assertTrue(m.transition(RecoveryState.RECOVERY_ABORTED))
        assertFalse(m.transition(RecoveryState.RETRY_SCHEDULED))
        assertTrue(m.reset())
    }

    @Test
    fun `cancelled is terminal`() {
        val m = RecoveryStateMachine()
        m.transition(RecoveryState.FAILURE_CLASSIFIED)
        m.transition(RecoveryState.RECOVERY_PLANNED)
        assertTrue(m.transition(RecoveryState.RECOVERY_CANCELLED))
        assertFalse(m.transition(RecoveryState.RECOVERY_PLANNED))
    }
}

class RecoveryEventSinkTest {

    private fun event(id: String) = RecoveryEvent(
        eventId = id,
        correlationId = "corr-1",
        type = RecoveryEventType.FAILURE_CLASSIFIED,
        deviceId = "d1",
        sessionId = "s1",
        operationId = "op1",
        attemptNumber = 1,
        elapsedMillis = 10L,
        decision = RecoveryDecision.RETRY_OPERATION,
    )

    @Test
    fun `sink is bounded`() {
        val sink = RecoveryEventSink(capacity = 3)
        repeat(5) { sink.record(event("e$it")) }
        assertEquals(3, sink.size)
        val ids = sink.snapshot().map { it.eventId }
        assertEquals(listOf("e2", "e3", "e4"), ids)
    }

    @Test
    fun `events carry no sensitive payload`() {
        val e = event("e1")
        // The model has no payload/secret/credential fields.
        assertTrue(e.deviceId == "d1")
        assertTrue(e.correlationId.isNotBlank())
    }

    @Test
    fun `clear empties the sink`() {
        val sink = RecoveryEventSink()
        sink.record(event("e1"))
        sink.clear()
        assertEquals(0, sink.size)
    }
}
