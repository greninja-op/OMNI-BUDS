package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel

/**
 * Canonical protocol identity model.
 *
 * Represents a verified protocol specification or implementation candidate with its
 * versioning scheme, schema format, transport requirements, and constraints.
 */
data class ProtocolIdentity(
    /** Stable unique protocol key, e.g. "bose.bmap" or "acme.buds". */
    val protocolId: String,

    /** Namespace / vendor scope, e.g. "bose", "sony", "apple", "acme". Null for standard generic protocols. */
    val vendorNamespace: String?,

    /** Protocol version represented by this definition. */
    val version: ProtocolVersion,

    /** Schema version of the metadata format. */
    val schemaVersion: ProtocolSchemaVersion,

    /** Primary transport channel required. */
    val transport: TransportKind,

    /** Version compatibility constraint that this implementation satisfies or matches against. */
    val versionConstraint: VersionConstraint,

    /** Supported device model IDs, or empty when matching all models in namespace. */
    val supportedModels: Set<String> = emptySet(),

    /** Firmware versions verified for this protocol implementation, or null if firmware-agnostic. */
    val verifiedFirmware: Set<String>? = null,

    /** Evidence confidence level; never self-promoted above actual evidence. */
    val confidence: VerificationLevel,

    /** Evidence provenance identifiers backing this protocol identity. */
    val evidenceIds: List<String> = emptyList(),

    /** Known functional or platform limitations when running this protocol. */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(protocolId.isNotBlank()) { "protocolId must not be blank" }
        require(vendorNamespace == null || vendorNamespace.isNotBlank()) {
            "vendorNamespace must be non-blank when specified"
        }
        require(transport != TransportKind.UNKNOWN) {
            "protocol '$protocolId' must specify a valid transport; UNKNOWN is prohibited"
        }
    }
}
