package com.omnibuds.core.config

/**
 * One typed configuration value.
 *
 * A value is a plain Kotlin data value: it carries no Android type, no storage
 * encoding and no protocol framing. Deliberate Phase 1 limits, not oversights:
 *
 *  - **No serialisation.** Storage formats are decided by the Phase 1 serialization
 *    decision in `docs/phases/phase-1/decisions.md` (execution prompt section 38),
 *    which is deferred out of the contract phase. Nothing here declares annotations
 *    for a format that has not been chosen, because a wrong annotation on a persisted
 *    value is a migration problem, not a typing problem.
 *  - **No `Any` escape hatch.** Being a sealed interface, the set of value shapes is
 *    closed and exhaustive `when` over it is a compile-time check. A configuration
 *    value is never a bare `Object`, a map or a JSON node.
 *
 * Unknown stays unknown at this layer by *omitting the entry* rather than by storing
 * a placeholder value here (specs.md section 2.2 tiering: `0`, `-1` and empty string
 * are never stand-ins for "not reported"). Absence of a key is expressed by the
 * holder, never by a fake [ConfigurationValue].
 */
sealed interface ConfigurationValue {

    /** A true/false setting, e.g. an ANC on/off style flag on the device side. */
    data class BooleanValue(val value: Boolean) : ConfigurationValue

    /** A whole-number setting, e.g. an EQ band gain in decibels or a level index. */
    data class IntValue(val value: Int) : ConfigurationValue

    /** A free-text setting whose content is not a mode, e.g. an equalizer preset name. */
    data class StringValue(val value: String) : ConfigurationValue

    /**
     * A setting chosen from a named set of modes, e.g. ANC `adaptive` vs `off`.
     *
     * The machine key and the human label are kept apart (specs.md section 1.3):
     * [technicalName] is the stable identity used by protocols and storage, and
     * [displayName] is presentation only. Renaming a display label is a copy change;
     * renaming a technical name is a protocol change.
     */
    data class ModeValue(val technicalName: String, val displayName: String) : ConfigurationValue {
        init {
            require(technicalName.isNotBlank()) { "a mode technical name is the identity; it cannot be blank" }
            require(displayName.isNotBlank()) { "a mode display name cannot be blank; omit the entry instead" }
        }
    }

    /**
     * A fractional setting, e.g. a transparency level expressed as a ratio, or a sidetone
     * amount the device reports on a continuous scale.
     *
     * NaN and infinities are refused at construction: they are the classic smuggling
     * route for "unknown" into a numeric field, and an unmeasured value stays unknown
     * by omitting the entry rather than by storing a non-number here (specs.md section
     * 2.2 tiering, ADR-P0-016).
     */
    data class FloatValue(val value: Double) : ConfigurationValue {
        init {
            require(!value.isNaN() && !value.isInfinite()) {
                "a float configuration value must be finite; NaN and infinities are not stand-ins for unknown"
            }
        }
    }

    /**
     * A bounded interval, e.g. the frequency span one EQ band covers, or the range a
     * level control sweeps.
     *
     * This is a *value*, not a constraint: it says "the device reports the interval
     * [min, max]", where a [com.omnibuds.core.feature.FeatureConstraints] says "a write
     * must fall inside [min, max]". The two must not be confused.
     */
    data class RangeValue(val min: Double, val max: Double) : ConfigurationValue {
        init {
            require(!min.isNaN() && !max.isNaN() && !min.isInfinite() && !max.isInfinite()) {
                "a range bound must be finite; non-numbers are not stand-ins for unknown"
            }
            require(min <= max) {
                "a range's min ($min) must not exceed its max ($max)"
            }
        }
    }

    /** One named field inside a [StructuredValue], e.g. one EQ band's frequency. */
    data class StructuredField(val name: String, val value: ConfigurationValue) {
        init {
            require(name.isNotBlank()) { "a structured field name is the identity; it cannot be blank" }
        }
    }

    /**
     * A composite value made of named fields, e.g. a graphic-EQ setting as a list of
     * bands, or one gesture assignment as gesture + side + action.
     *
     * Field names are unique within the value so that two fields cannot disagree about
     * the same name, and the entry count is bounded so a malformed device response
     * cannot turn one value into an unbounded allocation (Phase 9 security review).
     * Nesting is allowed — a field's value may itself be structured — but each level
     * enforces the same bound.
     */
    data class StructuredValue(val fields: List<StructuredField>) : ConfigurationValue {
        init {
            require(fields.isNotEmpty()) {
                "a structured value with no fields says nothing; omit the entry instead"
            }
            require(fields.size <= MAX_STRUCTURED_FIELDS) {
                "a structured value carries at most $MAX_STRUCTURED_FIELDS fields, got ${fields.size}"
            }
            val names = fields.map { it.name }
            require(names.size == names.toSet().size) {
                "a structured value must not name two fields alike: $names"
            }
        }

        /** The field called [name], or null when this value has no such field. */
        operator fun get(name: String): ConfigurationValue? =
            fields.firstOrNull { it.name == name }?.value

        companion object {
            /** Bound on one structured value's field count (Phase 9 security review). */
            const val MAX_STRUCTURED_FIELDS: Int = 64
        }
    }

    /**
     * A set of named flags, e.g. the set of input sides a gesture is armed on, or the
     * set of optional behaviours currently enabled.
     *
     * An empty set is refused: "no flags" is expressed by omitting the entry, not by an
     * empty value that a reader must interpret (specs.md section 2.2 tiering).
     */
    data class BitmaskValue(val flags: Set<String>) : ConfigurationValue {
        init {
            require(flags.isNotEmpty()) {
                "a bitmask with no flags says nothing; omit the entry instead"
            }
            require(flags.size <= MAX_FLAGS) {
                "a bitmask carries at most $MAX_FLAGS flags, got ${flags.size}"
            }
            require(flags.all { it.isNotBlank() }) {
                "bitmask flags are identities; blank flags are not permitted"
            }
        }

        companion object {
            /** Bound on one bitmask's flag count (Phase 9 security review). */
            const val MAX_FLAGS: Int = 64
        }
    }

    /**
     * An opaque vendor-defined payload carried as text, e.g. a manufacturer-specific
     * mode blob the generic engine must transport without interpreting.
     *
     * Opaque does not mean unbounded: the length cap keeps one value from becoming a
     * memory problem, and the engine never parses the content — interpretation belongs
     * to the vendor's own adapter in a later phase. This is never protocol bytes; see
     * [com.omnibuds.core.capability.VendorExtension] for the same rule at the
     * capability layer.
     */
    data class CustomValue(val payload: String) : ConfigurationValue {
        init {
            require(payload.isNotBlank()) {
                "a custom value with no payload says nothing; omit the entry instead"
            }
            require(payload.length <= MAX_CUSTOM_LENGTH) {
                "a custom value carries at most $MAX_CUSTOM_LENGTH characters, got ${payload.length}"
            }
        }

        companion object {
            /** Bound on one custom value's length (Phase 9 security review). */
            const val MAX_CUSTOM_LENGTH: Int = 4096
        }
    }
}
