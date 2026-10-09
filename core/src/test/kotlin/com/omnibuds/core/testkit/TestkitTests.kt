package com.omnibuds.core.testkit

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun request(id: String = "test.cmd") =
    TransportRequest(id, ByteArray(0), timeoutMillis = 1_000L)

class TestkitContractTest {

    @Test
    fun `test case requires id name and positive timeout`() {
        val tc = TestCase("transport.scripted-timeout", "Scripted timeout", TestCategory.UNIT)
        assertEquals("transport.scripted-timeout", tc.id)
        assertEquals(DeterminismClass.DETERMINISTIC, tc.determinism)
    }

    @Test
    fun `blank id is rejected`() {
        try {
            TestCase("", "x", TestCategory.UNIT)
            error("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("id"))
        }
    }

    @Test
    fun `non-positive timeout is rejected`() {
        try {
            TestCase("a.b", "x", TestCategory.UNIT, timeoutMillis = 0L)
            error("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("timeout"))
        }
    }
}

class FixtureValidationTest {

    private val schemas = mapOf("capability-snapshot" to 1..2)

    @Test
    fun `valid fixture passes`() {
        val f = TestFixture("f1", "capability-snapshot", 1, FixtureProvenance.SYNTHETIC, emptyMap())
        assertTrue(FixtureValidator.validate(f, schemas) is FixtureValidation.Valid)
    }

    @Test
    fun `unknown schema fails`() {
        val f = TestFixture("f1", "nope", 1, FixtureProvenance.SYNTHETIC, emptyMap())
        val v = FixtureValidator.validate(f, schemas)
        assertTrue(v is FixtureValidation.Invalid)
    }

    @Test
    fun `unsupported version fails`() {
        val f = TestFixture("f1", "capability-snapshot", 9, FixtureProvenance.SYNTHETIC, emptyMap())
        val v = FixtureValidator.validate(f, schemas)
        assertTrue(v is FixtureValidation.Invalid)
    }

    @Test
    fun `real capture without consent fails`() {
        val f = TestFixture("f1", "capability-snapshot", 1, FixtureProvenance.REAL_CAPTURE, emptyMap())
        val v = FixtureValidator.validate(f, schemas)
        assertTrue(v is FixtureValidation.Invalid)
    }
}

class ScriptedTransportTest {

    @Test
    fun `scripted success responds`() = runTest {
        val t = ScriptedTransport()
        t.script(ScriptedOutcome.Respond(TransportResponse("test.cmd", byteArrayOf(1), true)))
        assertTrue(t.open() is OperationOutcome.Success)
        val result = t.exchange(request(), 1_000L)
        assertTrue(result is OperationOutcome.Success)
        assertEquals(0, t.remaining())
    }

    @Test
    fun `script exhaustion fails closed`() = runTest {
        val t = ScriptedTransport()
        t.open()
        val result = t.exchange(request(), 1_000L)
        assertTrue(result is OperationOutcome.Failure)
        val error = (result as OperationOutcome.Failure).error
        assertEquals(OmniBudsErrorCategory.TIMEOUT, error.category)
    }

    @Test
    fun `scripted disconnect closes the channel`() = runTest {
        val t = ScriptedTransport()
        t.script(ScriptedOutcome.Disconnect)
        t.open()
        val result = t.exchange(request(), 1_000L)
        assertTrue(result is OperationOutcome.Failure)
        assertFalse(t.isOpen)
    }

    @Test
    fun `exchange on closed transport fails`() = runTest {
        val t = ScriptedTransport()
        val result = t.exchange(request(), 1_000L)
        assertTrue(result is OperationOutcome.Failure)
        assertEquals(
            OmniBudsErrorCategory.DEVICE_DISCONNECTED,
            (result as OperationOutcome.Failure).error.category,
        )
    }

    @Test
    fun `scripted failure carries the category`() = runTest {
        val t = ScriptedTransport()
        t.script(ScriptedOutcome.Fail(OmniBudsErrorCategory.PERMISSION_DENIED, "nope"))
        t.open()
        val result = t.exchange(request(), 1_000L)
        assertTrue(result is OperationOutcome.Failure)
        assertEquals(
            OmniBudsErrorCategory.PERMISSION_DENIED,
            (result as OperationOutcome.Failure).error.category,
        )
    }

    @Test
    fun `exchanges are recorded in order`() = runTest {
        val t = ScriptedTransport()
        t.script(
            ScriptedOutcome.Respond(TransportResponse("a", null, true)),
            ScriptedOutcome.Respond(TransportResponse("b", null, true)),
        )
        t.open()
        t.exchange(request("a"), 1_000L)
        t.exchange(request("b"), 1_000L)
        assertEquals(listOf("a", "b"), t.exchanges.map { it.commandId })
    }

    @Test
    fun `transport kind is preserved`() {
        assertEquals(TransportKind.GATT, ScriptedTransport().kind)
        assertEquals(TransportKind.RFCOMM, ScriptedTransport(kind = TransportKind.RFCOMM).kind)
    }
}

class FailureInjectionTest {

    @Test
    fun `seeded injector is deterministic`() {
        val a = FailureInjector(seed = 42L)
        val b = FailureInjector(seed = 42L)
        val ra = (1..20).map { a.shouldFail(0.5, "x") }
        val rb = (1..20).map { b.shouldFail(0.5, "x") }
        assertEquals(ra, rb)
    }

    @Test
    fun `zero probability never fails`() {
        val injector = FailureInjector()
        repeat(20) { assertFalse(injector.shouldFail(0.0, "x")) }
        assertTrue(injector.injectedFailures.isEmpty())
    }

    @Test
    fun `full probability always fails`() {
        val injector = FailureInjector()
        repeat(5) { assertTrue(injector.shouldFail(1.0, "x")) }
        assertEquals(5, injector.injectedFailures.size)
    }

    @Test
    fun `reset clears state`() {
        val injector = FailureInjector()
        injector.shouldFail(1.0, "x")
        injector.reset()
        assertTrue(injector.injectedFailures.isEmpty())
    }

    @Test
    fun `invalid probability rejected`() {
        try {
            FailureInjector().shouldFail(1.5, "x")
            error("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("probability"))
        }
    }
}

class TestEvidenceTest {

    @Test
    fun `all-passed requires every result passed`() {
        val report = TestReport(
            "s1",
            listOf(
                TestResult("a", "s1", TestResultCategory.PASSED),
                TestResult("b", "s1", TestResultCategory.SKIPPED),
            ),
        )
        assertFalse(report.allPassed)
        assertEquals(1, report.skipped)
    }

    @Test
    fun `empty report is not all-passed`() {
        assertFalse(TestReport("s1", emptyList()).allPassed)
    }

    @Test
    fun `summary lists non-passing results`() {
        val report = TestReport(
            "s1",
            listOf(
                TestResult("a", "s1", TestResultCategory.PASSED),
                TestResult("b", "s1", TestResultCategory.FAILED, errorCategory = "ASSERTION"),
            ),
        )
        val summary = report.summary()
        assertTrue(summary.contains("b: FAILED"))
        assertTrue(summary.contains("failed=1"))
    }

    @Test
    fun `simulated evidence never claims hardware`() {
        val r = TestResult("a", "s1", TestResultCategory.PASSED)
        assertEquals(TestEvidenceLevel.SIMULATED, r.evidenceLevel)
    }

    @Test
    fun `diagnostics are length-capped`() {
        try {
            TestResult("a", "s1", TestResultCategory.FAILED, diagnostics = "x".repeat(2001))
            error("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("2000"))
        }
    }
}

class TestIsolationTest {

    @Test
    fun `scripted transports do not share state`() = runTest {
        val a = ScriptedTransport()
        val b = ScriptedTransport()
        a.script(ScriptedOutcome.Respond(TransportResponse("x", null, true)))
        a.open(); b.open()
        a.exchange(request("x"), 1_000L)
        assertEquals(0, a.remaining())
        assertEquals(0, b.exchanges.size)
        // b has an empty script: fails closed, no cross-contamination
        assertTrue(b.exchange(request("y"), 1_000L) is OperationOutcome.Failure)
    }

    @Test
    fun `injector reset gives repeatable runs`() {
        val injector = FailureInjector(seed = 7L)
        val first = (1..10).map { injector.shouldFail(0.5, "x") }
        injector.reset()
        val second = (1..10).map { injector.shouldFail(0.5, "x") }
        assertEquals(first, second)
    }
}
