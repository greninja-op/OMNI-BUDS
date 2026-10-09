package com.omnibuds.core.hil

/**
 * Versioned hardware profile schema.
 *
 * Phase 38: describes the test rig for a future hardware campaign.
 * Unavailable fields are explicit nulls — never invented.
 */
data class HardwareProfile(
    /** Stable profile identifier. */
    val profileId: String,
    /** Schema version. Only [SUPPORTED_SCHEMA_VERSION] is accepted. */
    val schemaVersion: Int,
    /** Host device model, or null when unknown. */
    val hostModel: String?,
    /** Host Android build, or null when unknown. */
    val hostBuild: String?,
    /** Android API level, or null when unknown. */
    val apiLevel: Int?,
    /** OmniBuds build version. */
    val appVersion: String,
    /** Git commit of the build under test. */
    val gitCommit: String,
    /** Device manufacturer, or null when unknown. */
    val deviceManufacturer: String?,
    /** Device model, or null when unknown. */
    val deviceModel: String?,
    /** Firmware version, or null when unknown. */
    val firmwareVersion: String?,
    /** Transport identifier, or null when unknown. */
    val transportId: String?,
    /** Protocol identifier, or null when unknown. */
    val protocolId: String?,
    /** Required test prerequisites. */
    val prerequisites: List<String>,
    /** Allowed operation categories (e.g. `read-only`). */
    val allowedOperations: Set<String>,
    /** Verification status of this profile. */
    val verificationStatus: ProfileVerificationStatus,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

/** Profile confidence and verification status. */
enum class ProfileVerificationStatus {
    /** Declared but never exercised. */
    DECLARED,

    /** Exercised in simulation only. */
    SIMULATED,

    /** Exercised against real hardware. */
    HARDWARE_VERIFIED,
}

/** Profile validation result. */
sealed interface ProfileValidation {
    data object Valid : ProfileValidation
    data class Invalid(val reasons: List<String>) : ProfileValidation
}

/**
 * Validates hardware profiles. External definitions are untrusted.
 */
object HardwareProfileValidator {

    fun validate(profile: HardwareProfile): ProfileValidation {
        val reasons = mutableListOf<String>()

        if (profile.profileId.isBlank()) {
            reasons.add("profileId must not be blank")
        }
        if (profile.schemaVersion != HardwareProfile.SUPPORTED_SCHEMA_VERSION) {
            reasons.add(
                "unsupported schema version: ${profile.schemaVersion}",
            )
        }
        if (profile.appVersion.isBlank()) {
            reasons.add("appVersion must not be blank")
        }
        if (profile.gitCommit.isBlank()) {
            reasons.add("gitCommit must not be blank")
        }
        if (profile.apiLevel != null && profile.apiLevel !in 26..99) {
            reasons.add("apiLevel out of range: ${profile.apiLevel}")
        }
        profile.prerequisites.forEachIndexed { index, p ->
            if (p.isBlank()) reasons.add("prerequisite[$index] must not be blank")
        }

        return if (reasons.isEmpty()) ProfileValidation.Valid
        else ProfileValidation.Invalid(reasons.sorted())
    }
}
