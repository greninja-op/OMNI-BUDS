package com.omnibuds.core.firmware

/**
 * Expresses a constraint or range of compatible firmware revisions.
 */
sealed interface FirmwareConstraint {

    /** Evaluates whether an observed [FirmwareVersion] satisfies this constraint. */
    fun isSatisfiedBy(version: FirmwareVersion): Boolean

    /** Matches any firmware version, including Unknown when firmware is completely agnostic. */
    data object Any : FirmwareConstraint {
        override fun isSatisfiedBy(version: FirmwareVersion): Boolean = true
        override fun toString(): String = "*"
    }

    /** Requires an exact firmware version match. */
    data class Exact(val expected: FirmwareVersion) : FirmwareConstraint {
        override fun isSatisfiedBy(version: FirmwareVersion): Boolean =
            version == expected || version.rawValue.equals(expected.rawValue, ignoreCase = true)

        override fun toString(): String = "=${expected.rawValue}"
    }

    /** Requires firmware to be an element of an explicit allowlist. */
    data class Allowlist(val allowed: Set<FirmwareVersion>) : FirmwareConstraint {
        init {
            require(allowed.isNotEmpty()) { "allowlist must not be empty" }
        }

        override fun isSatisfiedBy(version: FirmwareVersion): Boolean {
            return allowed.any { it == version || it.rawValue.equals(version.rawValue, ignoreCase = true) }
        }

        override fun toString(): String = "in [${allowed.joinToString { it.rawValue }}]"
    }

    /**
     * An inclusive or exclusive ordered range of versions.
     * Only valid for version schemes with reliable ordering (Semantic, BuildNumber, DateBased).
     */
    data class Range(
        val minInclusive: FirmwareVersion? = null,
        val maxInclusive: FirmwareVersion? = null,
    ) : FirmwareConstraint {
        init {
            require(minInclusive != null || maxInclusive != null) { "range must have at least one bound" }
            if (minInclusive != null && maxInclusive != null) {
                require(minInclusive.scheme == maxInclusive.scheme) { "range bounds must share the same scheme" }
                require(minInclusive <= maxInclusive) { "min bound must not exceed max bound" }
            }
        }

        override fun isSatisfiedBy(version: FirmwareVersion): Boolean {
            if (version is FirmwareVersion.Unknown) return false
            if (minInclusive != null) {
                if (version.scheme != minInclusive.scheme) return false
                if (version < minInclusive) return false
            }
            if (maxInclusive != null) {
                if (version.scheme != maxInclusive.scheme) return false
                if (version > maxInclusive) return false
            }
            return true
        }

        override fun toString(): String = "[${minInclusive?.rawValue ?: "-inf"}, ${maxInclusive?.rawValue ?: "+inf"}]"
    }

    /**
     * An explicit denylist of known buggy, incompatible, or brick-risk firmware versions.
     * Rejects any version in the blocked set.
     */
    data class Denylist(val blocked: Set<FirmwareVersion>) : FirmwareConstraint {
        init {
            require(blocked.isNotEmpty()) { "denylist must not be empty" }
        }

        override fun isSatisfiedBy(version: FirmwareVersion): Boolean {
            if (version is FirmwareVersion.Unknown) return false
            return blocked.none { it == version || it.rawValue.equals(version.rawValue, ignoreCase = true) }
        }

        override fun toString(): String = "not in [${blocked.joinToString { it.rawValue }}]"
    }

    /**
     * Minimum required firmware revision.
     */
    data class AtLeast(val min: FirmwareVersion) : FirmwareConstraint {
        override fun isSatisfiedBy(version: FirmwareVersion): Boolean {
            if (version is FirmwareVersion.Unknown) return false
            if (version.scheme != min.scheme) return false
            return version >= min
        }

        override fun toString(): String = ">=${min.rawValue}"
    }
}
