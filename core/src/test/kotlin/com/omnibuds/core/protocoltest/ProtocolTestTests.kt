package com.omnibuds.core.protocoltest

import com.omnibuds.core.lab.LabParser
import com.omnibuds.core.lab.ParseOutcome
import com.omnibuds.core.testkit.TestEvidenceLevel
import com.omnibuds.core.testkit.TestResult
import com.omnibuds.core.testkit.TestResultCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun testCase(
    id: String = "proto.parser.basic",
    schemaVersion: Int = 1,
    fixtureId: String = "fixture-1",
    inputHex: String = "01020304",
    expectedOutcome: String = "parsed",
    timeoutMillis: Long = 1_000L,
    campaigns: Set<String> = setOf("parser-regression"),
) = ProtocolTestCase(
    testCaseId = id,
    schemaVersion = schemaVersion,
    protocolId = "test.proto",
    protocolVersion = "1.0",
    fixtureId = fixtureId,
    fixtureOrigin = ProtocolFixtureOrigin.SYNTHETIC,
    inputHex = inputHex,
    expectedOutcome = expectedOutcome,
    expectedFailureCategory = null,
    timeoutMillis = timeoutMillis,
    campaigns = campaigns,
    requirementRefs = listOf("A37-REQ-004"),
)

/** A minimal deterministic parser for tests. */
private class FakeParser : ProtocolParser {
    override fun parse(input: ByteArray): ParserOutcome {
        if (input.isEmpty()) return ParserOutcome.INCOMPLETE
        if (input[0] == 0xFF.toByte()) return ParserOutcome.REJECTED
        return ParserOutcome.PARSED
    }
}

class ProtocolTestCaseValidatorTest {

    @Test
    fun `valid case passes`() {
        assertTrue(
            ProtocolTestCaseValidator.validate(testCase()) is TestCaseValidation.Valid,
        )
    }

    @Test
    fun `unsupported schema version fails`() {
        val v = ProtocolTestCaseValidator.validate(testCase(schemaVersion = 99))
        assertTrue(v is TestCaseValidation.Invalid)
    }

    @Test
    fun `path-like fixture id fails`() {
        for (bad in listOf("../evil", "a/b", "a\\b")) {
            val v = ProtocolTestCaseValidator.validate(testCase(fixtureId = bad))
            assertTrue(v is TestCaseValidation.Invalid, bad)
        }
    }

    @Test
    fun `non-hex input fails`() {
        val v = ProtocolTestCaseValidator.validate(testCase(inputHex = "zz"))
        assertTrue(v is TestCaseValidation.Invalid)
    }

    @Test
    fun `oversized input fails`() {
        val v = ProtocolTestCaseValidator.validate(
            testCase(inputHex = "00".repeat(ProtocolTestCase.MAX_HEX_CHARS / 2 + 1)),
        )
        assertTrue(v is TestCaseValidation.Invalid)
    }

    @Test
    fun `impossible timeout fails`() {
        assertTrue(
            ProtocolTestCaseValidator.validate(testCase(timeoutMillis = 0))
                is TestCaseValidation.Invalid,
        )
        assertTrue(
            ProtocolTestCaseValidator.validate(
                testCase(timeoutMillis = ProtocolTestCase.MAX_TIMEOUT_MILLIS + 1),
            ) is TestCaseValidation.Invalid,
        )
    }

    @Test
    fun `blank id fails`() {
        assertTrue(
            ProtocolTestCaseValidator.validate(testCase(id = "  "))
                is TestCaseValidation.Invalid,
        )
    }
}

class ProtocolTestRunnerTest {

    private val runner = ProtocolTestRunner(mapOf("test.proto" to FakeParser()))

    @Test
    fun `passing case reports passed`() {
        val results = runner.run("run-1", "parser-regression", listOf(testCase()))
        assertEquals(1, results.size)
        assertTrue(results[0].passed)
        assertEquals("parsed", results[0].actualOutcome)
    }

    @Test
    fun `failing case reports failure reason`() {
        val results = runner.run(
            "run-1", "parser-regression",
            listOf(testCase(expectedOutcome = "rejected")),
        )
        assertFalse(results[0].passed)
        assertTrue(results[0].failureReason!!.contains("expected rejected"))
    }

    @Test
    fun `empty input gives incomplete`() {
        val results = runner.run(
            "run-1", "parser-regression",
            listOf(testCase(inputHex = "", expectedOutcome = "incomplete")),
        )
        assertTrue(results[0].passed)
    }

    @Test
    fun `unknown protocol gives no-parser`() {
        val bad = testCase().copy(protocolId = "nope.proto", expectedOutcome = "no-parser")
        val results = runner.run("run-1", "parser-regression", listOf(bad))
        assertTrue(results[0].passed)
        assertEquals("no-parser", results[0].actualOutcome)
    }

    @Test
    fun `campaign filter selects matching cases`() {
        val cases = listOf(
            testCase(id = "a", campaigns = setOf("parser-regression")),
            testCase(id = "b", campaigns = setOf("other")),
        )
        val results = runner.run("run-1", "parser-regression", cases)
        assertEquals(listOf("a"), results.map { it.testCaseId })
    }

    @Test
    fun `execution order is deterministic`() {
        val cases = listOf(testCase(id = "z"), testCase(id = "a"))
        val r1 = runner.run("run-1", "parser-regression", cases)
        val r2 = runner.run("run-1", "parser-regression", cases.reversed())
        assertEquals(
            r1.map { it.testCaseId },
            r2.map { it.testCaseId },
        )
        assertEquals(listOf("a", "z"), r1.map { it.testCaseId })
    }

    @Test
    fun `cancellation stops the run`() {
        var calls = 0
        val results = runner.run(
            "run-1", "parser-regression",
            listOf(testCase(id = "a"), testCase(id = "b")),
        ) { ++calls > 1 }
        assertTrue(results.size <= 1)
    }

    @Test
    fun `parser exception becomes error outcome not a crash`() {
        val exploding = ProtocolParser { throw IllegalStateException("boom") }
        val r = ProtocolTestRunner(mapOf("test.proto" to exploding))
        val results = r.run(
            "run-1", "parser-regression",
            listOf(testCase(expectedOutcome = "error")),
        )
        assertTrue(results[0].passed)
        assertEquals("error", results[0].actualOutcome)
    }

    @Test
    fun `results convert to phase 30 model`() {
        // Phase 30 integration: the conversion lives at the call site
        // (protocoltest may not import testkit sideways), and this
        // test proves the mapping works.
        val results = runner.run("run-1", "parser-regression", listOf(testCase()))
        val r = results[0]
        val tr = TestResult(
            testId = r.testCaseId,
            suiteId = r.campaignId,
            category = if (r.passed) TestResultCategory.PASSED
            else TestResultCategory.FAILED,
            diagnostics = r.failureReason,
            durationMillis = r.durationMillis,
            evidenceLevel = TestEvidenceLevel.SIMULATED,
        )
        assertEquals(TestResultCategory.PASSED, tr.category)
        assertEquals("proto.parser.basic", tr.testId)
        assertEquals("parser-regression", tr.suiteId)
    }

    @Test
    fun `a real LabParser adapts to the runner`() {
        // Integration proof: the runner never imports the lab, but a
        // lab parser adapts cleanly at the call site.
        val labParser = object : LabParser {
            override val parserId = "lab.adapter"
            override val protocolId: String? = "test.proto"
            override val minInputBytes = 0
            override val maxInputBytes = 1024
            override fun parse(input: ByteArray): ParseOutcome =
                if (input.isEmpty()) ParseOutcome.Incomplete(bytesNeeded = 1)
                else ParseOutcome.UnsupportedFormat
        }
        val adapted = ProtocolParser { input ->
            when (labParser.parse(input)) {
                is ParseOutcome.Parsed -> ParserOutcome.PARSED
                is ParseOutcome.Incomplete -> ParserOutcome.INCOMPLETE
                is ParseOutcome.UnsupportedFormat -> ParserOutcome.REJECTED
                is ParseOutcome.Malformed -> ParserOutcome.REJECTED
                is ParseOutcome.UnsupportedVersion -> ParserOutcome.REJECTED
                is ParseOutcome.LimitExceeded -> ParserOutcome.REJECTED
            }
        }
        val r = ProtocolTestRunner(mapOf("test.proto" to adapted))
        val results = r.run(
            "run-1", "parser-regression",
            listOf(testCase(inputHex = "", expectedOutcome = "incomplete")),
        )
        assertTrue(results[0].passed)
    }

    @Test
    fun `malformed input campaign rejects safely`() {
        val cases = listOf(
            testCase(
                id = "proto.malformed.ff",
                inputHex = "ff",
                expectedOutcome = "rejected",
                campaigns = setOf("malformed-input-safety"),
            ),
            testCase(
                id = "proto.malformed.empty",
                inputHex = "",
                expectedOutcome = "incomplete",
                campaigns = setOf("malformed-input-safety"),
            ),
        )
        val results = runner.run("run-1", "malformed-input-safety", cases)
        assertTrue(results.all { it.passed })
    }
}

class ProtocolCampaignTest {

    @Test
    fun `prefix selection works`() {
        val c = ProtocolCampaigns.PARSER_REGRESSION
        assertTrue(c.selects("proto.parser.basic"))
        assertFalse(c.selects("proto.framing.basic"))
    }

    @Test
    fun `excludes win over includes`() {
        val c = ProtocolCampaign(
            campaignId = "x",
            description = "x",
            includes = setOf("proto."),
            excludes = setOf("proto.parser.bad"),
        )
        assertFalse(c.selects("proto.parser.bad"))
        assertTrue(c.selects("proto.parser.good"))
    }

    @Test
    fun `empty includes selects everything`() {
        assertTrue(ProtocolCampaigns.FULL_OFFLINE.selects("anything.at.all"))
    }

    @Test
    fun `all campaigns are defined`() {
        assertEquals(4, ProtocolCampaigns.ALL.size)
        assertEquals(
            ProtocolCampaigns.ALL.map { it.campaignId }.toSet().size,
            ProtocolCampaigns.ALL.size,
        )
    }
}
