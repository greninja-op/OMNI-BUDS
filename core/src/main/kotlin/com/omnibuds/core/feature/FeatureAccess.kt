package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.state.CapabilityState

/**
 * What the engine may do with a feature right now: read it, write it, both, or
 * neither.
 *
 * Access is *derived*, never declared: it is computed from the capability record
 * Phase 8 established and the momentary availability that discovery pass reported.
 * A UI asks this to decide whether a control may be offered; the engine asks it to
 * decide whether an operation may be attempted (Phase 9 prompt section 11).
 *
 * Two distinctions the derivation keeps apart:
 *
 *  - **Support vs. availability.** A feature may be supported yet momentarily
 *    unusable (a prerequisite is off); that is [UNAVAILABLE], not a support verdict.
 *    The support verdict itself lives in [CapabilityState] and is consulted before
 *    access is ever derived.
 *  - **Readable vs. writable.** [FeatureCapability] already records both affordances
 *    with an `init` block that refuses contradictions; access restates them as one
 *    vocabulary the feature layer reasons about.
 */
enum class FeatureAccess {

    /** Nothing is established about this feature; no operation may be attempted. */
    UNKNOWN,

    /** The device reports the value but accepts no changes. */
    READ_ONLY,

    /**
     * Reserved vocabulary for a control the device accepts without offering reliable
     * read-back.
     *
     * No [CapabilityState] rung maps to it — every controllable rung requires
     * `readable && writable` by [FeatureCapability]'s `init` (ARCH-TERM-003 forbids
     * adding rungs to [CapabilityState]) — so the Phase 9 derivation never produces
     * it. It exists so a future capability-model change can name the concept without
     * inventing a second vocabulary; until then, a feature whose read-back cannot be
     * established is not offered as a control at all.
     */
    WRITE_ONLY,

    /** The value may be read and changed. */
    READ_WRITE,

    /**
     * Established as supported but not usable at this moment — e.g. discovery
     * reported [CapabilityAvailability.UNAVAILABLE], or the capability record itself
     * is a positive absence. Never a claim about what the device implements in
     * general.
     */
    UNAVAILABLE,
}

/**
 * Derives the access for one feature from its capability record and momentary
 * availability.
 *
 * Availability is consulted first: a supported feature that cannot be used *now*
 * is [FeatureAccess.UNAVAILABLE] however strong its support rung is. An
 * [CapabilityState.UNSUPPORTED] record maps to [FeatureAccess.UNAVAILABLE] as well —
 * the engine's capability gate rejects unsupported features before access is
 * consulted, so this arm exists only to keep the function total, and it must never
 * be read as "the device might support this later". The support verdict stays in
 * the capability record, where it belongs.
 */
fun accessOf(capability: FeatureCapability, availability: CapabilityAvailability): FeatureAccess {
    if (
        availability == CapabilityAvailability.UNAVAILABLE ||
        availability == CapabilityAvailability.TEMPORARILY_UNAVAILABLE
    ) {
        return FeatureAccess.UNAVAILABLE
    }
    return when (capability.state) {
        CapabilityState.UNKNOWN -> FeatureAccess.UNKNOWN
        CapabilityState.UNSUPPORTED -> FeatureAccess.UNAVAILABLE
        CapabilityState.READ_ONLY -> FeatureAccess.READ_ONLY
        CapabilityState.SUPPORTED_VOLATILE,
        CapabilityState.SUPPORTED_PERSISTENT,
        CapabilityState.PERSISTENCE_VERIFIED,
        -> FeatureAccess.READ_WRITE
    }
}

/** Whether an operation of [type] may be attempted under this access. */
fun FeatureAccess.permits(type: FeatureOperationType): Boolean = when (this) {
    FeatureAccess.READ_WRITE -> true
    FeatureAccess.READ_ONLY -> type == FeatureOperationType.READ
    FeatureAccess.WRITE_ONLY -> type != FeatureOperationType.READ
    FeatureAccess.UNKNOWN,
    FeatureAccess.UNAVAILABLE,
    -> false
}
