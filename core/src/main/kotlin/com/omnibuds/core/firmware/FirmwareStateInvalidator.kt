package com.omnibuds.core.firmware

import com.omnibuds.core.state.CapabilityState

/**
 * Diagnostic event logged when a device's firmware observation shifts or invalidates.
 */
data class FirmwareChangeEvent(
    val deviceId: String,
    val previousVersion: FirmwareVersion,
    val newVersion: FirmwareVersion,
    val timestampMs: Long,
    val reason: String,
    val affectedCapabilitiesCount: Int,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(timestampMs >= 0) { "timestampMs must be non-negative" }
    }
}

/**
 * Manages the invalidation and lifecycle reconciliation of capability states
 * and pending operations when a device firmware changes.
 */
class FirmwareStateInvalidator(
    private val dependentCapabilities: List<FirmwareDependentCapability> = emptyList(),
) {
    /**
     * Evaluates whether two firmware versions represent a meaningful change.
     */
    fun hasFirmwareChanged(
        previous: FirmwareVersion?,
        current: FirmwareVersion,
    ): Boolean {
        if (previous == null) return false
        if (previous is FirmwareVersion.Unknown && current is FirmwareVersion.Unknown) return false
        return previous != current && !previous.rawValue.equals(current.rawValue, ignoreCase = true)
    }

    /**
     * Computes the updated capability states following a firmware transition.
     * Stale capability states are explicitly revoked or recalculated.
     */
    fun reconcileCapabilitiesOnFirmwareChange(
        modelId: String?,
        previousVersion: FirmwareVersion?,
        newVersion: FirmwareVersion,
        currentCapabilities: Map<String, CapabilityState>,
    ): Map<String, CapabilityState> {
        val updated = currentCapabilities.toMutableMap()

        for (dep in dependentCapabilities) {
            val featureKey = dep.featureId.qualifiedName
            if (dep.modelScope.isNotEmpty() && modelId != null && modelId !in dep.modelScope) {
                continue
            }

            val newState = dep.evaluate(modelId, newVersion)
            // If new firmware does not satisfy requirement, downgrade or mark unsupported
            if (newVersion is FirmwareVersion.Unknown) {
                // Unknown firmware: cannot assume prior capabilities still hold
                updated[featureKey] = CapabilityState.UNKNOWN
            } else {
                updated[featureKey] = newState
            }
        }

        return updated
    }

    /**
     * Determines whether an in-flight operation with a required firmware constraint
     * must be cancelled due to a firmware change.
     */
    fun shouldCancelPendingOperation(
        operationRequiredConstraint: FirmwareConstraint?,
        newVersion: FirmwareVersion,
    ): Boolean {
        if (operationRequiredConstraint == null) return false
        if (newVersion is FirmwareVersion.Unknown) return true
        return !operationRequiredConstraint.isSatisfiedBy(newVersion)
    }
}
