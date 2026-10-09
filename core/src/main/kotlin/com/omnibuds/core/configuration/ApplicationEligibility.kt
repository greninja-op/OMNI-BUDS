package com.omnibuds.core.configuration

import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState

/**
 * Determines whether a saved preference is eligible for application to hardware.
 *
 * Phase 17 (OB-P17-REQ-008): a saved preference is never automatically
 * applied. Eligibility requires: known device, supported capability,
 * writable control, and valid parameter constraints. Each check is explicit;
 * any failure yields a structured reason — never silent application.
 */
object ApplicationEligibility {

    /**
     * Check whether a preference may be applied.
     *
     * @param preferenceKey the configuration key (e.g. "anc.mode").
     * @param value the saved preference value.
     * @param feature the hardware feature this preference targets, or null
     * when the preference is app-only (no hardware application).
     * @param capability the discovered capability, or null when unknown.
     */
    fun check(
        preferenceKey: String,
        value: ConfigurationValue,
        feature: FeatureId?,
        capability: FeatureCapability?,
    ): Eligibility {
        // App-only preferences (no hardware target) are always "eligible" —
        // there is nothing to apply.
        if (feature == null) {
            return Eligibility.Eligible("app-only preference; no hardware application")
        }
        if (capability == null) {
            return Eligibility.NotEligible(
                "capability unknown for $feature; refusing to apply '$preferenceKey'",
            )
        }
        if (capability.feature != feature) {
            return Eligibility.NotEligible("capability mismatch for $feature")
        }
        when (capability.state) {
            CapabilityState.UNKNOWN ->
                return Eligibility.NotEligible("$feature capability unknown; not applying '$preferenceKey'")
            CapabilityState.UNSUPPORTED ->
                return Eligibility.NotEligible("$feature unsupported; not applying '$preferenceKey'")
            CapabilityState.READ_ONLY ->
                return Eligibility.NotEligible("$feature is read-only; not applying '$preferenceKey'")
            else -> { /* supported states continue */ }
        }
        if (!capability.writable) {
            return Eligibility.NotEligible("$feature has no write path; not applying '$preferenceKey'")
        }
        // Parameter shape check: the value must be structurally valid.
        // Deep constraint validation belongs to the feature engine.
        if (!isPlausible(value)) {
            return Eligibility.NotEligible("preference '$preferenceKey' has invalid value shape")
        }
        return Eligibility.Eligible("$feature is supported and writable")
    }

    private fun isPlausible(value: ConfigurationValue): Boolean = when (value) {
        is ConfigurationValue.BooleanValue -> true
        is ConfigurationValue.IntValue -> true
        is ConfigurationValue.StringValue -> value.value.isNotBlank()
        is ConfigurationValue.ModeValue -> true // validated at construction
        is ConfigurationValue.FloatValue -> true // NaN/inf refused at construction
        is ConfigurationValue.RangeValue -> true
        is ConfigurationValue.StructuredValue -> value.fields.isNotEmpty()
        is ConfigurationValue.BitmaskValue -> true
        is ConfigurationValue.CustomValue -> value.payload.isNotBlank()
    }
}

/** Whether a saved preference may be applied to hardware. */
sealed interface Eligibility {
    /** Application may be attempted (still requires the control path). */
    data class Eligible(val reason: String) : Eligibility

    /** Application must not be attempted. */
    data class NotEligible(val reason: String) : Eligibility
}
