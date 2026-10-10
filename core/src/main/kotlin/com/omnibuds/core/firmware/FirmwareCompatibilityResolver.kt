package com.omnibuds.core.firmware

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.protocol.version.CompatibilityResolution
import com.omnibuds.core.protocol.version.CompatibilityResolver
import com.omnibuds.core.protocol.version.CompatibilityStatus
import com.omnibuds.core.protocol.version.ProtocolIdentity
import com.omnibuds.core.protocol.version.ProtocolVersion

/**
 * Firmware-aware compatibility assessment outcome.
 */
enum class FirmwareCompatibilityStatus {
    /** Firmware is explicitly verified and satisfies all compatibility requirements. */
    COMPATIBLE,

    /** Firmware is compatible, but operations are restricted (e.g. read-only fallback). */
    COMPATIBLE_WITH_LIMITATIONS,

    /** Firmware is known to be incompatible, blocked, or outside supported ranges. */
    INCOMPATIBLE,

    /** Firmware version has not been read, is null, or is unobserved. */
    UNKNOWN_FIRMWARE,

    /** Firmware observation is malformed or invalid. */
    INVALID_FIRMWARE,

    /** Firmware observation has expired beyond the allowable freshness window. */
    STALE_FIRMWARE_OBSERVATION,

    /** Conflicting compatibility rules matched without deterministic disambiguation. */
    AMBIGUOUS,

    /** Insufficient identity evidence exists to evaluate firmware rules. */
    INSUFFICIENT_EVIDENCE,

    /** Operation or firmware access blocked by centralized policy. */
    BLOCKED_BY_POLICY,
}

/**
 * Detailed result of firmware compatibility evaluation.
 */
data class FirmwareCompatibilityResult(
    val status: FirmwareCompatibilityStatus,
    val matchedRule: FirmwareCompatibilityRule?,
    val protocolResolution: CompatibilityResolution?,
    val reasonCode: String,
    val message: String,
    val limitations: List<String> = emptyList(),
    val evidenceReference: String? = null,
) {
    /** Mutating operations are only allowed if strictly COMPATIBLE. */
    val canAuthorizeMutatingOperations: Boolean
        get() = status == FirmwareCompatibilityStatus.COMPATIBLE &&
                (protocolResolution?.canAuthorizeMutatingOperations ?: true)

    /** Safe read-only operations may be allowed under limitations if explicitly safe. */
    val canAuthorizeReadOnlyOperations: Boolean
        get() = (status == FirmwareCompatibilityStatus.COMPATIBLE ||
                status == FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS) &&
                (protocolResolution?.canAuthorizeReadOnlyOperations ?: true)
}

/**
 * Firmware Compatibility Resolver.
 *
 * Integrates firmware identity, observation provenance, freshness windows,
 * explicit compatibility rules, and protocol resolution.
 */
class FirmwareCompatibilityResolver(
    private val rules: List<FirmwareCompatibilityRule> = emptyList(),
    private val underlyingProtocolResolver: CompatibilityResolver = CompatibilityResolver(),
    private val maxFreshnessMs: Long = FirmwareObservation.DEFAULT_FRESHNESS_WINDOW_MS,
) {
    init {
        // Guard against duplicate rule IDs
        val duplicateIds = rules.groupBy { it.ruleId }.filter { it.value.size > 1 }.keys
        require(duplicateIds.isEmpty()) { "duplicate firmware compatibility rule IDs: $duplicateIds" }
    }

    /**
     * Resolves firmware compatibility deterministically.
     */
    fun resolve(
        candidates: List<ProtocolIdentity>,
        fingerprint: DeviceFingerprint,
        identification: IdentificationResult,
        observedVersion: ProtocolVersion,
        observation: FirmwareObservation,
        availableTransports: Set<TransportKind>,
        deviceModelId: String? = null,
        targetScope: String? = null,
        currentTimeMs: Long = observation.observedAtMs,
    ): FirmwareCompatibilityResult {
        // 1. Identification gate
        if (identification is IdentificationResult.InsufficientEvidence) {
            return FirmwareCompatibilityResult(
                status = FirmwareCompatibilityStatus.INSUFFICIENT_EVIDENCE,
                matchedRule = null,
                protocolResolution = CompatibilityResolution.insufficientEvidence(),
                reasonCode = "ERR_INSUFFICIENT_EVIDENCE",
                message = "device identity evidence is insufficient to evaluate firmware compatibility",
            )
        }

        // 2. Validation of observation integrity
        if (observation.validationState == FirmwareValidationState.MALFORMED) {
            return FirmwareCompatibilityResult(
                status = FirmwareCompatibilityStatus.INVALID_FIRMWARE,
                matchedRule = null,
                protocolResolution = null,
                reasonCode = "ERR_MALFORMED_FIRMWARE",
                message = "observed firmware string is malformed or invalid",
                limitations = observation.limitations,
            )
        }
        if (observation.validationState == FirmwareValidationState.REJECTED_BY_POLICY) {
            return FirmwareCompatibilityResult(
                status = FirmwareCompatibilityStatus.BLOCKED_BY_POLICY,
                matchedRule = null,
                protocolResolution = null,
                reasonCode = "ERR_FIRMWARE_REJECTED",
                message = "firmware observation rejected by security policy",
                limitations = observation.limitations,
            )
        }

        // 3. Freshness evaluation
        if (!observation.isFresh(currentTimeMs, maxFreshnessMs)) {
            return FirmwareCompatibilityResult(
                status = FirmwareCompatibilityStatus.STALE_FIRMWARE_OBSERVATION,
                matchedRule = null,
                protocolResolution = null,
                reasonCode = "ERR_STALE_FIRMWARE",
                message = "firmware observation is stale (age ${currentTimeMs - observation.observedAtMs}ms exceeds max ${maxFreshnessMs}ms)",
            )
        }

        // 4. Unknown firmware check
        if (observation.version is FirmwareVersion.Unknown || observation.rawValue == null) {
            // Evaluate underlying protocol resolver with null observed firmware
            val protoRes = underlyingProtocolResolver.resolve(
                candidates = candidates,
                fingerprint = fingerprint,
                identification = identification,
                observedVersion = observedVersion,
                observedFirmware = null,
                availableTransports = availableTransports,
                deviceModelId = deviceModelId,
            )

            // If a candidate is firmware-agnostic (verifiedFirmware == null) and permits unknown, allow with limitations
            val agnosticCandidate = candidates.firstOrNull { it.verifiedFirmware == null }
            return if (agnosticCandidate != null && protoRes.status == CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS) {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
                    matchedRule = null,
                    protocolResolution = protoRes,
                    reasonCode = "WARN_FIRMWARE_UNKNOWN_AGNOSTIC",
                    message = "device firmware is unobserved, but protocol '${agnosticCandidate.protocolId}' is firmware-agnostic; running in restricted mode",
                    limitations = listOf("firmware unobserved; mutating commands restricted"),
                )
            } else {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.UNKNOWN_FIRMWARE,
                    matchedRule = null,
                    protocolResolution = protoRes,
                    reasonCode = "ERR_UNKNOWN_FIRMWARE",
                    message = "device firmware version has not been read; operations requiring firmware verification are blocked",
                )
            }
        }

        // 5. Explicit rule matching
        val matchingRules = rules.filter { rule ->
            rule.matches(deviceModelId, observation.version, targetScope)
        }

        // Evaluate rule conflicts
        if (matchingRules.size > 1) {
            // Check if there is an exact model match vs family-wide match
            val modelSpecific = matchingRules.filter { deviceModelId != null && deviceModelId in it.applicableModels }
            val chosenRules = if (modelSpecific.isNotEmpty()) modelSpecific else matchingRules

            // Check if outcomes differ
            val distinctOutcomes = chosenRules.map { it.outcome }.distinct()
            if (distinctOutcomes.size > 1) {
                // Rule conflict! If any rule says INCOMPATIBLE, fail safe to INCOMPATIBLE
                val denyRule = chosenRules.firstOrNull { it.outcome == FirmwareRuleOutcome.INCOMPATIBLE }
                if (denyRule != null) {
                    return FirmwareCompatibilityResult(
                        status = FirmwareCompatibilityStatus.INCOMPATIBLE,
                        matchedRule = denyRule,
                        protocolResolution = null,
                        reasonCode = "ERR_RULE_DENY",
                        message = "firmware rule '${denyRule.ruleId}' explicitly marks firmware '${observation.version.rawValue}' as incompatible: ${denyRule.rationale}",
                        evidenceReference = denyRule.evidenceReference,
                    )
                }

                return FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.AMBIGUOUS,
                    matchedRule = null,
                    protocolResolution = null,
                    reasonCode = "ERR_CONFLICTING_RULES",
                    message = "conflicting firmware compatibility rules matched for '${observation.version.rawValue}': ${chosenRules.map { it.ruleId }}",
                )
            }
        }

        val primaryRule = matchingRules.firstOrNull()

        // Handle rule outcome if present
        if (primaryRule != null) {
            when (primaryRule.outcome) {
                FirmwareRuleOutcome.INCOMPATIBLE -> {
                    return FirmwareCompatibilityResult(
                        status = FirmwareCompatibilityStatus.INCOMPATIBLE,
                        matchedRule = primaryRule,
                        protocolResolution = null,
                        reasonCode = "ERR_FIRMWARE_UNSUPPORTED",
                        message = "firmware '${observation.version.rawValue}' is unsupported: ${primaryRule.rationale}",
                        evidenceReference = primaryRule.evidenceReference,
                    )
                }
                FirmwareRuleOutcome.UNKNOWN_OR_WITHDRAWN -> {
                    return FirmwareCompatibilityResult(
                        status = FirmwareCompatibilityStatus.UNKNOWN_FIRMWARE,
                        matchedRule = primaryRule,
                        protocolResolution = null,
                        reasonCode = "ERR_COMPATIBILITY_WITHDRAWN",
                        message = "firmware compatibility claim for '${observation.version.rawValue}' was withdrawn or unverified",
                        evidenceReference = primaryRule.evidenceReference,
                    )
                }
                FirmwareRuleOutcome.COMPATIBLE_WITH_LIMITATIONS -> {
                    val protoRes = underlyingProtocolResolver.resolve(
                        candidates = candidates,
                        fingerprint = fingerprint,
                        identification = identification,
                        observedVersion = observedVersion,
                        observedFirmware = observation.version.rawValue,
                        availableTransports = availableTransports,
                        deviceModelId = deviceModelId,
                    )
                    return FirmwareCompatibilityResult(
                        status = FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
                        matchedRule = primaryRule,
                        protocolResolution = protoRes,
                        reasonCode = "OK_COMPATIBLE_WITH_LIMITATIONS",
                        message = "firmware '${observation.version.rawValue}' is compatible with limitations: ${primaryRule.rationale}",
                        limitations = primaryRule.limitations,
                        evidenceReference = primaryRule.evidenceReference,
                    )
                }
                FirmwareRuleOutcome.COMPATIBLE -> {
                    // Continue to resolve protocol compatibility
                }
            }
        }

        // 6. Underlying protocol resolver evaluation with verified firmware string
        val protoRes = underlyingProtocolResolver.resolve(
            candidates = candidates,
            fingerprint = fingerprint,
            identification = identification,
            observedVersion = observedVersion,
            observedFirmware = observation.version.rawValue,
            availableTransports = availableTransports,
            deviceModelId = deviceModelId,
        )

        return when (protoRes.status) {
            CompatibilityStatus.COMPATIBLE -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.COMPATIBLE,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "OK_COMPATIBLE",
                    message = "firmware '${observation.version.rawValue}' and protocol are fully compatible",
                    evidenceReference = primaryRule?.evidenceReference,
                )
            }
            CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "OK_COMPATIBLE_WITH_LIMITATIONS",
                    message = protoRes.machineReason,
                    limitations = protoRes.limitations,
                    evidenceReference = primaryRule?.evidenceReference,
                )
            }
            CompatibilityStatus.INCOMPATIBLE -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.INCOMPATIBLE,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = protoRes.reasonCode,
                    message = protoRes.machineReason,
                    evidenceReference = primaryRule?.evidenceReference,
                )
            }
            CompatibilityStatus.UNKNOWN_VERSION -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.UNKNOWN_FIRMWARE,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "ERR_UNKNOWN_VERSION",
                    message = protoRes.machineReason,
                )
            }
            CompatibilityStatus.AMBIGUOUS -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.AMBIGUOUS,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "ERR_AMBIGUOUS_PROTOCOL",
                    message = protoRes.machineReason,
                )
            }
            CompatibilityStatus.INSUFFICIENT_EVIDENCE -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.INSUFFICIENT_EVIDENCE,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "ERR_INSUFFICIENT_EVIDENCE",
                    message = protoRes.machineReason,
                )
            }
            CompatibilityStatus.UNSUPPORTED_SCHEMA -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.INCOMPATIBLE,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = "ERR_UNSUPPORTED_SCHEMA",
                    message = protoRes.machineReason,
                )
            }
            CompatibilityStatus.BLOCKED_BY_POLICY -> {
                FirmwareCompatibilityResult(
                    status = FirmwareCompatibilityStatus.BLOCKED_BY_POLICY,
                    matchedRule = primaryRule,
                    protocolResolution = protoRes,
                    reasonCode = protoRes.reasonCode,
                    message = protoRes.machineReason,
                )
            }
        }
    }
}
