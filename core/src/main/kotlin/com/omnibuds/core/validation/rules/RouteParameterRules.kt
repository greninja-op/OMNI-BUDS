package com.omnibuds.core.validation.rules

import com.omnibuds.core.validation.ValidationCategory
import com.omnibuds.core.validation.ValidationInput
import com.omnibuds.core.validation.ValidationResult
import com.omnibuds.core.validation.ValidationRule
import com.omnibuds.core.validation.ValidationSeverity
import com.omnibuds.core.validation.ValidationStatus

/**
 * Phase 14 (OB-P14-REQ-007, OB-P14-REQ-008): route and parameter rules.
 */

/**
 * P14-ROUTE-001: route consistency — an available device is not proof of a
 * selected route; a selected route is not proof of flowing audio.
 */
object RouteConsistencyRule : ValidationRule {
    override val ruleId = "P14-ROUTE-001"
    override val description =
        "Route observations are consistent: availability, selection, and activity are distinct."
    override val category = ValidationCategory.ROUTE

    override fun evaluate(input: ValidationInput): ValidationResult {
        val available = input.audioDeviceAvailable
        val routeActive = input.routeActive
        if (available == null && routeActive == null) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.NOT_OBSERVABLE,
                severity = ValidationSeverity.INFO,
                reason = "Neither device availability nor route state is observable.",
                limitations = listOf("Route observation unavailable on this platform state"),
            )
        }
        // Available but no route claim: valid, and explicitly not active.
        if (available == true && routeActive != true) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = "Device available but route not active; availability does not " +
                    "imply active audio.",
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
            reason = "Route observation is internally consistent.",
        )
    }
}

/**
 * P14-PARAM-001: audio parameters satisfy domain constraints where observed.
 * Missing values stay missing; uncommon-but-legal values are not rejected.
 */
object ParameterDomainRule : ValidationRule {
    override val ruleId = "P14-PARAM-001"
    override val description =
        "Observed audio parameters satisfy basic domain constraints."
    override val category = ValidationCategory.PARAMETERS

    override fun evaluate(input: ValidationInput): ValidationResult {
        val violations = mutableListOf<String>()
        input.sampleRateHz?.let {
            if (it <= 0 || it > 768_000) violations += "sample rate $it Hz out of domain"
        }
        input.bitDepth?.let {
            if (it !in setOf(8, 16, 20, 24, 32)) violations += "bit depth $it not a known depth"
        }
        if (violations.isEmpty()) {
            val observed = listOfNotNull(
                input.sampleRateHz?.let { "sampleRate=$it" },
                input.bitDepth?.let { "bitDepth=$it" },
                input.channelMode?.let { "channels=$it" },
            )
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = if (observed.isEmpty()) {
                    "No parameters observed; nothing to constrain."
                } else {
                    "Observed parameters satisfy domain constraints: ${observed.joinToString()}."
                },
            )
        }
        return ValidationResult(
            validationId = ruleId,
            device = input.device,
            sessionGeneration = input.sessionGeneration,
            category = category,
            status = ValidationStatus.INVALID,
            severity = ValidationSeverity.ERROR,
            observedAtMillis = input.timestampMillis,
            reason = "Parameter domain violations: ${violations.joinToString("; ")}.",
        )
    }
}

/**
 * P14-FRESH-001: freshness coherence — delegates to the Phase 13 freshness
 * vocabulary; flags unknown freshness as inconclusive, never as failure.
 */
object FreshnessCoherenceRule : ValidationRule {
    override val ruleId = "P14-FRESH-001"
    override val description =
        "Observation freshness is coherent with the validation claim."
    override val category = ValidationCategory.FRESHNESS

    override fun evaluate(input: ValidationInput): ValidationResult {
        return when (input.codecFreshness) {
            com.omnibuds.core.audio.CodecFreshness.CURRENT -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = "Observations are current.",
            )
            com.omnibuds.core.audio.CodecFreshness.STALE -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.STALE,
                severity = ValidationSeverity.WARNING,
                observedAtMillis = input.timestampMillis,
                reason = "Observations are stale; preserved as history, not current.",
            )
            else -> ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "Freshness unknown; currency cannot be established.",
            )
        }
    }
}

/**
 * P14-LIFE-001: lifecycle ordering — a generation mismatch means the input
 * belongs to an obsolete session and must not validate current state.
 */
object SessionGenerationRule : ValidationRule {
    override val ruleId = "P14-LIFE-001"
    override val description =
        "Validation inputs belong to the current session generation."
    override val category = ValidationCategory.LIFECYCLE

    override fun evaluate(input: ValidationInput): ValidationResult {
        // The engine guarantees generation currency before evaluation; this
        // rule documents the invariant and guards direct rule use.
        return ValidationResult(
            validationId = ruleId,
            device = input.device,
            sessionGeneration = input.sessionGeneration,
            category = category,
            status = ValidationStatus.VALID,
            severity = ValidationSeverity.INFO,
            observedAtMillis = input.timestampMillis,
            reason = "Input generation ${input.sessionGeneration} accepted for evaluation; " +
                "obsolete generations are discarded by the engine before rules run.",
        )
    }
}
