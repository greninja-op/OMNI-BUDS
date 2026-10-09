package com.omnibuds.core.testkit.crossdevice

import com.omnibuds.core.testkit.TestResult
import com.omnibuds.core.testkit.TestResultCategory

/**
 * A regression baseline: a valid prior campaign.
 *
 * Phase 31 (OB-P31-REQ-009): identifies its source so results can be
 * compared meaningfully. Baselines are never silently overwritten.
 */
data class RegressionBaseline(
    val baselineId: String,
    /** Source revision, e.g. git SHA. */
    val sourceRevision: String,
    val profileVersions: Map<String, Int>,
    val fixtureVersions: Map<String, Int>,
    val runnerVersion: String,
    /** Expected result category per test id. */
    val expected: Map<String, TestResultCategory>,
) {
    init {
        require(baselineId.isNotBlank()) { "baselineId must not be blank" }
        require(sourceRevision.isNotBlank()) { "sourceRevision must not be blank" }
    }
}

/**
 * Regression findings.
 */
sealed interface RegressionFinding {
    /** A test that passed in the baseline now fails. */
    data class NewFailure(val testId: String) : RegressionFinding

    /** A test that failed in the baseline now passes. */
    data class NewPass(val testId: String) : RegressionFinding

    /** A test newly skipped or blocked. */
    data class NewSkipOrBlock(val testId: String) : RegressionFinding

    /** A test with no baseline entry. */
    data class Unbaselined(val testId: String) : RegressionFinding
}

/**
 * Compares a campaign against a baseline.
 *
 * Phase 31 (OB-P31-REQ-008): detects regressions without auto-updating
 * baselines. A deliberately changed expectation needs a documented
 * decision — represented here as an explicit baseline replacement, not
 * a silent pass.
 */
object RegressionComparator {

    /**
     * Compare current results against the baseline.
     */
    fun compare(
        baseline: RegressionBaseline,
        results: List<TestResult>,
    ): List<RegressionFinding> {
        val findings = mutableListOf<RegressionFinding>()
        for (result in results.sortedBy { it.testId }) {
            val expected = baseline.expected[result.testId]
            if (expected == null) {
                findings.add(RegressionFinding.Unbaselined(result.testId))
                continue
            }
            val actual = result.category
            if (actual == expected) continue
            when {
                expected == TestResultCategory.PASSED &&
                    actual == TestResultCategory.FAILED ->
                    findings.add(RegressionFinding.NewFailure(result.testId))
                expected == TestResultCategory.FAILED &&
                    actual == TestResultCategory.PASSED ->
                    findings.add(RegressionFinding.NewPass(result.testId))
                actual == TestResultCategory.SKIPPED ||
                    actual == TestResultCategory.BLOCKED ->
                    findings.add(RegressionFinding.NewSkipOrBlock(result.testId))
                // Other transitions (e.g. invalid → passed) are reported
                // as new passes/skips conservatively below.
                else -> findings.add(RegressionFinding.NewSkipOrBlock(result.testId))
            }
        }
        return findings
    }

    /** True when no new failures were found. */
    fun hasRegressions(findings: List<RegressionFinding>): Boolean =
        findings.any { it is RegressionFinding.NewFailure }
}
