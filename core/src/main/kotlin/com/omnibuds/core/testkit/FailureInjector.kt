package com.omnibuds.core.testkit

/**
 * Test-only failure injection.
 *
 * Phase 30 (OB-P30-REQ-004/018): scoped to the current test, seeded
 * randomness only, no production backdoors. The injector is passed
 * explicitly to test doubles — nothing in production code references it.
 */
class FailureInjector(
    /** Seed for deterministic pseudo-random failures. */
    val seed: Long = 0L,
) {
    private var random = kotlin.random.Random(seed)
    private val injected = mutableListOf<String>()

    /** Failures injected so far (for assertions). */
    val injectedFailures: List<String> get() = injected.toList()

    /**
     * Decide whether to inject a failure with the given probability.
     * Deterministic for a fixed seed and call sequence.
     */
    fun shouldFail(probability: Double, label: String): Boolean {
        require(probability in 0.0..1.0) { "probability must be in [0,1]" }
        val fail = random.nextDouble() < probability
        if (fail) injected.add(label)
        return fail
    }

    /** Reset the injector (test cleanup). */
    fun reset() {
        random = kotlin.random.Random(seed)
        injected.clear()
    }
}
