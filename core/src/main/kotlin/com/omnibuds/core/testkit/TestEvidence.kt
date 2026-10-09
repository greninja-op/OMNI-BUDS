package com.omnibuds.core.testkit

/**
 * Test result categories.
 *
 * Phase 30 (OB-P30-REQ-005/020): skipped and blocked are never passes.
 */
enum class TestResultCategory {
    PASSED,
    FAILED,
    SKIPPED,
    BLOCKED,
    CANCELLED,
    INVALID_DEFINITION,
    INFRASTRUCTURE_ERROR,
}

/**
 * Evidence level of a test result.
 *
 * Phase 30 (OB-P30-REQ-006): a pass against a fake transport proves the
 * application logic behaved under that fixture — never that a real
 * device supports the capability.
 */
enum class TestEvidenceLevel {
    /** Logic validated against scripted/fake inputs. */
    SIMULATED,

    /** Validated against documented specifications. */
    DOCUMENTED,

    /** Validated against real hardware (future phases). */
    HARDWARE_OBSERVED,
}

/**
 * One structured test result.
 */
data class TestResult(
    val testId: String,
    val suiteId: String,
    val category: TestResultCategory,
    val requirementRefs: List<String> = emptyList(),
    val fixtureVersion: String? = null,
    val assertionsPassed: Int = 0,
    val assertionsFailed: Int = 0,
    val errorCategory: String? = null,
    val diagnostics: String? = null,
    val durationMillis: Long = 0L,
    val determinism: DeterminismClass = DeterminismClass.DETERMINISTIC,
    val evidenceLevel: TestEvidenceLevel = TestEvidenceLevel.SIMULATED,
    val timestampMillis: Long = 0L,
) {
    init {
        require(testId.isNotBlank()) { "testId must not be blank" }
        // Diagnostics must not carry raw payloads or credentials.
        require(diagnostics == null || diagnostics.length <= 2000) {
            "diagnostics capped at 2000 chars"
        }
    }
}

/**
 * A test report: the results of one suite run.
 */
data class TestReport(
    val suiteId: String,
    val results: List<TestResult>,
) {
    val passed: Int get() = results.count { it.category == TestResultCategory.PASSED }
    val failed: Int get() = results.count { it.category == TestResultCategory.FAILED }
    val skipped: Int get() = results.count { it.category == TestResultCategory.SKIPPED }
    val blocked: Int get() = results.count { it.category == TestResultCategory.BLOCKED }

    /** True when every result is PASSED. Skipped/blocked never count as passes. */
    val allPassed: Boolean get() = results.isNotEmpty() && results.all { it.category == TestResultCategory.PASSED }

    /** Render a human-readable summary. */
    fun summary(): String = buildString {
        appendLine("suite=$suiteId total=${results.size} passed=$passed failed=$failed skipped=$skipped blocked=$blocked")
        for (r in results.filter { it.category != TestResultCategory.PASSED }) {
            appendLine("  ${r.testId}: ${r.category}${r.errorCategory?.let { " [$it]" } ?: ""}")
        }
    }
}
