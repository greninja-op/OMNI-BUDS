package com.omnibuds.core.feature

import com.omnibuds.core.config.ConfigurationValue

/**
 * The bounds a feature definition places on acceptable values.
 *
 * Constraints are *validation*, never silent correction: a value outside them is
 * refused with [FeatureErrorCode.INVALID_VALUE], never clamped, unless the feature
 * contract explicitly documents clamping — and no Phase 9 contract does (Phase 9
 * prompt section 13).
 *
 * Every bound here is optional because most bounds are discovered per device, not
 * known universally. A `null` bound means "this definition states no bound", which
 * is different from "the device accepts anything": the device's own rejection is
 * still reported honestly when a write fails. Bounds that *are* stated here must be
 * universal truths (safety limits such as string lengths and entry counts) or be
 * documented as the definition's explicit claim — never a guess about hardware no
 * device has demonstrated.
 *
 * Numeric bounds are [Long]/[Double] rather than [Int]/[Float] so the constraint
 * type never narrows what a device may report; comparison widens the value.
 */
data class FeatureConstraints(
    /** Smallest acceptable integer value, inclusive, or null for no stated bound. */
    val minInt: Long? = null,
    /** Largest acceptable integer value, inclusive, or null for no stated bound. */
    val maxInt: Long? = null,
    /** Required step for integer values (value - minInt must be a multiple), or null. */
    val stepInt: Long? = null,
    /** Smallest acceptable fractional value, inclusive, or null for no stated bound. */
    val minFloat: Double? = null,
    /** Largest acceptable fractional value, inclusive, or null for no stated bound. */
    val maxFloat: Double? = null,
    /** The mode technical names a value may take, or null for no stated set. */
    val allowedModes: Set<String>? = null,
    /** Longest acceptable free-text value, or null for no stated bound. */
    val maxStringLength: Int? = null,
    /**
     * Most entries a structured value, bitmask or custom payload may carry
     * (fields, flags, characters), or null for no stated bound beyond the
     * [ConfigurationValue]-level safety caps.
     */
    val maxEntries: Int? = null,
    /** The flag names a bitmask value may contain, or null for no stated set. */
    val allowedFlags: Set<String>? = null,
) {

    init {
        if (minInt != null && maxInt != null) {
            require(minInt <= maxInt) {
                "integer bounds are contradictory: minInt=$minInt exceeds maxInt=$maxInt"
            }
        }
        if (stepInt != null) {
            require(stepInt > 0) { "integer step must be positive, was $stepInt" }
        }
        if (minFloat != null && maxFloat != null) {
            require(minFloat <= maxFloat) {
                "float bounds are contradictory: minFloat=$minFloat exceeds maxFloat=$maxFloat"
            }
        }
        if (allowedModes != null) {
            require(allowedModes.isNotEmpty()) { "an empty allowedModes set forbids every mode; omit it instead" }
            require(allowedModes.all { it.isNotBlank() }) { "mode names are identities; blank names are not permitted" }
        }
        if (maxStringLength != null) {
            require(maxStringLength > 0) { "maxStringLength must be positive, was $maxStringLength" }
        }
        if (maxEntries != null) {
            require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
        }
        if (allowedFlags != null) {
            require(allowedFlags.isNotEmpty()) { "an empty allowedFlags set forbids every flag; omit it instead" }
            require(allowedFlags.all { it.isNotBlank() }) { "flag names are identities; blank names are not permitted" }
        }
    }

    /**
     * Checks [value] against these constraints.
     *
     * Returns `null` when the value satisfies every stated bound, or a human-readable
     * reason when it does not. The reason is diagnostic text for the
     * [FeatureErrorCode.INVALID_VALUE] error the validator builds; it carries no device
     * identifiers (SEC-LOG-002).
     *
     * Shape is *not* checked here — [FeatureValueType.accepts] owns that — so a value
     * of an unexpected kind is reported as a shape problem by the caller, not silently
     * passed by constraints that do not apply to it.
     */
    fun violationOf(value: ConfigurationValue): String? {
        return when (value) {
            is ConfigurationValue.IntValue -> {
                val v = value.value.toLong()
                if (minInt != null && v < minInt) return "value $v is below the minimum $minInt"
                if (maxInt != null && v > maxInt) return "value $v is above the maximum $maxInt"
                if (stepInt != null) {
                    val base = minInt ?: 0L
                    if ((v - base) % stepInt != 0L) {
                        return "value $v does not honour the step $stepInt from $base"
                    }
                }
                null
            }

            is ConfigurationValue.FloatValue -> {
                val v = value.value
                if (minFloat != null && v < minFloat) return "value $v is below the minimum $minFloat"
                if (maxFloat != null && v > maxFloat) return "value $v is above the maximum $maxFloat"
                null
            }

            is ConfigurationValue.ModeValue -> {
                if (allowedModes != null && value.technicalName !in allowedModes) {
                    return "mode '${value.technicalName}' is not among the allowed modes $allowedModes"
                }
                null
            }

            is ConfigurationValue.StringValue -> {
                if (maxStringLength != null && value.value.length > maxStringLength) {
                    return "text of length ${value.value.length} exceeds the maximum $maxStringLength"
                }
                null
            }

            is ConfigurationValue.StructuredValue -> {
                if (maxEntries != null && value.fields.size > maxEntries) {
                    return "structured value has ${value.fields.size} fields, above the maximum $maxEntries"
                }
                null
            }

            is ConfigurationValue.BitmaskValue -> {
                if (allowedFlags != null) {
                    val unknown = value.flags - allowedFlags
                    if (unknown.isNotEmpty()) {
                        return "unknown flags $unknown; allowed flags are $allowedFlags"
                    }
                }
                if (maxEntries != null && value.flags.size > maxEntries) {
                    return "bitmask has ${value.flags.size} flags, above the maximum $maxEntries"
                }
                null
            }

            is ConfigurationValue.CustomValue -> {
                if (maxEntries != null && value.payload.length > maxEntries) {
                    return "custom payload of length ${value.payload.length} exceeds the maximum $maxEntries"
                }
                null
            }

            is ConfigurationValue.BooleanValue,
            is ConfigurationValue.RangeValue,
            -> null
        }
    }

    companion object {
        /** No stated bounds: the definition leaves every bound to the device. */
        fun none(): FeatureConstraints = FeatureConstraints()
    }
}
