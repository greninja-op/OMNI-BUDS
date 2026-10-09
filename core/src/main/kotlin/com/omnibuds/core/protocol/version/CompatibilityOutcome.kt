package com.omnibuds.core.protocol.version

/**
 * Outcomes of protocol compatibility resolution.
 */
enum class CompatibilityStatus {
    /** Exactly one candidate matched all constraints and is fully compatible. */
    COMPATIBLE,

    /** Candidate is compatible but with documented limitations (e.g. read-only fallback). */
    COMPATIBLE_WITH_LIMITATIONS,

    /** Candidate matches family/namespace but version or firmware is incompatible. */
    INCOMPATIBLE,

    /** Protocol version of the device is unknown or cannot be observed. */
    UNKNOWN_VERSION,

    /** Multiple candidates match equally with no disambiguating evidence. No arbitrary winner. */
    AMBIGUOUS,

    /** Gathered identity evidence is too thin to evaluate compatibility. */
    INSUFFICIENT_EVIDENCE,

    /** Protocol metadata uses an unsupported schema version. */
    UNSUPPORTED_SCHEMA,

    /** Protocol selection is blocked by centralized security or device access policy. */
    BLOCKED_BY_POLICY,
}

/**
 * Result of a deterministic compatibility resolution evaluation.
 */
data class CompatibilityResolution(
    val status: CompatibilityStatus,
    val selectedProtocol: ProtocolIdentity?,
    val candidateProtocols: List<ProtocolIdentity>,
    val reasonCode: String,
    val machineReason: String,
    val limitations: List<String> = emptyList(),
) {
    init {
        when (status) {
            CompatibilityStatus.COMPATIBLE,
            CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS -> {
                require(selectedProtocol != null) {
                    "$status requires a selected protocol"
                }
                require(candidateProtocols.contains(selectedProtocol)) {
                    "selectedProtocol must be in candidateProtocols"
                }
            }
            CompatibilityStatus.AMBIGUOUS -> {
                require(selectedProtocol == null && candidateProtocols.size > 1) {
                    "AMBIGUOUS requires no selected protocol and >1 candidates"
                }
            }
            CompatibilityStatus.INCOMPATIBLE,
            CompatibilityStatus.UNKNOWN_VERSION,
            CompatibilityStatus.INSUFFICIENT_EVIDENCE,
            CompatibilityStatus.UNSUPPORTED_SCHEMA,
            CompatibilityStatus.BLOCKED_BY_POLICY -> {
                require(selectedProtocol == null) {
                    "$status must not select any protocol candidate"
                }
            }
        }
    }

    /**
     * Whether mutating (write/control) operations are permitted.
     * Only true for strictly COMPATIBLE resolutions.
     */
    val canAuthorizeMutatingOperations: Boolean
        get() = status == CompatibilityStatus.COMPATIBLE

    /**
     * Whether safe read-only operations may be executed.
     */
    val canAuthorizeReadOnlyOperations: Boolean
        get() = status == CompatibilityStatus.COMPATIBLE || status == CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS

    companion object {
        fun compatible(protocol: ProtocolIdentity): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.COMPATIBLE,
                selectedProtocol = protocol,
                candidateProtocols = listOf(protocol),
                reasonCode = "OK_COMPATIBLE",
                machineReason = "protocol '${protocol.protocolId}' version '${protocol.version.rawValue}' is compatible",
            )

        fun compatibleWithLimitations(
            protocol: ProtocolIdentity,
            limitations: List<String>,
            reason: String,
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
                selectedProtocol = protocol,
                candidateProtocols = listOf(protocol),
                reasonCode = "OK_COMPATIBLE_WITH_LIMITATIONS",
                machineReason = reason,
                limitations = limitations,
            )

        fun incompatible(
            candidates: List<ProtocolIdentity>,
            reasonCode: String,
            reason: String,
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.INCOMPATIBLE,
                selectedProtocol = null,
                candidateProtocols = candidates,
                reasonCode = reasonCode,
                machineReason = reason,
            )

        fun unknownVersion(
            candidates: List<ProtocolIdentity>,
            reason: String = "device protocol version is unknown or unobserved",
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.UNKNOWN_VERSION,
                selectedProtocol = null,
                candidateProtocols = candidates,
                reasonCode = "ERR_UNKNOWN_VERSION",
                machineReason = reason,
            )

        fun ambiguous(
            candidates: List<ProtocolIdentity>,
            reason: String = "multiple protocol candidates matched equally; no arbitrary winner",
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.AMBIGUOUS,
                selectedProtocol = null,
                candidateProtocols = candidates.sortedBy { it.protocolId },
                reasonCode = "ERR_AMBIGUOUS_CANDIDATES",
                machineReason = reason,
            )

        fun insufficientEvidence(
            reason: String = "insufficient identity evidence to evaluate protocol compatibility",
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.INSUFFICIENT_EVIDENCE,
                selectedProtocol = null,
                candidateProtocols = emptyList(),
                reasonCode = "ERR_INSUFFICIENT_EVIDENCE",
                machineReason = reason,
            )

        fun unsupportedSchema(
            schemaVersion: ProtocolSchemaVersion,
            reason: String = "unsupported protocol metadata schema version $schemaVersion",
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.UNSUPPORTED_SCHEMA,
                selectedProtocol = null,
                candidateProtocols = emptyList(),
                reasonCode = "ERR_UNSUPPORTED_SCHEMA",
                machineReason = reason,
            )

        fun blockedByPolicy(
            reasonCode: String,
            reason: String,
        ): CompatibilityResolution =
            CompatibilityResolution(
                status = CompatibilityStatus.BLOCKED_BY_POLICY,
                selectedProtocol = null,
                candidateProtocols = emptyList(),
                reasonCode = reasonCode,
                machineReason = reason,
            )
    }
}
