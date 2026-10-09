package com.omnibuds.core.testkit.crossdevice

/**
 * Evidence category of a device profile.
 *
 * Phase 31 (OB-P31-REQ-003): uses the project's evidence vocabulary.
 * A synthetic profile is never promoted automatically.
 */
enum class ProfileEvidence {
    /** Hand-written; validates generic behavior only. */
    SYNTHETIC,

    /** Derived from a fixture with documented provenance. */
    FIXTURE_DERIVED,

    /** Tested in a controlled lab. */
    LAB_TESTED,

    /** Observed on real hardware. */
    HARDWARE_VERIFIED,

    /** Hardware persistence independently verified. */
    PERSISTENCE_VERIFIED,
}

/**
 * A versioned device profile for cross-device testing.
 *
 * Phase 31 (OB-P31-REQ-001/002): unknown values stay unknown — never
 * guessed. Nullable fields are the honest representation.
 */
data class DeviceProfile(
    /** Stable profile identifier. */
    val profileId: String,
    /** Profile schema version. */
    val schemaVersion: Int = 1,
    val manufacturer: String? = null,
    val model: String? = null,
    val hardwareRevision: String? = null,
    val firmwareVersion: String? = null,
    val transportKind: String? = null,
    val protocolFamily: String? = null,
    val protocolVersion: String? = null,
    /** Declared capability identifiers. */
    val capabilities: Set<String> = emptySet(),
    val evidence: ProfileEvidence = ProfileEvidence.SYNTHETIC,
    /** Fixture IDs backing this profile. */
    val fixtures: List<String> = emptyList(),
    val limitations: String? = null,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
    }
}

/**
 * Profile validation outcomes.
 */
sealed interface ProfileValidation {
    data object Valid : ProfileValidation
    data class Invalid(val reasons: List<String>) : ProfileValidation
}

/**
 * Validates device profiles.
 */
object DeviceProfileValidator {

    /** Supported profile schema versions. */
    val supportedVersions: IntRange = 1..1

    fun validate(profile: DeviceProfile): ProfileValidation {
        val reasons = mutableListOf<String>()
        if (profile.schemaVersion !in supportedVersions) {
            reasons.add(
                "unsupported schema version ${profile.schemaVersion}; " +
                    "supported: $supportedVersions",
            )
        }
        if (profile.evidence == ProfileEvidence.HARDWARE_VERIFIED ||
            profile.evidence == ProfileEvidence.PERSISTENCE_VERIFIED
        ) {
            if (profile.manufacturer.isNullOrBlank() || profile.model.isNullOrBlank()) {
                reasons.add(
                    "hardware-verified profiles must identify manufacturer and model",
                )
            }
        }
        return if (reasons.isEmpty()) ProfileValidation.Valid
        else ProfileValidation.Invalid(reasons)
    }
}
