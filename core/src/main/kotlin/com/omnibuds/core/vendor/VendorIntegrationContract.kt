package com.omnibuds.core.vendor

/**
 * Versioned vendor integration contract metadata.
 *
 * Phase 40: every vendor integration declares the contract version
 * it was built against. Contract changes are explicit and testable;
 * unknown versions are rejected rather than silently interpreted.
 */
data class VendorIntegrationContract(
    /** The contract version this integration implements. */
    val contractVersion: Int,
    /** Stable integration identifier, e.g. `vendor.acme`. */
    val integrationId: String,
    /** Product families covered, e.g. `acme.buds-pro`. */
    val productFamilies: Set<String>,
    /** Firmware versions verified, or null when firmware-agnostic. */
    val verifiedFirmware: Set<String>?,
) {
    companion object {
        /** The current contract version. */
        const val CURRENT_CONTRACT_VERSION = 1
    }

    init {
        require(contractVersion >= 1) { "contractVersion must be >= 1" }
        require(integrationId.isNotBlank()) { "integrationId must not be blank" }
    }
}

/**
 * Firmware compatibility assessment.
 */
enum class FirmwareCompatibility {
    /** Firmware verified against this integration. */
    VERIFIED,

    /** Firmware in the supported range but not individually verified. */
    SUPPORTED_RANGE,

    /** Firmware unknown; integration is firmware-agnostic. */
    UNKNOWN_FIRMWARE,

    /** Firmware explicitly unsupported. */
    INCOMPATIBLE,
}

/**
 * Evaluate firmware compatibility for an integration.
 *
 * Never invents support: unknown firmware stays unknown, and
 * explicitly unsupported firmware is rejected.
 */
object FirmwareCompatibilityEvaluator {

    fun evaluate(
        contract: VendorIntegrationContract,
        firmwareVersion: String?,
    ): FirmwareCompatibility {
        val verified = contract.verifiedFirmware
        if (verified == null) return FirmwareCompatibility.UNKNOWN_FIRMWARE
        if (firmwareVersion == null) return FirmwareCompatibility.UNKNOWN_FIRMWARE
        return if (firmwareVersion in verified) FirmwareCompatibility.VERIFIED
        else FirmwareCompatibility.INCOMPATIBLE
    }
}
