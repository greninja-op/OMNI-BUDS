package com.omnibuds.core.firmware

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel

/**
 * Definition of a capability that is conditional upon the device running
 * a specific firmware revision or satisfying firmware constraints.
 */
data class FirmwareDependentCapability(
    /** The feature identifier governed by this constraint. */
    val featureId: FeatureId,

    /** Model scope where this rule applies. Empty means applicable across the vendor family. */
    val modelScope: Set<String> = emptySet(),

    /** Required firmware constraint for this capability to be available. */
    val firmwareConstraint: FirmwareConstraint,

    /** Minimum protocol version required, if applicable. */
    val requiredProtocolVersion: String? = null,

    /** Transport required for this capability. */
    val requiredTransport: TransportKind? = null,

    /**
     * Capability state when firmware satisfies the constraint.
     * Cannot claim PERSISTENCE_VERIFIED statically without dynamic persistence proof.
     */
    val availableState: CapabilityState = CapabilityState.SUPPORTED_VOLATILE,

    /** State when firmware does NOT satisfy the constraint (typically UNSUPPORTED or READ_ONLY). */
    val fallbackState: CapabilityState = CapabilityState.UNSUPPORTED,

    /** Evidence reference backing this capability claim. */
    val evidenceReference: String,

    /** Verification level of the capability evidence. */
    val verificationLevel: VerificationLevel = VerificationLevel.LAB_TESTED,

    /** Documented limitations. */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(availableState != CapabilityState.PERSISTENCE_VERIFIED) {
            "PERSISTENCE_VERIFIED cannot be claimed statically by firmware metadata"
        }
    }

    /**
     * Evaluates the capability state for an observed firmware version.
     */
    fun evaluate(
        modelId: String?,
        firmware: FirmwareVersion,
    ): CapabilityState {
        if (modelScope.isNotEmpty() && modelId != null && modelId !in modelScope) {
            return CapabilityState.UNKNOWN
        }
        if (firmware is FirmwareVersion.Unknown) {
            return CapabilityState.UNKNOWN
        }
        return if (firmwareConstraint.isSatisfiedBy(firmware)) {
            availableState
        } else {
            fallbackState
        }
    }
}
