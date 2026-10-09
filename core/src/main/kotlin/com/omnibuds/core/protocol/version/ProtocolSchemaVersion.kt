package com.omnibuds.core.protocol.version

/**
 * Strongly-typed protocol metadata schema version.
 *
 * Governs the format of stored protocol definitions, schemas, and evidence in repositories.
 * Distinct from device wire protocol versions and firmware versions.
 */
data class ProtocolSchemaVersion(
    val version: Int,
) : Comparable<ProtocolSchemaVersion> {
    init {
        require(version >= 1) { "schema version must be >= 1" }
    }

    override fun compareTo(other: ProtocolSchemaVersion): Int = version.compareTo(other.version)

    override fun toString(): String = "schema-v$version"

    companion object {
        /** The initial schema format for protocol metadata. */
        val V1 = ProtocolSchemaVersion(1)

        /** Schema format v2 introducing explicit version constraints and multi-revision indices. */
        val V2 = ProtocolSchemaVersion(2)

        /** Current runtime schema version. */
        val CURRENT = V2

        /** Supported schema versions that this runtime can read or migrate. */
        val SUPPORTED_VERSIONS: Set<ProtocolSchemaVersion> = setOf(V1, V2)
    }
}
