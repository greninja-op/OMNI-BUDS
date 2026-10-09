package com.omnibuds.core.protocol.version

/**
 * Strongly-typed protocol version representation.
 *
 * Separates protocol versions from application, SDK, schema, and firmware versions.
 * Supports multiple versioning schemes without forcing artificial semantic versioning
 * onto protocols whose specifications use integer revisions or proprietary tokens.
 */
sealed interface ProtocolVersion : Comparable<ProtocolVersion> {

    /** String representation of this version for serialization or diagnostic logs. */
    val rawValue: String

    /**
     * Standard Semantic Versioning (SemVer) with major, minor, and patch components.
     */
    data class Semantic(
        val major: Int,
        val minor: Int,
        val patch: Int = 0,
    ) : ProtocolVersion {
        init {
            require(major >= 0) { "major version must be non-negative" }
            require(minor >= 0) { "minor version must be non-negative" }
            require(patch >= 0) { "patch version must be non-negative" }
        }

        override val rawValue: String = "$major.$minor.$patch"

        override fun compareTo(other: ProtocolVersion): Int {
            if (other !is Semantic) return this.javaClass.name.compareTo(other.javaClass.name)
            val maj = major.compareTo(other.major)
            if (maj != 0) return maj
            val min = minor.compareTo(other.minor)
            if (min != 0) return min
            return patch.compareTo(other.patch)
        }

        override fun toString(): String = rawValue
    }

    /**
     * Monotonic integer revision counter (e.g. Protocol Rev 1, Rev 2).
     */
    data class IntegerRevision(
        val revision: Int,
    ) : ProtocolVersion {
        init {
            require(revision >= 0) { "revision number must be non-negative" }
        }

        override val rawValue: String = revision.toString()

        override fun compareTo(other: ProtocolVersion): Int {
            if (other !is IntegerRevision) return this.javaClass.name.compareTo(other.javaClass.name)
            return revision.compareTo(other.revision)
        }

        override fun toString(): String = "rev-$revision"
    }

    /**
     * Vendor-defined proprietary revision or variant token (e.g. "bmap-v2", "aac-v1").
     *
     * Cannot be safely ordered numeric-wise; ordering defaults to string comparison.
     */
    data class VendorDefined(
        val identifier: String,
    ) : ProtocolVersion {
        init {
            require(identifier.isNotBlank()) { "vendor-defined identifier must not be blank" }
        }

        override val rawValue: String = identifier

        override fun compareTo(other: ProtocolVersion): Int {
            if (other !is VendorDefined) return this.javaClass.name.compareTo(other.javaClass.name)
            return identifier.compareTo(other.identifier)
        }

        override fun toString(): String = identifier
    }

    /**
     * Explicit sentinel indicating that the protocol version has not been observed,
     * cannot be negotiated, or is unknown. Never replaced with 0.0.0 or arbitrary defaults.
     */
    data object Unknown : ProtocolVersion {
        override val rawValue: String = "unknown"

        override fun compareTo(other: ProtocolVersion): Int {
            if (other is Unknown) return 0
            return this.javaClass.name.compareTo(other.javaClass.name)
        }

        override fun toString(): String = "unknown"
    }

    companion object {
        /**
         * Safely parse a semantic version string "X.Y" or "X.Y.Z".
         * Returns null if the format is invalid.
         */
        fun parseSemantic(value: String): Semantic? {
            val parts = value.trim().split(".")
            if (parts.size !in 2..3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else 0
            if (major < 0 || minor < 0 || patch < 0) return null
            return Semantic(major, minor, patch)
        }

        /**
         * Safely parse an integer revision string.
         */
        fun parseIntegerRevision(value: String): IntegerRevision? {
            val rev = value.trim().removePrefix("rev-").removePrefix("r").toIntOrNull() ?: return null
            if (rev < 0) return null
            return IntegerRevision(rev)
        }
    }
}
