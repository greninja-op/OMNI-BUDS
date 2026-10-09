package com.omnibuds.core.testkit

/**
 * Determinism classification of a test case.
 *
 * Phase 30 (OB-P30-REQ-008): deterministic tests must give identical
 * results on repeat runs with no wall-clock dependencies.
 */
enum class DeterminismClass {
    /** Fully deterministic: same inputs, same outputs, always. */
    DETERMINISTIC,

    /** Timing-sensitive but bounded; documents its assumptions. */
    TIME_BOUNDED,

    /** Requires platform/hardware; never in the ordinary suite. */
    PLATFORM_DEPENDENT,
}

/**
 * Test categories.
 */
enum class TestCategory {
    UNIT,
    COMPONENT,
    INTEGRATION,
    PLATFORM,
    NEGATIVE,
    CONCURRENCY,
    LIFECYCLE,
    PERSISTENCE,
}

/**
 * A structured test-case descriptor.
 *
 * Phase 30 (OB-P30-REQ-001): the contract future suites (Phases 31–33)
 * build on. Expected outcomes must verify behavior, never pass on
 * "no exception".
 */
data class TestCase(
    /** Stable identifier, e.g. `transport.scripted-timeout`. */
    val id: String,
    val name: String,
    val category: TestCategory,
    /** Requirement IDs this case validates, e.g. `OB-P30-REQ-003`. */
    val requirementRefs: List<String> = emptyList(),
    val preconditions: List<String> = emptyList(),
    /** Fixture IDs this case consumes. */
    val fixtures: List<String> = emptyList(),
    val timeoutMillis: Long = 10_000L,
    val tags: Set<String> = emptySet(),
    val determinism: DeterminismClass = DeterminismClass.DETERMINISTIC,
) {
    init {
        require(id.isNotBlank()) { "test id must not be blank" }
        require(name.isNotBlank()) { "test name must not be blank" }
        require(timeoutMillis > 0) { "timeout must be positive" }
    }
}
