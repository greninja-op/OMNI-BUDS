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
}
