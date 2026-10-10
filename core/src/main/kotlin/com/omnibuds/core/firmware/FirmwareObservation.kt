package com.omnibuds.core.firmware

import com.omnibuds.core.state.VerificationLevel

/**
 * Categorization of the origin of a firmware version observation.
 *
 * Distinguishes authoritative on-device readings from static or unverified sources.
 */
enum class FirmwareObservationSource {
    /** Explicitly read from authoritative device interface (e.g. GATT DIS 0x2A26 or vendor command). */
    DEVICE_DIS_AUTHORITATIVE,

    /** Read via vendor proprietary protocol telemetry payload. */
    VENDOR_TELEMETRY,

    /** Retrieved from previously verified persistent storage cache. */
    PERSISTED_CACHE,

    /** Inferred from broadcast beacon or advertising packet (e.g. manufacturer data). */
    ADVERTISEMENT_INFERRED,

    /** Provided via test fixture or synthetic device mock. */
    TEST_FIXTURE,

    /** Manually entered by user (strictly untrusted for capability enablement). */
    USER_MANUAL_ENTRY,

    /** No observation source available. */
    UNKNOWN,
}

/**
 * Validation state of an observed firmware value.
 */
enum class FirmwareValidationState {
    /** Value is well-formed, within length limits, and parsed into a recognized scheme. */
    VALID,

    /** Value is empty, null, or indicates unknown firmware. */
    UNKNOWN_OR_MISSING,

    /** Value exceeded size limits, contained invalid characters, or failed schema sanity checks. */
    MALFORMED,

    /** Value was rejected by security policy (e.g. disallowed characters, potential injection). */
    REJECTED_BY_POLICY,
}

/**
 * An immutable, evidence-backed firmware observation recording how, when,
 * and with what confidence a firmware version was observed on a device.
 */
data class FirmwareObservation(
    /** Device identifier this observation belongs to. */
    val deviceId: String,

    /** Raw string value as read or provided. */
    val rawValue: String?,

    /** Strongly-typed parsed version. */
    val version: FirmwareVersion,

    /** Origin source of this observation. */
    val source: FirmwareObservationSource,

    /** Monotonic or epoch timestamp (milliseconds) when observation was made. */
    val observedAtMs: Long,

    /** Verification evidence level supporting this observation. */
    val verificationLevel: VerificationLevel,

    /** Hardware revision observed alongside firmware, if any. */
    val hardwareRevision: String? = null,

    /** Associated protocol scope under which observation occurred, if known. */
    val protocolScope: String? = null,

    /** Validation status of this observation. */
    val validationState: FirmwareValidationState = FirmwareValidationState.VALID,

    /** Documented limitations or caveats regarding this observation. */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(observedAtMs >= 0) { "observedAtMs must be non-negative" }
    }

    /**
     * Checks if this observation is fresh given the current time and a maximum age.
     */
    fun isFresh(currentTimeMs: Long, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) return true
        val age = currentTimeMs - observedAtMs
        return age in 0..maxAgeMs
    }

    /**
     * Whether this observation carries sufficient trust for mutating protocol operations.
     * User-entered or unverified inferred observations cannot authorize mutating commands.
     */
    val isTrustworthyForMutations: Boolean
        get() = validationState == FirmwareValidationState.VALID &&
                source in setOf(
                    FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
                    FirmwareObservationSource.VENDOR_TELEMETRY,
                    FirmwareObservationSource.PERSISTED_CACHE,
                    FirmwareObservationSource.TEST_FIXTURE,
                ) &&
                verificationLevel.ordinal >= VerificationLevel.LAB_TESTED.ordinal &&
                version !is FirmwareVersion.Unknown

    companion object {
        const val DEFAULT_FRESHNESS_WINDOW_MS: Long = 300_000L // 5 minutes

        /**
         * Create an observation representing missing or unavailable firmware.
         */
        fun missing(
            deviceId: String,
            timestampMs: Long,
        ): FirmwareObservation = FirmwareObservation(
            deviceId = deviceId,
            rawValue = null,
            version = FirmwareVersion.Unknown,
            source = FirmwareObservationSource.UNKNOWN,
            observedAtMs = timestampMs,
            verificationLevel = VerificationLevel.INFERRED,
            validationState = FirmwareValidationState.UNKNOWN_OR_MISSING,
        )

        /**
         * Safely construct and validate a device observation from raw readings.
         */
        fun create(
            deviceId: String,
            rawVersion: String?,
            source: FirmwareObservationSource,
            observedAtMs: Long,
            verificationLevel: VerificationLevel,
            hardwareRevision: String? = null,
            protocolScope: String? = null,
            preferredScheme: FirmwareScheme? = null,
        ): FirmwareObservation {
            if (rawVersion.isNullOrBlank()) {
                return FirmwareObservation(
                    deviceId = deviceId,
                    rawValue = rawVersion,
                    version = FirmwareVersion.Unknown,
                    source = source,
                    observedAtMs = observedAtMs,
                    verificationLevel = verificationLevel,
                    hardwareRevision = hardwareRevision,
                    protocolScope = protocolScope,
                    validationState = FirmwareValidationState.UNKNOWN_OR_MISSING,
                )
            }

            // Security check: bounds and printable ASCII
            if (rawVersion.length > 128) {
                return FirmwareObservation(
                    deviceId = deviceId,
                    rawValue = rawVersion.take(128),
                    version = FirmwareVersion.Unknown,
                    source = source,
                    observedAtMs = observedAtMs,
                    verificationLevel = verificationLevel,
                    hardwareRevision = hardwareRevision,
                    protocolScope = protocolScope,
                    validationState = FirmwareValidationState.MALFORMED,
                    limitations = listOf("firmware string exceeded maximum allowed length (128)"),
                )
            }

            // Reject suspicious control characters
            if (rawVersion.any { it.code < 32 || it.code == 127 }) {
                return FirmwareObservation(
                    deviceId = deviceId,
                    rawValue = null,
                    version = FirmwareVersion.Unknown,
                    source = source,
                    observedAtMs = observedAtMs,
                    verificationLevel = verificationLevel,
                    hardwareRevision = hardwareRevision,
                    protocolScope = protocolScope,
                    validationState = FirmwareValidationState.REJECTED_BY_POLICY,
                    limitations = listOf("firmware string contains illegal control characters"),
                )
            }

            val parsed = FirmwareVersion.parse(rawVersion, preferredScheme)
            return FirmwareObservation(
                deviceId = deviceId,
                rawValue = rawVersion,
                version = parsed,
                source = source,
                observedAtMs = observedAtMs,
                verificationLevel = verificationLevel,
                hardwareRevision = hardwareRevision,
                protocolScope = protocolScope,
                validationState = FirmwareValidationState.VALID,
            )
        }
    }
}
