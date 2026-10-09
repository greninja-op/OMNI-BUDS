package com.omnibuds.core.hil

/**
 * Test rig abstraction.
 *
 * Phase 38: interfaces with injectable dependencies. Fake
 * implementations are used for automated tests; no rig touches
 * Android Bluetooth APIs in this phase.
 */
interface HilRig {

    /** Stable rig identifier. */
    val rigId: String

    /** The environment this rig simulates. */
    val environment: HilEnvironment

    /** Initialize the rig. Idempotent. */
    fun initialize(): RigOutcome

    /** Release rig resources. Always attempted, even on failure. */
    fun cleanup(): RigOutcome
}

/** Rig operation outcome. */
sealed interface RigOutcome {
    data object Ready : RigOutcome
    data object CleanedUp : RigOutcome
    data class Failed(val reason: String) : RigOutcome
}

/**
 * A deterministic fake rig for automated tests.
 *
 * Never touches Bluetooth. Records the operations it was asked to
 * perform so tests can assert no physical execution occurred.
 */
class DryRunHilRig(
    override val rigId: String = "fake-rig-1",
    override val environment: HilEnvironment = HilEnvironment.SIMULATED,
    private val failInitialize: Boolean = false,
    private val failCleanup: Boolean = false,
) : HilRig {

    /** Operations this rig was asked to perform, in order. */
    val requestedOperations = mutableListOf<String>()

    override fun initialize(): RigOutcome {
        requestedOperations.add("initialize")
        return if (failInitialize) RigOutcome.Failed("simulated init failure")
        else RigOutcome.Ready
    }

    override fun cleanup(): RigOutcome {
        requestedOperations.add("cleanup")
        return if (failCleanup) RigOutcome.Failed("simulated cleanup failure")
        else RigOutcome.CleanedUp
    }

    /** True when this rig ever performed a physical operation. */
    val performedPhysicalOperation: Boolean
        get() = requestedOperations.any { it.startsWith("physical:") }
}
