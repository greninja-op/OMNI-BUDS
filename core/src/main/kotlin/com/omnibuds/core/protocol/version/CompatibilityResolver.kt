package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationResult

/**
 * Deterministic compatibility resolution engine.
 *
 * Evaluates candidate protocols against observed device evidence, transport constraints,
 * firmware restrictions, and version compatibility rules.
 */
class CompatibilityResolver {

    /**
     * Resolves protocol compatibility deterministically.
     *
     * Rules:
     * 1. If identification is InsufficientEvidence, returns INSUFFICIENT_EVIDENCE.
     * 2. Filters candidates by supported transports.
     * 3. Filters candidates by device model scope if specified.
     * 4. Filters candidates by firmware version constraints if observed.
     * 5. If observed version is Unknown and candidate requires specific version, returns UNKNOWN_VERSION.
     * 6. If no candidates satisfy the version constraint, returns INCOMPATIBLE.
     * 7. If exactly 1 candidate matches, returns COMPATIBLE (or COMPATIBLE_WITH_LIMITATIONS).
     * 8. If >1 candidates match equally, returns AMBIGUOUS (never picks arbitrary winner).
     */
    fun resolve(
        candidates: List<ProtocolIdentity>,
        fingerprint: DeviceFingerprint,
        identification: IdentificationResult,
        observedVersion: ProtocolVersion,
        observedFirmware: String?,
        availableTransports: Set<TransportKind>,
        deviceModelId: String? = null,
    ): CompatibilityResolution {
        if (identification is IdentificationResult.InsufficientEvidence) {
            return CompatibilityResolution.insufficientEvidence()
        }

        if (candidates.isEmpty()) {
            return CompatibilityResolution.incompatible(
                candidates = emptyList(),
                reasonCode = "ERR_NO_CANDIDATES",
                reason = "no protocol implementations registered for this device family",
            )
        }

        // Schema validation gate
        for (candidate in candidates) {
            if (candidate.schemaVersion !in ProtocolSchemaVersion.SUPPORTED_VERSIONS) {
                return CompatibilityResolution.unsupportedSchema(candidate.schemaVersion)
            }
        }

        // Transport filtering
        val transportCompatible = candidates.filter { it.transport in availableTransports }
        if (transportCompatible.isEmpty()) {
            return CompatibilityResolution.incompatible(
                candidates = candidates,
                reasonCode = "ERR_TRANSPORT_UNSUPPORTED",
                reason = "device protocol requires transports not available: ${candidates.map { it.transport }}",
            )
        }

        // Model scope filtering
        val modelCompatible = if (deviceModelId != null) {
            val matching = transportCompatible.filter { it.supportedModels.isEmpty() || deviceModelId in it.supportedModels }
            if (matching.isEmpty()) transportCompatible else matching
        } else {
            transportCompatible
        }

        // Firmware filtering
        val firmwareCompatible = if (observedFirmware != null) {
            modelCompatible.filter { candidate ->
                candidate.verifiedFirmware == null || observedFirmware in candidate.verifiedFirmware
            }
        } else {
            modelCompatible
        }

        if (firmwareCompatible.isEmpty()) {
            return CompatibilityResolution.incompatible(
                candidates = modelCompatible,
                reasonCode = "ERR_FIRMWARE_INCOMPATIBLE",
                reason = "observed firmware '$observedFirmware' is not verified for any candidate protocol",
            )
        }

        // Version constraint evaluation
        if (observedVersion is ProtocolVersion.Unknown) {
            // Check if any candidate explicitly permits Unknown or is an unversioned baseline
            val permitsUnknown = firmwareCompatible.filter { it.versionConstraint is VersionConstraint.None || it.version is ProtocolVersion.Unknown }
            return if (permitsUnknown.size == 1) {
                CompatibilityResolution.compatibleWithLimitations(
                    protocol = permitsUnknown.single(),
                    limitations = listOf("unverified protocol version; operation scope restricted"),
                    reason = "device protocol version is unobserved; using unversioned baseline",
                )
            } else {
                CompatibilityResolution.unknownVersion(
                    candidates = firmwareCompatible,
                    reason = "device protocol version could not be determined; mutating operations blocked",
                )
            }
        }

        val versionMatching = firmwareCompatible.filter { candidate ->
            candidate.versionConstraint.isSatisfiedBy(observedVersion)
        }

        if (versionMatching.isEmpty()) {
            return CompatibilityResolution.incompatible(
                candidates = firmwareCompatible,
                reasonCode = "ERR_VERSION_MISMATCH",
                reason = "observed protocol version '${observedVersion.rawValue}' does not satisfy any candidate constraints",
            )
        }

        if (versionMatching.size > 1) {
            // Check for exact version match disambiguation
            val exactMatches = versionMatching.filter { it.version == observedVersion }
            if (exactMatches.size == 1) {
                val candidate = exactMatches.single()
                return if (candidate.limitations.isNotEmpty()) {
                    CompatibilityResolution.compatibleWithLimitations(
                        protocol = candidate,
                        limitations = candidate.limitations,
                        reason = "matched exact protocol version '${observedVersion.rawValue}' with limitations",
                    )
                } else {
                    CompatibilityResolution.compatible(candidate)
                }
            }

            return CompatibilityResolution.ambiguous(
                candidates = versionMatching,
                reason = "multiple candidates (${versionMatching.map { it.protocolId }}) match version '${observedVersion.rawValue}' equally; no candidate selected",
            )
        }

        val selected = versionMatching.single()
        return if (selected.limitations.isNotEmpty()) {
            CompatibilityResolution.compatibleWithLimitations(
                protocol = selected,
                limitations = selected.limitations,
                reason = "candidate '${selected.protocolId}' matched with documented limitations",
            )
        } else {
            CompatibilityResolution.compatible(selected)
        }
    }
}
