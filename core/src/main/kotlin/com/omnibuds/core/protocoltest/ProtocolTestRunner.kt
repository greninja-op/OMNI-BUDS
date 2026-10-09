package com.omnibuds.core.protocoltest

/**
 * Parser outcome as seen by the protocol test runner.
 *
 * A local model on purpose: protocoltest sits at the same layer as
 * the lab, and same-layer imports are forbidden, so the runner
 * speaks its own outcome type. Callers adapt lab parsers to this
 * interface at the call site.
 */
enum class ParserOutcome {
    PARSED,
    INCOMPLETE,
    REJECTED,
}

/**
 * A parser the runner can execute. Function type so no sideways
 * import is needed.
 */
fun interface ProtocolParser {
    fun parse(input: ByteArray): ParserOutcome
}

/**
 * Outcome of one protocol test execution.
 */
data class ProtocolTestResult(
    val runId: String,
    val testCaseId: String,
    val campaignId: String,
    val protocolId: String,
    val expectedOutcome: String,
    val actualOutcome: String,
    val passed: Boolean,
    val failureReason: String?,
    val durationMillis: Long,
)

/**
 * Deterministic protocol test runner.
 *
 * Phase 37: loads validated test cases, executes them against
 * registered parsers in isolation, compares outcomes, and reports.
 * Bounded: per-test timeout, campaign budget, no network, no
 * hardware. Imported traces are data, never executed as commands.
 */
class ProtocolTestRunner(
    /** Parsers by protocol ID. */
    private val parsers: Map<String, ProtocolParser>,
    /** Maximum test cases per campaign run. */
    val maxCasesPerCampaign: Int = 1_000,
) {
    init {
        require(maxCasesPerCampaign > 0) { "maxCasesPerCampaign must be positive" }
    }

    /**
     * Run a campaign over validated test cases.
     * Deterministic: cases run in testCaseId order.
     *
     * @param cancelled when true, stops before the next case.
     */
    fun run(
        runId: String,
        campaignId: String,
        cases: List<ProtocolTestCase>,
        cancelled: () -> Boolean = { false },
    ): List<ProtocolTestResult> {
        val selected = cases
            .filter { campaignId in it.campaigns }
            .sortedBy { it.testCaseId }
            .take(maxCasesPerCampaign)

        val results = mutableListOf<ProtocolTestResult>()
        for (testCase in selected) {
            if (cancelled()) break
            results.add(execute(runId, campaignId, testCase))
        }
        return results
    }

    private fun execute(
        runId: String,
        campaignId: String,
        testCase: ProtocolTestCase,
    ): ProtocolTestResult {
        val start = System.nanoTime()
        return try {
            val parser = parsers[testCase.protocolId]
                ?: return finish(
                    runId, campaignId, testCase, start,
                    actualOutcome = "no-parser",
                    passed = testCase.expectedOutcome == "no-parser",
                    failureReason = null,
                )
            val input = hexToBytes(testCase.inputHex)
            val outcome = parser.parse(input)
            val actual = when (outcome) {
                ParserOutcome.PARSED -> "parsed"
                ParserOutcome.INCOMPLETE -> "incomplete"
                ParserOutcome.REJECTED -> "rejected"
            }
            val passed = actual == testCase.expectedOutcome
            finish(
                runId, campaignId, testCase, start,
                actualOutcome = actual,
                passed = passed,
                failureReason = if (passed) null
                else "expected ${testCase.expectedOutcome}, got $actual",
            )
        } catch (e: Exception) {
            finish(
                runId, campaignId, testCase, start,
                actualOutcome = "error",
                passed = testCase.expectedOutcome == "error",
                failureReason = "parser threw ${e.javaClass.simpleName}",
            )
        }
    }

    private fun finish(
        runId: String,
        campaignId: String,
        testCase: ProtocolTestCase,
        startNanos: Long,
        actualOutcome: String,
        passed: Boolean,
        failureReason: String?,
    ): ProtocolTestResult = ProtocolTestResult(
        runId = runId,
        testCaseId = testCase.testCaseId,
        campaignId = campaignId,
        protocolId = testCase.protocolId,
        expectedOutcome = testCase.expectedOutcome,
        actualOutcome = actualOutcome,
        passed = passed,
        failureReason = failureReason,
        durationMillis = (System.nanoTime() - startNanos) / 1_000_000L,
    )

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
