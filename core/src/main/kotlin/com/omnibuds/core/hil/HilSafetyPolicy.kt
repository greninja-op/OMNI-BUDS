package com.omnibuds.core.hil

/**
 * Operation categories a campaign may request.
 */
enum class OperationCategory {
    READ_ONLY_OBSERVATION,
    CONNECTION_MANAGEMENT,
    HARDWARE_WRITE,
    FIRMWARE_UPDATE,
    FACTORY_RESET,
}

/**
 * Centralized HIL safety policy, enforced in executable code.
 *
 * Phase 38: defaults deny everything physical. Physical execution
 * and hardware writes are disabled; they can only be enabled by an
 * explicit operator-constructed policy, which this phase never
 * builds for physical use.
 */
data class HilSafetyPolicy(
    /** Whether any physical execution is allowed. Default false. */
    val physicalExecutionAllowed: Boolean = false,

    /** Whether hardware writes are allowed. Default false. */
    val hardwareWritesAllowed: Boolean = false,

    /** Operation categories allowlisted for execution. */
    val allowlistedOperations: Set<OperationCategory> =
        setOf(OperationCategory.READ_ONLY_OBSERVATION),
) {
    init {
        // Invariant: hardware writes require physical execution.
        require(!hardwareWritesAllowed || physicalExecutionAllowed) {
            "hardware writes require physical execution"
        }
        // Invariant: destructive operations are never routine.
        require(
            OperationCategory.FIRMWARE_UPDATE !in allowlistedOperations &&
                OperationCategory.FACTORY_RESET !in allowlistedOperations,
        ) {
            "firmware update and factory reset are never routine test actions"
        }
    }

    /** The default safe policy. */
    companion object {
        val DEFAULT_SAFE = HilSafetyPolicy()
    }
}

/** Safety decision for one proposed action. */
sealed interface SafetyDecision {
    data object Allowed : SafetyDecision
    data class Denied(val reason: String) : SafetyDecision
}

/**
 * Evaluates proposed actions against a safety policy.
 */
class HilSafetyGate(private val policy: HilSafetyPolicy) {

    /**
     * Decide whether an action may execute in [environment].
     */
    fun decide(
        environment: HilEnvironment,
        category: OperationCategory,
    ): SafetyDecision {
        if (environment.involvesPhysicalHardware() && !policy.physicalExecutionAllowed) {
            return SafetyDecision.Denied(
                "physical execution is disabled by the safety policy",
            )
        }
        if (category == OperationCategory.HARDWARE_WRITE && !policy.hardwareWritesAllowed) {
            return SafetyDecision.Denied(
                "hardware writes are disabled by the safety policy",
            )
        }
        if (category !in policy.allowlistedOperations) {
            return SafetyDecision.Denied(
                "operation category $category is not allowlisted",
            )
        }
        return SafetyDecision.Allowed
    }

    /**
     * Whether a mutating operation has all required authorizations.
     * All seven must hold; anything missing denies.
     */
    fun authorizeMutatingOperation(
        supportedDevice: Boolean,
        protocolVerified: Boolean,
        operatorConsent: Boolean,
        rollbackAvailable: Boolean,
        timeoutBounded: Boolean,
        readBackPlanned: Boolean,
        auditable: Boolean,
    ): SafetyDecision {
        val missing = buildList {
            if (!supportedDevice) add("supported device")
            if (!protocolVerified) add("verified protocol compatibility")
            if (!operatorConsent) add("explicit operator consent")
            if (!rollbackAvailable) add("rollback or safe recovery strategy")
            if (!timeoutBounded) add("bounded timeout")
            if (!readBackPlanned) add("read-back or reconciliation strategy")
            if (!auditable) add("auditable evidence record")
        }
        return if (missing.isEmpty()) SafetyDecision.Allowed
        else SafetyDecision.Denied("missing: ${missing.joinToString(", ")}")
    }
}
