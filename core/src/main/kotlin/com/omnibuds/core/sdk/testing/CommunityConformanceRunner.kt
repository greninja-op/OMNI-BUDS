package com.omnibuds.core.sdk.testing

import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.sdk.api.CommunityProtocolAdapter
import com.omnibuds.core.sdk.api.SdkOperationResult
import com.omnibuds.core.vendor.MatchResult

/**
 * Conformance test harness for verifying community protocol adapters.
 */
object CommunityConformanceRunner {

    data class ConformanceSummary(
        val adapterId: String,
        val passed: Boolean,
        val errors: List<String>,
    )

    fun verifyAdapter(
        adapter: CommunityProtocolAdapter,
        sampleFingerprint: DeviceFingerprint,
    ): ConformanceSummary {
        val errors = mutableListOf<String>()

        // 1. Verify basic contract properties
        if (adapter.adapterId.isBlank()) {
            errors.add("adapterId must not be blank")
        }
        if (adapter.displayName.isBlank()) {
            errors.add("displayName must not be blank")
        }
        if (adapter.integrationVersion.isBlank()) {
            errors.add("integrationVersion must not be blank")
        }

        // 2. Verify match behavior does not throw
        try {
            val match = adapter.match(sampleFingerprint)
            if (match is MatchResult.Matched && match.adapterId != adapter.adapterId) {
                errors.add("MatchResult.Matched returned mismatched adapterId: ${match.adapterId} vs ${adapter.adapterId}")
            }
        } catch (t: Throwable) {
            errors.add("adapter.match threw exception: ${t.message}")
        }

        // 3. Verify declared operations have valid feature mappings
        val declaredFeatureIds = adapter.declaredCapabilities.map { it.featureId }.toSet()
        for (op in adapter.declaredOperations) {
            if (op.targetFeature !in declaredFeatureIds) {
                errors.add("Operation ${op.operationId} targets undeclared feature ${op.targetFeature.qualifiedName}")
            }
        }

        // 4. Verify robust handling of malformed payloads
        val malformedResult = adapter.parseInboundPayload("unknown.op", byteArrayOf())
        if (malformedResult !is SdkOperationResult.Unsupported && malformedResult !is SdkOperationResult.MalformedData) {
            errors.add("parseInboundPayload on unknown.op must return Unsupported or MalformedData, got: $malformedResult")
        }

        return ConformanceSummary(
            adapterId = adapter.adapterId,
            passed = errors.isEmpty(),
            errors = errors,
        )
    }
}
