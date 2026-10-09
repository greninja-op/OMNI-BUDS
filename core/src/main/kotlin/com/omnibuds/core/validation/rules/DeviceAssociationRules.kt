package com.omnibuds.core.validation.rules

import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.validation.ValidationCategory
import com.omnibuds.core.validation.ValidationInput
import com.omnibuds.core.validation.ValidationResult
import com.omnibuds.core.validation.ValidationRule
import com.omnibuds.core.validation.ValidationSeverity
import com.omnibuds.core.validation.ValidationStatus

/**
 * Phase 14 (OB-P14-REQ-004): device/session association rules.
 */

/**
 * P14-DEVICE-001: the quality state's device must match the input device.
 * Cross-device attribution is a hard ERROR.
 */
object DeviceIdentityMatchRule : ValidationRule {
    override val ruleId = "P14-DEVICE-001"
    override val description =
        "Audio observations are attributed to the device they were collected for."
    override val category = ValidationCategory.DEVICE_ASSOCIATION

    override fun evaluate(input: ValidationInput): ValidationResult {
        val quality = input.qualityState
        if (quality == null) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "No quality state available; device attribution cannot be checked.",
                limitations = listOf("Phase 13 quality state absent"),
            )
        }
        return if (quality.device == input.device) {
            ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                evidenceReferences = listOf("AudioQualityState.device"),
                observedAtMillis = input.timestampMillis,
                reason = "Quality state device matches the validated device.",
            )
        } else {
            ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INVALID,
                severity = ValidationSeverity.CRITICAL,
                evidenceReferences = listOf("AudioQualityState.device"),
                observedAtMillis = input.timestampMillis,
                reason = "Quality state belongs to a different device; observations from " +
                    "one device must never overwrite another's state.",
                suggestedAction = "Re-correlate the observation source to the correct DeviceIdentity.",
            )
        }
    }
}

/**
 * P14-DEVICE-002: a disconnected device must not retain a current active route.
 */
object DisconnectedRouteRule : ValidationRule {
    override val ruleId = "P14-DEVICE-002"
    override val description =
        "A disconnected device must not present a current active audio route."
    override val category = ValidationCategory.DEVICE_ASSOCIATION

    override fun evaluate(input: ValidationInput): ValidationResult {
        val connected = input.transportConnected
        val routeActive = input.routeActive
        if (connected == null) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "Transport connection state unknown; route validity cannot be checked.",
            )
        }
        if (!connected && routeActive == true) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INVALID,
                severity = ValidationSeverity.CRITICAL,
                observedAtMillis = input.timestampMillis,
                reason = "Transport reports disconnected but the route is marked active; " +
                    "a disconnected device cannot carry a current active route.",
                suggestedAction = "Invalidate the route and mark associated state stale.",
            )
        }
        if (!connected) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = "Device disconnected and no active route is claimed.",
            )
        }
        return ValidationResult(
            validationId = ruleId,
            device = input.device,
            sessionGeneration = input.sessionGeneration,
            category = category,
            status = ValidationStatus.VALID,
            severity = ValidationSeverity.INFO,
            observedAtMillis = input.timestampMillis,
            reason = "Device connected; route state is plausible.",
        )
    }
}

/**
 * P14-DEVICE-003: stale codec observations must not be presented as current
 * for the active session generation.
 */
object StaleCodecRule : ValidationRule {
    override val ruleId = "P14-DEVICE-003"
    override val description =
        "A stale codec observation must not masquerade as the current codec."
    override val category = ValidationCategory.DEVICE_ASSOCIATION

    override fun evaluate(input: ValidationInput): ValidationResult {
        return when (input.codecFreshness) {
            CodecFreshness.STALE -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.STALE,
                severity = ValidationSeverity.WARNING,
                evidenceReferences = listOf("CodecRuntimeState.freshness"),
                observedAtMillis = input.timestampMillis,
                reason = "Codec observation is stale; it is preserved as history " +
                    "but must not be treated as current.",
                suggestedAction = "Re-observe the codec state before presenting it as current.",
            )
            CodecFreshness.CURRENT -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = "Codec observation is current.",
            )
            else -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "Codec freshness unknown; currency cannot be established.",
            )
        }
    }
}
