package com.omnibuds.core.testkit.crossdevice

import com.omnibuds.core.testkit.TestCase
import com.omnibuds.core.testkit.TestResult
import com.omnibuds.core.testkit.TestResultCategory

/**
 * How a test case was treated for a profile.
 */
enum class TestDisposition {
    /** Ran against the profile. */
    EXECUTED,

    /** Profile lacks a required capability. */
    NOT_APPLICABLE,

    /** Deliberately skipped with a reason. */
    SKIPPED,

    /** Blocked by missing infrastructure/fixture. */
    BLOCKED,
}

/**
 * One planned test execution.
 */
data class PlannedTest(
    val testCase: TestCase,
    val profile: DeviceProfile,
    val disposition: TestDisposition,
    val reason: String? = null,
)

/**
 * A deterministic campaign plan.
 */
data class CampaignPlan(
    val campaignId: String,
    val planned: List<PlannedTest>,
) {
    /** Tests that will actually execute. */
    val executable: List<PlannedTest>
        get() = planned.filter { it.disposition == TestDisposition.EXECUTED }
}

/**
 * Deterministic campaign planner.
 *
 * Phase 31 (OB-P31-REQ-004): selects applicable tests per profile.
 * Capability prerequisites come from test tags of the form
 * `requires-capability:<id>`.
 */
object CampaignPlanner {

    /**
     * Plan a campaign over validated profiles and test cases.
     * Deterministic: profiles and tests are sorted by id.
     */
    fun plan(
        campaignId: String,
        profiles: List<DeviceProfile>,
        tests: List<TestCase>,
        availableFixtures: Set<String> = emptySet(),
    ): CampaignPlan {
        val planned = mutableListOf<PlannedTest>()
        for (profile in profiles.sortedBy { it.profileId }) {
            for (test in tests.sortedBy { it.id }) {
                planned.add(dispose(test, profile, availableFixtures))
            }
        }
        return CampaignPlan(campaignId, planned)
    }

    private fun dispose(
        test: TestCase,
        profile: DeviceProfile,
        availableFixtures: Set<String>,
    ): PlannedTest {
        // Capability prerequisites.
        val required = test.tags
            .filter { it.startsWith("requires-capability:") }
            .map { it.removePrefix("requires-capability:") }
        val missing = required.filter { it !in profile.capabilities }
        if (missing.isNotEmpty()) {
            return PlannedTest(
                test, profile, TestDisposition.NOT_APPLICABLE,
                "profile lacks capabilities: ${missing.joinToString()}",
            )
        }
        // Fixture availability.
        val missingFixtures = test.fixtures.filter { it !in availableFixtures }
        if (missingFixtures.isNotEmpty()) {
            return PlannedTest(
                test, profile, TestDisposition.BLOCKED,
                "missing fixtures: ${missingFixtures.joinToString()}",
            )
        }
        return PlannedTest(test, profile, TestDisposition.EXECUTED)
    }
}
