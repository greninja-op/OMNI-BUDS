package com.omnibuds.core.extension

import com.omnibuds.core.state.VerificationLevel

/**
 * Typed vendor feature values.
 *
 * Phase 23 (OB-P23-REQ-005): closed set of value shapes. No unvalidated
 * objects; no silent coercion between incompatible types.
 */
sealed interface VendorFeatureValue {
    data class BooleanValue(val value: Boolean) : VendorFeatureValue
    data class IntValue(val value: Int) : VendorFeatureValue
    data class FloatValue(val value: Double) : VendorFeatureValue
    data class EnumValue(val value: String) : VendorFeatureValue
    data class StructuredValue(val fields: Map<String, VendorFeatureValue>) : VendorFeatureValue
    data class ListValue(val items: List<VendorFeatureValue>) : VendorFeatureValue
}

/**
 * The declared type of a feature value.
 */
enum class VendorValueType {
    BOOLEAN,
    INT,
    FLOAT,
    ENUM,
    STRUCTURED,
    LIST,
}

/**
 * Validation constraints for a feature value.
 *
 * Phase 23 (OB-P23-REQ-006): deterministic, side-effect-free validation.
 */
data class ValueConstraints(
    val minInt: Int? = null,
    val maxInt: Int? = null,
    val minFloat: Double? = null,
    val maxFloat: Double? = null,
    val allowedEnumValues: Set<String> = emptySet(),
    val intStep: Int? = null,
    val floatStep: Double? = null,
    val maxListLength: Int? = null,
    /** Required field names for structured values. */
    val requiredFields: Set<String> = emptySet(),
    /** Allowed field names for structured values. Null = any. */
    val allowedFields: Set<String>? = null,
    /** Cross-field constraints, described textually. */
    val crossFieldConstraints: List<String> = emptyList(),
) {
    init {
        if (minInt != null && maxInt != null) {
            require(minInt <= maxInt) { "minInt must be <= maxInt" }
        }
        if (minFloat != null && maxFloat != null) {
            require(minFloat <= maxFloat) { "minFloat must be <= maxFloat" }
        }
    }
}

/**
 * A vendor feature definition.
 *
 * Phase 23 (OB-P23-REQ-004): typed schema for vendor-specific feature
 * metadata and values.
 */
data class VendorFeatureDefinition(
    val id: VendorFeatureId,
    val extensionId: VendorExtensionId,
    /** Display-independent canonical name. */
    val canonicalName: String,
    val category: String,
    val valueType: VendorValueType,
    val constraints: ValueConstraints = ValueConstraints(),
    /** Documented default, or null when unknown. */
    val defaultValue: VendorFeatureValue? = null,
    /** Whether the feature can be read. */
    val readable: Boolean = false,
    /** Whether the feature can be written. */
    val writable: Boolean = false,
    /** Feature IDs this depends on. */
    val dependencies: Set<VendorFeatureId> = emptySet(),
    /** Feature IDs this conflicts with. */
    val conflicts: Set<VendorFeatureId> = emptySet(),
    /** Required protocol operation IDs. */
    val requiredOperations: Set<String> = emptySet(),
    /** Applicable device-model stable IDs. */
    val applicableModels: Set<String> = emptySet(),
    /** Firmware constraints, e.g. ">= 3.0". Empty = unknown. */
    val firmwareConstraints: List<String> = emptyList(),
    /** Whether read-back is supported. */
    val readBackSupported: Boolean = false,
    /** Whether persistence verification is possible. */
    val persistenceVerifiable: Boolean = false,
    val evidenceIds: List<String> = emptyList(),
    val verification: VerificationLevel = VerificationLevel.INFERRED,
    val deprecated: Boolean = false,
) {
    init {
        require(canonicalName.isNotBlank()) { "canonical name must not be blank" }
        // Default value must match the declared type.
        defaultValue?.let {
            require(validateValue(it).isValid()) {
                "default value does not satisfy constraints"
            }
        }
    }

    /**
     * Validate a value against this definition's type and constraints.
     * Deterministic and side-effect-free.
     */
    fun validateValue(value: VendorFeatureValue): ValueValidationResult {
        // Type check.
        val typeOk = when (valueType) {
            VendorValueType.BOOLEAN -> value is VendorFeatureValue.BooleanValue
            VendorValueType.INT -> value is VendorFeatureValue.IntValue
            VendorValueType.FLOAT -> value is VendorFeatureValue.FloatValue
            VendorValueType.ENUM -> value is VendorFeatureValue.EnumValue
            VendorValueType.STRUCTURED -> value is VendorFeatureValue.StructuredValue
            VendorValueType.LIST -> value is VendorFeatureValue.ListValue
        }
        if (!typeOk) {
            return ValueValidationResult.Invalid("expected $valueType, got ${value::class.simpleName}")
        }

        return when (value) {
            is VendorFeatureValue.IntValue -> validateInt(value.value)
            is VendorFeatureValue.FloatValue -> validateFloat(value.value)
            is VendorFeatureValue.EnumValue -> validateEnum(value.value)
            is VendorFeatureValue.ListValue -> validateList(value.items)
            is VendorFeatureValue.StructuredValue -> validateStructured(value.fields)
            is VendorFeatureValue.BooleanValue -> ValueValidationResult.Valid
        }
    }

    private fun validateInt(v: Int): ValueValidationResult {
        constraints.minInt?.let { if (v < it) return invalid("below minimum $it") }
        constraints.maxInt?.let { if (v > it) return invalid("above maximum $it") }
        constraints.intStep?.let { step ->
            val base = constraints.minInt ?: 0
            if ((v - base) % step != 0) return invalid("not a multiple of step $step from $base")
        }
        return ValueValidationResult.Valid
    }

    private fun validateFloat(v: Double): ValueValidationResult {
        constraints.minFloat?.let { if (v < it) return invalid("below minimum $it") }
        constraints.maxFloat?.let { if (v > it) return invalid("above maximum $it") }
        return ValueValidationResult.Valid
    }

    private fun validateEnum(v: String): ValueValidationResult {
        if (constraints.allowedEnumValues.isNotEmpty() && v !in constraints.allowedEnumValues) {
            return invalid("not in allowed values ${constraints.allowedEnumValues}")
        }
        return ValueValidationResult.Valid
    }

    private fun validateList(items: List<VendorFeatureValue>): ValueValidationResult {
        constraints.maxListLength?.let {
            if (items.size > it) return invalid("list exceeds maximum length $it")
        }
        return ValueValidationResult.Valid
    }

    private fun validateStructured(fields: Map<String, VendorFeatureValue>): ValueValidationResult {
        val missing = constraints.requiredFields - fields.keys
        if (missing.isNotEmpty()) {
            return invalid("missing required fields $missing")
        }
        constraints.allowedFields?.let { allowed ->
            val unexpected = fields.keys - allowed
            if (unexpected.isNotEmpty()) {
                return invalid("unexpected fields $unexpected")
            }
        }
        return ValueValidationResult.Valid
    }

    private fun invalid(reason: String) = ValueValidationResult.Invalid(reason)
}

/** The result of validating a feature value. */
sealed interface ValueValidationResult {
    data object Valid : ValueValidationResult
    data class Invalid(val reason: String) : ValueValidationResult

    fun isValid(): Boolean = this is Valid
}
