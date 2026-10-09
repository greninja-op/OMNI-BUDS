package com.omnibuds.core.access

/**
 * Operation categories.
 *
 * Phase 21 (OB-P21-REQ-005): every operation has an explicit rule.
 * Read categories are observation-only; write categories mutate hardware.
 */
enum class OperationCategory {
    DEVICE_DISCOVERY,
    IDENTITY_OBSERVATION,
    CONNECTION_OBSERVATION,
    METADATA_OBSERVATION,
    PROTOCOL_PARSING,
    CAPABILITY_INSPECTION,
    HARDWARE_STATE_READ,
    HARDWARE_STATE_WRITE,
    CONFIGURATION_RESET,
    FIRMWARE_UPDATE,
    RAW_TRANSPORT_WRITE,
}

/**
 * Structured access decision.
 *
 * Phase 21 (OB-P21-REQ-009): allowed/denied + reason code + classification
 * + identifiers + missing evidence + read-only flag + re-evaluation hint.
 * Never contains sensitive payloads.
 */
data class AccessDecision(
    val allowed: Boolean,
    val reason: DenialReason,
    val classification: DeviceClassification,
    val operation: OperationCategory,
    /** Capability identifier, when the operation targets one. Null otherwise. */
    val capabilityId: String?,
    /** Evidence that would be required to allow this. Empty when allowed. */
    val missingEvidence: List<String>,
    /** True when the operation is read-only by nature. */
    val isReadOnly: Boolean,
    /** True when a later state change may change this decision. */
    val reEvaluateAfterChange: Boolean,
)

/** Typed reason codes for access decisions. */
enum class DenialReason {
    /** No reason to deny — the operation is allowed. */
    ALLOWED,
    UNKNOWN_DEVICE_WRITE_DENIED,
    AMBIGUOUS_IDENTITY_WRITE_DENIED,
    UNSUPPORTED_DEVICE_WRITE_DENIED,
    UNVERIFIED_PROTOCOL_WRITE_DENIED,
    READ_ONLY_CAPABILITY_WRITE_DENIED,
    CAPABILITY_UNKNOWN,
    CAPABILITY_UNSUPPORTED,
    FIRMWARE_INCOMPATIBLE,
    RAW_WRITE_DENIED,
    CONFIGURATION_RESET_DENIED,
    FIRMWARE_UPDATE_DENIED,
    STALE_EVIDENCE,
    CONFLICTING_EVIDENCE,
    PROTOCOL_VERSION_MISMATCH,
    MISSING_WRITE_PERMISSION,
    READ_PERMISSION_INSUFFICIENT_FOR_WRITE,
}

/**
 * Centralized default-deny access policy.
 *
 * Phase 21 (OB-P21-REQ-004/006/007/008/027/028): evaluated at the
 * domain/control boundary. A connection or pairing never authorizes
 * proprietary hardware control.
 */
object DeviceAccessPolicy {

    /**
     * Evaluate whether [operation] is permitted for a device in [state].
     *
     * @param capabilityId the target capability, when relevant.
     * @param capabilityWriteVerified true when the capability has a verified
     * write path (read-back or persistence verification).
     * @param firmwareCompatible true when firmware is known compatible.
     * @param evidenceFresh true when the underlying evidence is current.
     */
    fun evaluate(
        state: DeviceAccessState,
        operation: OperationCategory,
        capabilityId: String? = null,
        capabilityWriteVerified: Boolean = false,
        firmwareCompatible: Boolean = true,
        evidenceFresh: Boolean = true,
    ): AccessDecision {
        val isReadOnly = operation.isReadOnly()

        // Observation categories: allowed when evidence is fresh.
        // These are legitimate public interfaces, not vendor control.
        if (isReadOnly) {
            if (!evidenceFresh) {
                return deny(state, operation, capabilityId, DenialReason.STALE_EVIDENCE, true)
            }
            return AccessDecision(
                allowed = true,
                reason = DenialReason.ALLOWED,
                classification = state.classification,
                operation = operation,
                capabilityId = capabilityId,
                missingEvidence = emptyList(),
                isReadOnly = true,
                reEvaluateAfterChange = false,
            )
        }

        // Write categories: default-deny, with typed reasons.
        return evaluateWrite(state, operation, capabilityId, capabilityWriteVerified, firmwareCompatible, evidenceFresh)
    }

    private fun evaluateWrite(
        state: DeviceAccessState,
        operation: OperationCategory,
        capabilityId: String?,
        capabilityWriteVerified: Boolean,
        firmwareCompatible: Boolean,
        evidenceFresh: Boolean,
    ): AccessDecision {
        // Hard denials that no evidence can soften at this layer.
        if (operation == OperationCategory.RAW_TRANSPORT_WRITE) {
            return deny(state, operation, capabilityId, DenialReason.RAW_WRITE_DENIED, true)
        }
        if (operation == OperationCategory.FIRMWARE_UPDATE) {
            return deny(state, operation, capabilityId, DenialReason.FIRMWARE_UPDATE_DENIED, true)
        }
        if (operation == OperationCategory.CONFIGURATION_RESET) {
            return deny(state, operation, capabilityId, DenialReason.CONFIGURATION_RESET_DENIED, true)
        }

        if (!evidenceFresh) {
            return deny(state, operation, capabilityId, DenialReason.STALE_EVIDENCE, true)
        }
        if (!firmwareCompatible) {
            return deny(state, operation, capabilityId, DenialReason.FIRMWARE_INCOMPATIBLE, true)
        }

        val reason = when (state.classification) {
            DeviceClassification.UNKNOWN_DEVICE,
            DeviceClassification.PARTIALLY_IDENTIFIED ->
                DenialReason.UNKNOWN_DEVICE_WRITE_DENIED
            DeviceClassification.AMBIGUOUS_IDENTITY ->
                DenialReason.AMBIGUOUS_IDENTITY_WRITE_DENIED
            DeviceClassification.IDENTIFIED_UNSUPPORTED ->
                DenialReason.UNSUPPORTED_DEVICE_WRITE_DENIED
            DeviceClassification.KNOWN_PROTOCOL_UNVERIFIED ->
                DenialReason.UNVERIFIED_PROTOCOL_WRITE_DENIED
            DeviceClassification.KNOWN_PROTOCOL_SUPPORTED,
            DeviceClassification.KNOWN_DEVICE_SUPPORTED -> null
        }

        if (reason != null) {
            val missing = buildList {
                when (reason) {
                    DenialReason.UNKNOWN_DEVICE_WRITE_DENIED ->
                        add("device identity evidence")
                    DenialReason.AMBIGUOUS_IDENTITY_WRITE_DENIED ->
                        add("unambiguous device identity")
                    DenialReason.UNSUPPORTED_DEVICE_WRITE_DENIED ->
                        add("compatible protocol implementation")
                    DenialReason.UNVERIFIED_PROTOCOL_WRITE_DENIED ->
                        add("protocol validation to VERIFIED level")
                    else -> Unit
                }
            }
            return AccessDecision(
                allowed = false,
                reason = reason,
                classification = state.classification,
                operation = operation,
                capabilityId = capabilityId,
                missingEvidence = missing,
                isReadOnly = false,
                reEvaluateAfterChange = true,
            )
        }

        // Supported device: capability-level write verification still required.
        if (!capabilityWriteVerified) {
            return AccessDecision(
                allowed = false,
                reason = DenialReason.MISSING_WRITE_PERMISSION,
                classification = state.classification,
                operation = operation,
                capabilityId = capabilityId,
                missingEvidence = listOf(
                    "verified write path for capability ${capabilityId ?: operation.name}",
                ),
                isReadOnly = false,
                reEvaluateAfterChange = true,
            )
        }

        return AccessDecision(
            allowed = true,
            reason = DenialReason.ALLOWED,
            classification = state.classification,
            operation = operation,
            capabilityId = capabilityId,
            missingEvidence = emptyList(),
            isReadOnly = false,
            reEvaluateAfterChange = false,
        )
    }

    private fun deny(
        state: DeviceAccessState,
        operation: OperationCategory,
        capabilityId: String?,
        reason: DenialReason,
        reEvaluate: Boolean,
    ): AccessDecision = AccessDecision(
        allowed = false,
        reason = reason,
        classification = state.classification,
        operation = operation,
        capabilityId = capabilityId,
        missingEvidence = emptyList(),
        isReadOnly = operation.isReadOnly(),
        reEvaluateAfterChange = reEvaluate,
    )

    private fun OperationCategory.isReadOnly(): Boolean = when (this) {
        OperationCategory.DEVICE_DISCOVERY,
        OperationCategory.IDENTITY_OBSERVATION,
        OperationCategory.CONNECTION_OBSERVATION,
        OperationCategory.METADATA_OBSERVATION,
        OperationCategory.PROTOCOL_PARSING,
        OperationCategory.CAPABILITY_INSPECTION,
        OperationCategory.HARDWARE_STATE_READ -> true
        OperationCategory.HARDWARE_STATE_WRITE,
        OperationCategory.CONFIGURATION_RESET,
        OperationCategory.FIRMWARE_UPDATE,
        OperationCategory.RAW_TRANSPORT_WRITE -> false
    }
}
