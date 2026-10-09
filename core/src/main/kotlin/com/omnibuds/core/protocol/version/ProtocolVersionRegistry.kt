package com.omnibuds.core.protocol.version

import com.omnibuds.core.state.VerificationLevel

/**
 * Registry managing versioned protocol implementations.
 *
 * Rejects duplicate registrations, invalid constraints, unsupported schemas,
 * and self-promoted hardware verification claims.
 */
class ProtocolVersionRegistry {

    private val entries = mutableMapOf<String, ProtocolIdentity>()

    /**
     * Register a verified protocol implementation.
     *
     * @throws IllegalArgumentException on duplicate ID, unsupported schema, or invalid constraints.
     * @throws SecurityException on unevidenced hardware verification claims.
     */
    fun register(identity: ProtocolIdentity) {
        require(!entries.containsKey(identity.protocolId)) {
            "duplicate protocol registration: '${identity.protocolId}' is already registered"
        }

        require(identity.schemaVersion in ProtocolSchemaVersion.SUPPORTED_VERSIONS) {
            "unsupported metadata schema version: ${identity.schemaVersion}"
        }

        // Anti-self-promotion: Hardware or persistence verification requires explicit evidence provenance IDs
        if (identity.confidence == VerificationLevel.HARDWARE_VERIFIED ||
            identity.confidence == VerificationLevel.PERSISTENCE_VERIFIED
        ) {
            if (identity.evidenceIds.isEmpty()) {
                throw SecurityException(
                    "protocol '${identity.protocolId}' claims ${identity.confidence} without evidence provenance IDs",
                )
            }
        }

        entries[identity.protocolId] = identity
    }

    /**
     * Retrieve a registered protocol by its unique protocol ID.
     */
    fun findById(protocolId: String): ProtocolIdentity? = entries[protocolId]

    /**
     * List all registered protocol implementations for a vendor namespace.
     */
    fun findByNamespace(vendorNamespace: String): List<ProtocolIdentity> =
        entries.values.filter { it.vendorNamespace == vendorNamespace }

    /**
     * List all registered protocols.
     */
    fun all(): List<ProtocolIdentity> = entries.values.toList()

    /**
     * Clear all registered protocol implementations (for test isolation).
     */
    fun clear() {
        entries.clear()
    }

    val count: Int
        get() = entries.size
}
