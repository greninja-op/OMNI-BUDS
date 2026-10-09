package com.omnibuds.core.sdk.api

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState

/**
 * Access tier for declared capability operations.
 */
enum class OperationTier {
    READ_ONLY,
    MUTATING,
}

/**
 * Declared capability metadata provided by a community protocol adapter.
 */
data class CommunityCapabilityDeclaration(
    val featureId: FeatureId,
    val claimedState: CapabilityState,
    val tier: OperationTier,
    val requiredTransport: TransportKind,
    val timeoutMillis: Long = 3000L,
    val dependencies: Set<FeatureId> = emptySet(),
    val conflicts: Set<FeatureId> = emptySet(),
    val notes: String = "",
) {
    init {
        require(timeoutMillis > 0) { "timeoutMillis must be > 0" }
        require(requiredTransport != TransportKind.UNKNOWN) {
            "requiredTransport must not be TransportKind.UNKNOWN"
        }
        // Community declarations cannot self-promote to PERSISTENCE_VERIFIED
        require(claimedState != CapabilityState.PERSISTENCE_VERIFIED) {
            "Community capability declaration cannot claim PERSISTENCE_VERIFIED in metadata"
        }
        if (tier == OperationTier.READ_ONLY) {
            require(claimedState == CapabilityState.READ_ONLY || claimedState == CapabilityState.UNKNOWN || claimedState == CapabilityState.UNSUPPORTED) {
                "READ_ONLY tier requires claimedState to be READ_ONLY, UNKNOWN, or UNSUPPORTED"
            }
        }
    }
}

/**
 * Specification for an individual operation exposed by an adapter.
 */
data class CommunityOperationDefinition(
    val operationId: String,
    val targetFeature: FeatureId,
    val tier: OperationTier,
    val defaultTimeoutMillis: Long = 3000L,
    val maxRetries: Int = 0,
    val description: String = "",
) {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(defaultTimeoutMillis > 0) { "defaultTimeoutMillis must be > 0" }
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
    }
}
