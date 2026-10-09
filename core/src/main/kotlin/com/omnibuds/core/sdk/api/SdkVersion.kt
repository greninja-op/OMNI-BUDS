package com.omnibuds.core.sdk.api

/**
 * Versioned metadata for the Community Protocol SDK.
 *
 * Enforces explicit versioning across the public SDK contract.
 */
data class SdkVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SdkVersion> {

    init {
        require(major >= 1) { "major must be >= 1" }
        require(minor >= 0) { "minor must be >= 0" }
        require(patch >= 0) { "patch must be >= 0" }
    }

    override fun compareTo(other: SdkVersion): Int {
        val majorCmp = major.compareTo(other.major)
        if (majorCmp != 0) return majorCmp
        val minorCmp = minor.compareTo(other.minor)
        if (minorCmp != 0) return minorCmp
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** The current public SDK contract version. */
        val CURRENT = SdkVersion(1, 0, 0)

        fun parse(value: String): SdkVersion? {
            val parts = value.split(".")
            if (parts.size != 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            if (major < 1 || minor < 0 || patch < 0) return null
            return SdkVersion(major, minor, patch)
        }
    }
}
