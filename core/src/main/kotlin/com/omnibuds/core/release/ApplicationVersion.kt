package com.omnibuds.core.release

/**
 * Single authoritative source of truth for OmniBuds release and build metadata.
 *
 * Implements Phase 51 Section 4 requirements:
 * - Single authoritative application-version definition.
 * - Monotonically increasing version codes.
 * - Separation between application version, community protocol SDK version,
 *   protocol knowledge schema version, and configuration schema version.
 * - Deterministic build metadata validation.
 */
data class ApplicationVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val buildNumber: Int? = null,
) {
    init {
        require(major >= 0) { "Major version cannot be negative: $major" }
        require(minor >= 0) { "Minor version cannot be negative: $minor" }
        require(patch >= 0) { "Patch version cannot be negative: $patch" }
        buildNumber?.let {
            require(it >= 0) { "Build number cannot be negative: $it" }
        }
    }

    /**
     * Standard semantic version string (e.g., "1.0.0" or "1.0.0-rc1").
     */
    val versionName: String = buildString {
        append("$major.$minor.$patch")
        if (!preRelease.isNullOrBlank()) {
            append("-$preRelease")
        }
    }

    /**
     * Android version code calculated monotonically as:
     * major * 1,000,000 + minor * 10,000 + patch * 100 + (buildNumber ?: 0)
     * Fits cleanly in a 32-bit positive integer (max ~2.1B, Google Play limit).
     */
    val versionCode: Int = (major * 1_000_000) + (minor * 10_000) + (patch * 100) + (buildNumber ?: 0)

    override fun toString(): String = versionName

    companion object {
        val CURRENT = ApplicationVersion(
            major = 1,
            minor = 0,
            patch = 0,
            preRelease = null,
            buildNumber = 0,
        )

        fun parse(versionString: String): ApplicationVersion? {
            val trimmed = versionString.trim()
            val regex = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$")
            val match = regex.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val pre = match.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() }
            return ApplicationVersion(major, minor, patch, pre)
        }
    }
}

/**
 * Release metadata manifest item describing an individual build artifact.
 */
data class ReleaseArtifactInfo(
    val productName: String,
    val applicationVersion: String,
    val platform: String,
    val architecture: String,
    val filename: String,
    val format: String,
    val sizeBytes: Long,
    val sha256Checksum: String,
    val gitRevision: String,
    val buildTask: String,
    val buildTimestamp: String,
    val signingStatus: SigningStatus,
    val validationStatus: String,
    val knownLimitations: List<String> = emptyList(),
) {
    init {
        require(productName.isNotBlank()) { "Product name must not be blank" }
        require(applicationVersion.isNotBlank()) { "Application version must not be blank" }
        require(filename.isNotBlank()) { "Filename must not be blank" }
        require(sizeBytes > 0) { "Artifact size must be positive: $sizeBytes" }
        require(sha256Checksum.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "Checksum must be a valid 64-character SHA-256 hex string: $sha256Checksum"
        }
    }
}

enum class SigningStatus {
    UNSIGNED,
    DEBUG_SIGNED,
    RELEASE_SIGNED,
    BLOCKED_NO_KEYS,
}

/**
 * Release readiness verification result.
 */
data class ReleaseGateResult(
    val gateId: String,
    val name: String,
    val passed: Boolean,
    val isMandatory: Boolean,
    val statusDetail: String,
)
