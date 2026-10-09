package com.omnibuds.core.sdk.api

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.protocol.ProtocolDefinition
import com.omnibuds.core.vendor.MatchResult
import com.omnibuds.core.vendor.VendorAdapter

/**
 * Public contract for community protocol integrations.
 *
 * Implements [VendorAdapter] so it seamlessly registers with the host application's
 * vendor registry while enforcing SDK constraints, typed capability declarations,
 * and operation dispatch.
 */
interface CommunityProtocolAdapter : VendorAdapter {

    /** The SDK version this adapter was compiled against. */
    val sdkVersion: SdkVersion

    /** Stable integration version, e.g. "1.2.0". */
    val integrationVersion: String

    /** Provenance and evidence record. */
    val evidenceRecord: CommunityEvidenceRecord

    /** Declared capabilities supported by this adapter. */
    val declaredCapabilities: List<CommunityCapabilityDeclaration>

    /** Declared operations supported by this adapter. */
    val declaredOperations: List<CommunityOperationDefinition>

    /**
     * Parse an inbound raw protocol packet into a structured reading or observation.
     */
    fun parseInboundPayload(
        operationId: String,
        payload: ByteArray,
    ): SdkOperationResult<Map<String, String>>

    /**
     * Encode a structured outbound command request into protocol wire format.
     */
    fun encodeOutboundCommand(
        operationId: String,
        parameters: Map<String, String>,
    ): SdkOperationResult<ByteArray>
}
