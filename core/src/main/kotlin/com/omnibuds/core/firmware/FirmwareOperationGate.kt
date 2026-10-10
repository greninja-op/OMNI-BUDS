package com.omnibuds.core.firmware

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState

/**
 * Result of an operation authorization check with firmware validation.
 */
sealed interface FirmwareAuthorizationDecision {
    data class Authorized(
        val message: String = "operation authorized under firmware policy",
    ) : FirmwareAuthorizationDecision

    data class Denied(
        val reasonCode: String,
        val machineReason: String,
        val limitations: List<String> = emptyList(),
    ) : FirmwareAuthorizationDecision
}

/**
 * Operation authorization gate ensuring operations respect firmware compatibility rules.
 */
object FirmwareOperationGate {

    /**
     * Assesses whether an operation is permitted under current firmware compatibility standing.
     *
     * Invariants:
     * 1. Mutating operations are strictly forbidden on INCOMPATIBLE, INVALID, or STALE firmware.
     * 2. Mutating operations are forbidden when firmware is UNKNOWN unless operation is explicitly
     *    unconditional and firmware-agnostic.
     * 3. Read-only operations are permitted if capability state is READ_ONLY or higher.
     */
    fun authorizeOperation(
        isMutating: Boolean,
        featureId: FeatureId,
        compatibilityResult: FirmwareCompatibilityResult,
        capabilityState: CapabilityState,
        operationFirmwareConstraint: FirmwareConstraint? = null,
        observedVersion: FirmwareVersion = FirmwareVersion.Unknown,
    ): FirmwareAuthorizationDecision {
        // Evaluate operation-specific constraint if specified
        if (operationFirmwareConstraint != null) {
            if (observedVersion is FirmwareVersion.Unknown) {
                return FirmwareAuthorizationDecision.Denied(
                    reasonCode = "DENY_FIRMWARE_UNKNOWN",
                    machineReason = "operation for ${featureId.qualifiedName} requires verified firmware but firmware is unknown",
                )
            }
            if (!operationFirmwareConstraint.isSatisfiedBy(observedVersion)) {
                return FirmwareAuthorizationDecision.Denied(
                    reasonCode = "DENY_FIRMWARE_CONSTRAINT_UNSATISFIED",
                    machineReason = "observed firmware '${observedVersion.rawValue}' does not satisfy constraint $operationFirmwareConstraint for ${featureId.qualifiedName}",
                )
            }
        }

        // Evaluate capability state
        if (capabilityState == CapabilityState.UNSUPPORTED) {
            return FirmwareAuthorizationDecision.Denied(
                reasonCode = "DENY_CAPABILITY_UNSUPPORTED",
                machineReason = "capability ${featureId.qualifiedName} is unsupported on this device and firmware",
            )
        }
        if (isMutating && capabilityState == CapabilityState.READ_ONLY) {
            return FirmwareAuthorizationDecision.Denied(
                reasonCode = "DENY_CAPABILITY_READ_ONLY",
                machineReason = "capability ${featureId.qualifiedName} is read-only on this device and firmware; mutating operation denied",
            )
        }
        if (isMutating && capabilityState == CapabilityState.UNKNOWN) {
            return FirmwareAuthorizationDecision.Denied(
                reasonCode = "DENY_CAPABILITY_UNKNOWN",
                machineReason = "capability ${featureId.qualifiedName} is not established; mutating operation denied",
            )
        }

        // Evaluate compatibility result standing
        if (isMutating) {
            return if (compatibilityResult.canAuthorizeMutatingOperations) {
                FirmwareAuthorizationDecision.Authorized()
            } else {
                FirmwareAuthorizationDecision.Denied(
                    reasonCode = compatibilityResult.reasonCode,
                    machineReason = "mutating operation denied: ${compatibilityResult.message}",
                    limitations = compatibilityResult.limitations,
                )
            }
        } else {
            return if (compatibilityResult.canAuthorizeReadOnlyOperations) {
                FirmwareAuthorizationDecision.Authorized()
            } else {
                FirmwareAuthorizationDecision.Denied(
                    reasonCode = compatibilityResult.reasonCode,
                    machineReason = "read-only operation denied: ${compatibilityResult.message}",
                    limitations = compatibilityResult.limitations,
                )
            }
        }
    }
}
