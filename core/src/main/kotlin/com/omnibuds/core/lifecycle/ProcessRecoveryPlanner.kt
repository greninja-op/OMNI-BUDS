package com.omnibuds.core.lifecycle

/**
 * Process-recovery decisions.
 *
 * Phase 28 (OB-P28-REQ-004/005/025): on process start, sessions that
 * cannot be proven valid are invalidated. Persisted CONNECTED is never
 * proof of a live connection. Interrupted operations are marked, never
 * replayed.
 */
class ProcessRecoveryPlanner {

    /**
     * Plan recovery for a device on process start.
     */
    fun plan(input: RecoveryInput): RecoveryPlan {
        val actions = mutableListOf<RecoveryAction>()

        // Sessions: invalidate unless proven valid.
        if (input.hasLiveSessionProof) {
            actions.add(RecoveryAction.KeepSession(input.deviceId))
        } else {
            actions.add(RecoveryAction.InvalidateSession(input.deviceId))
        }

        // Observations: mark by age and provenance.
        when (input.lastObservationAge) {
            ObservationAge.FRESH -> {
                // Keep; provenance preserved.
            }
            ObservationAge.STALE, ObservationAge.EXPIRED, ObservationAge.UNKNOWN -> {
                actions.add(RecoveryAction.MarkStale(input.deviceId))
            }
        }

        // Interrupted operations: mark, never replay.
        input.interruptedOperations.forEach { featureId ->
            actions.add(RecoveryAction.MarkInterrupted(input.deviceId, featureId))
        }

        // Capability rediscovery: only when required.
        if (input.capabilityRediscoveryRequired) {
            actions.add(RecoveryAction.RediscoverCapabilities(input.deviceId))
        }

        return RecoveryPlan(input.deviceId, actions)
    }
}

/**
 * Input for recovery planning.
 */
data class RecoveryInput(
    val deviceId: String,
    /** Proof of a live session (e.g. platform reports still connected). */
    val hasLiveSessionProof: Boolean,
    val lastObservationAge: ObservationAge,
    /** Feature ids with operations pending at process death. */
    val interruptedOperations: List<String>,
    /** True when capability state cannot be trusted. */
    val capabilityRediscoveryRequired: Boolean,
)

/**
 * Age of the last observation.
 */
enum class ObservationAge {
    FRESH,
    STALE,
    EXPIRED,
    UNKNOWN,
}

/**
 * Recovery actions (ordered).
 */
sealed interface RecoveryAction {
    data class InvalidateSession(val deviceId: String) : RecoveryAction
    data class KeepSession(val deviceId: String) : RecoveryAction
    data class MarkStale(val deviceId: String) : RecoveryAction
    data class MarkInterrupted(val deviceId: String, val featureId: String) : RecoveryAction
    data class RediscoverCapabilities(val deviceId: String) : RecoveryAction
}

/**
 * The recovery plan for a device.
 */
data class RecoveryPlan(
    val deviceId: String,
    val actions: List<RecoveryAction>,
)
