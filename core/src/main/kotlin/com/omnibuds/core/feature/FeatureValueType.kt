package com.omnibuds.core.feature

import com.omnibuds.core.config.ConfigurationValue

/**
 * The shape of value a hardware feature takes.
 *
 * This is vocabulary, not a support claim: naming a shape never says any device
 * implements it. Each member names the [ConfigurationValue] subtype that carries a
 * value of that shape, so the engine can check "is this value even the right kind"
 * before it checks constraints, ranges or dependencies (Phase 9 prompt sections 8
 * and 13).
 *
 * The shapes are deliberately generic. A vendor-specific meaning rides inside a
 * generic shape — a mode name, a structured band list, an opaque custom payload —
 * rather than in a per-vendor value type, which is what keeps the engine from
 * growing `if (sony)` branches (ADR-P0-007).
 */
enum class FeatureValueType {

    /** True/false, carried by [ConfigurationValue.BooleanValue]. */
    BOOLEAN,

    /** One of a named set, carried by [ConfigurationValue.ModeValue]. */
    ENUM,

    /** A whole number, carried by [ConfigurationValue.IntValue]. */
    INTEGER,

    /** A fractional number, carried by [ConfigurationValue.FloatValue]. */
    FLOAT,

    /** A bounded interval, carried by [ConfigurationValue.RangeValue]. */
    RANGE,

    /** Free text, carried by [ConfigurationValue.StringValue]. */
    STRING,

    /** Named fields, carried by [ConfigurationValue.StructuredValue]. */
    STRUCTURED,

    /** A set of named flags, carried by [ConfigurationValue.BitmaskValue]. */
    BITMASK,

    /** An opaque vendor payload, carried by [ConfigurationValue.CustomValue]. */
    CUSTOM,
}

/**
 * Whether [value] is the right *kind* for this shape — before any constraint,
 * range or semantic check.
 *
 * Shape is checked first because a value of the wrong kind is a modelling error,
 * not a value the constraints could ever accept: an out-of-range integer is
 * [com.omnibuds.core.feature.FeatureErrorCode.INVALID_VALUE], a string where an
 * integer belongs is a malformed value.
 */
fun FeatureValueType.accepts(value: ConfigurationValue): Boolean = when (this) {
    FeatureValueType.BOOLEAN -> value is ConfigurationValue.BooleanValue
    FeatureValueType.ENUM -> value is ConfigurationValue.ModeValue
    FeatureValueType.INTEGER -> value is ConfigurationValue.IntValue
    FeatureValueType.FLOAT -> value is ConfigurationValue.FloatValue
    FeatureValueType.RANGE -> value is ConfigurationValue.RangeValue
    FeatureValueType.STRING -> value is ConfigurationValue.StringValue
    FeatureValueType.STRUCTURED -> value is ConfigurationValue.StructuredValue
    FeatureValueType.BITMASK -> value is ConfigurationValue.BitmaskValue
    FeatureValueType.CUSTOM -> value is ConfigurationValue.CustomValue
}
