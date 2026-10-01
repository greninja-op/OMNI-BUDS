package com.omnibuds.core.config

import com.omnibuds.core.common.FeatureId

/**
 * Configuration addressed to one device: what the earbud's own settings should be,
 * or what they were read as.
 *
 * ANC mode, equalizer bands, gesture mappings — these are values that live in, or
 * travel to, the hardware, keyed by feature identity rather than by brand conditional
 * or by raw characteristic.
 *
 * **The rule that matters: an entry here is an intent, not a result.**
 * Putting `noise-control.anc -> ModeValue("adaptive", "Adaptive")` into this map
 * records that the user or the app *wants* that mode. It is only an intention until a
 * protocol phase writes it and reads it back against real hardware. Verification of a
 * write belongs to the protocol/session layer and is reported through
 * `com.omnibuds.core.state.CapabilityState` and `VerificationLevel`; nothing in this
 * type can express "verified", and it must not start to. A device that was never
 * reached still holds the same desired entries, because desire is not evidence.
 *
 * This type also says nothing about the application (see [ApplicationConfiguration])
 * and nothing about how the channel is addressed (see [ProtocolConfiguration]).
 *
 * [entries] is a snapshot. Construction through [of] copies the incoming map, so a
 * caller holding a `MutableMap` cannot change a configuration value after the fact by
 * mutating their own copy; passing a map straight to the primary constructor is only
 * sound when that map is already an immutable snapshot.
 */
data class DeviceConfiguration(
    /** Desired or observed device-side settings, keyed by stable feature identity. */
    val entries: Map<FeatureId, ConfigurationValue>,
) {
    /**
     * The recorded value for [feature], or null when nothing was recorded.
     *
     * Null means "no intent was expressed for this feature". It never falls back to a
     * default value, because a default would be an invented device setting — and an
     * invented setting that is later written to hardware is how a config layer ends up
     * claiming support it never earned. Absence here is not `UNSUPPORTED` either; it is
     * simply not asked for.
     */
    fun valueFor(feature: FeatureId): ConfigurationValue? = entries[feature]

    companion object {
        /** A configuration that expresses no intent at all. */
        fun empty(): DeviceConfiguration = DeviceConfiguration(emptyMap())

        /** Defensive-copy construction: later mutation of [entries] cannot reach this value. */
        fun of(entries: Map<FeatureId, ConfigurationValue>): DeviceConfiguration =
            DeviceConfiguration(entries.entries.associate { entry -> entry.key to entry.value })
    }
}
