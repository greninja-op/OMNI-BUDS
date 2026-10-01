package com.omnibuds.core.capability

/**
 * The functional area a feature belongs to.
 *
 * Grouping is by what the feature *does*, never by who makes it: a brand never appears
 * in this vocabulary, because `if (sony) … if (bose) …` is the pattern the capability
 * model exists to remove (Phase 1 prompt sections 20 and 21, ADR-P0-007). Categories
 * organise discovery results, diagnostics and later presentation grouping; they carry no
 * claim about whether any device supports anything, and adding a category changes
 * capability semantics enough to require an ADR (specs section 1.3).
 *
 * [VENDOR] is the odd one out and is deliberate: it is the bucket for functionality that
 * is real but belongs to no universal area. A feature in [VENDOR] is addressed through
 * [VendorExtension] under a `vendor.<vendor>.<feature>` identity, so it can never be
 * mistaken for — or quietly redefine — a core feature.
 */
enum class FeatureCategory {
    /** ANC, transparency and their adaptive or environment-aware variants. */
    NOISE_CONTROL,

    /** Presets, bands and any shaping applied to the signal. */
    EQUALIZATION,

    /** Touch and physical controls, and the actions bound to them. */
    INPUT,

    /** Wear, in-ear, motion and head-position sensing. */
    SENSING,

    /** Link-level behaviour: multipoint, latency modes, device handover. */
    CONNECTIVITY,

    /** Energy: earbud and case charge, charging state, power-saving behaviour. */
    POWER,

    /** Rendering and voice-path qualities that are neither EQ nor noise control. */
    AUDIO_QUALITY,

    /** Version and identity of what is running on the device. */
    FIRMWARE,

    /** Manufacturer-specific functionality with no universal equivalent. */
    VENDOR,
}
