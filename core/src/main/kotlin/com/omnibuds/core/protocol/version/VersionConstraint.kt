package com.omnibuds.core.protocol.version

/**
 * Typed constraint model expressing protocol version compatibility requirements.
 */
sealed interface VersionConstraint {

    /**
     * Evaluates whether [version] satisfies this constraint.
     */
    fun isSatisfiedBy(version: ProtocolVersion): Boolean

    /**
     * Exact version match constraint.
     */
    data class Exact(
        val expected: ProtocolVersion,
    ) : VersionConstraint {
        override fun isSatisfiedBy(version: ProtocolVersion): Boolean = version == expected
    }

    /**
     * Semantic version range constraint `[min, maxInclusive]`.
     */
    data class SemanticRange(
        val min: ProtocolVersion.Semantic,
        val maxInclusive: ProtocolVersion.Semantic,
    ) : VersionConstraint {
        init {
            require(min <= maxInclusive) { "min version must be <= maxInclusive version" }
        }

        override fun isSatisfiedBy(version: ProtocolVersion): Boolean {
            if (version !is ProtocolVersion.Semantic) return false
            return version >= min && version <= maxInclusive
        }
    }

    /**
     * Integer revision range constraint `[minRevision, maxRevision]`.
     */
    data class IntegerRange(
        val minRevision: Int,
        val maxRevision: Int,
    ) : VersionConstraint {
        init {
            require(minRevision <= maxRevision) { "minRevision must be <= maxRevision" }
        }

        override fun isSatisfiedBy(version: ProtocolVersion): Boolean {
            if (version !is ProtocolVersion.IntegerRevision) return false
            return version.revision in minRevision..maxRevision
        }
    }

    /**
     * Explicitly enumerated set of compatible versions.
     */
    data class Enumerated(
        val allowed: Set<ProtocolVersion>,
    ) : VersionConstraint {
        override fun isSatisfiedBy(version: ProtocolVersion): Boolean = version in allowed
    }

    /**
     * Matches any known version (excludes [ProtocolVersion.Unknown]).
     */
    data object AnyKnown : VersionConstraint {
        override fun isSatisfiedBy(version: ProtocolVersion): Boolean = version !is ProtocolVersion.Unknown
    }

    /**
     * Matches no version (for disabled or retracted protocol implementations).
     */
    data object None : VersionConstraint {
        override fun isSatisfiedBy(version: ProtocolVersion): Boolean = false
    }
}
