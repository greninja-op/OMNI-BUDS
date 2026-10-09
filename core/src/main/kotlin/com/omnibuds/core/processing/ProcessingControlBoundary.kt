package com.omnibuds.core.processing

import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId

/**
 * Guards for feature-control requests across processing domains.
 *
 * Phase 15 (OB-P15-REQ-012): a hardware request cannot silently use an
 * Android backend; an Android request cannot silently become a hardware
 * command; unknown domains are rejected, never substituted.
 */
object ProcessingControlBoundary {

    /**
     * Validate that a control request targets the correct backend.
     *
     * @param feature the feature to control.
     * @param capability the resolved capability record.
     * @param requestedDomain the domain the caller intends to control.
     * @param resolution the domain resolution for the capability.
     */
    fun checkRequest(
        feature: FeatureId,
        capability: FeatureCapability,
        requestedDomain: AudioProcessingDomain,
        resolution: DomainResolution,
    ): ControlEligibility {
        // Delegate to the resolver's eligibility check — the boundary is the
        // policy, the resolver is the mechanism.
        return ProcessingDomainResolver.canControl(capability, requestedDomain, resolution)
    }
}
