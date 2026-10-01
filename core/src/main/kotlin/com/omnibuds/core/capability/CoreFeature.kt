package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId

/**
 * The core feature identities OmniBuds reasons about universally.
 *
 * This is the one registry of universal features: a stable string held here and
 * referenced everywhere else, so that no layer below the UI has to invent its own name
 * for the same thing, and so that `if (sony) … if (bose) …` never becomes how a control
 * is found (Phase 1 prompt sections 14 and 20, specs section 1.5, ADR-P0-007).
 *
 * Two decisions are load-bearing:
 *
 *  - **Namespaces are functional, never brands.** Every id is `FeatureId.of(namespace, name)`
 *    under an area name such as `noise-control` or `power`. Nothing in this object is
 *    vendor-scoped, and nothing here may be renamed to accommodate one manufacturer's
 *    version of a feature — a manufacturer-specific behaviour that has no universal
 *    equivalent belongs in a [VendorExtension] under `vendor.<vendor>.<feature>`.
 *  - **Listing a feature is not supporting it.** These identities exist so discovery has
 *    something to answer questions *about*. Presence here plus no [FeatureCapability]
 *    record in a device's [DeviceCapabilities] means `UNKNOWN`, and the capability model
 *    refuses to convert that into `UNSUPPORTED` or into any `SUPPORTED_*` state
 *    (PROTO-CAP-004, master section 53). Nothing in this file claims that any earbud,
 *    headset or brand does anything.
 *
 * The [definitions] list is documentation-shaped metadata for the same identities; the
 * `init` block below asserts that the two views cannot drift apart.
 *
 * This catalogue is deliberately not exhaustive of everything hardware can do — adaptive
 * ANC levels, environmental modes, per-band EQ and gesture assignments are feature
 * *values* discovered per device, not new core identities.
 */
object CoreFeature {

    /** Active noise control: the device's own cancellation of outside sound. */
    val ANC: FeatureId = FeatureId.of(Namespace.NOISE_CONTROL, "anc")

    /** Pass-through of outside sound, deliberately not modelled as "ANC turned off". */
    val TRANSPARENCY: FeatureId = FeatureId.of(Namespace.NOISE_CONTROL, "transparency")

    /** Noise control whose behaviour the device adapts on its own. */
    val ADAPTIVE_ANC: FeatureId = FeatureId.of(Namespace.NOISE_CONTROL, "adaptive-anc")

    /** Tone shaping, whether preset, band or parametric. */
    val EQUALIZER: FeatureId = FeatureId.of(Namespace.EQUALIZATION, "equalizer")

    /** Touch and physical gesture controls and their assignable actions. */
    val GESTURES: FeatureId = FeatureId.of(Namespace.INPUT, "gestures")

    /** In-ear presence detection, the basis for auto-pause and wear-aware features. */
    val WEAR_DETECTION: FeatureId = FeatureId.of(Namespace.SENSING, "wear-detection")

    /** Head-relative audio placement. */
    val SPATIAL_AUDIO: FeatureId = FeatureId.of(Namespace.AUDIO_QUALITY, "spatial-audio")

    /** Sensing of head position or movement, whether or not the device uses it for audio. */
    val HEAD_TRACKING: FeatureId = FeatureId.of(Namespace.SENSING, "head-tracking")

    /** Simultaneous connections to more than one source device. */
    val MULTIPOINT: FeatureId = FeatureId.of(Namespace.CONNECTIVITY, "multipoint")

    /** Charge state of the earbud or headphone itself. */
    val BATTERY: FeatureId = FeatureId.of(Namespace.POWER, "battery")

    /** Charge state of the charging case, reported separately from the buds. */
    val CASE_BATTERY: FeatureId = FeatureId.of(Namespace.POWER, "case-battery")

    /** Firmware version as the device reports it, the fact that revokes claims on change. */
    val FIRMWARE_INFO: FeatureId = FeatureId.of(Namespace.FIRMWARE, "info")

    /** Latency-oriented mode, a link behaviour rather than a sound-shaping one. */
    val GAMING_MODE: FeatureId = FeatureId.of(Namespace.CONNECTIVITY, "gaming-mode")

    /** Spoken notifications the device produces itself, such as pairing or battery warnings. */
    val VOICE_PROMPTS: FeatureId = FeatureId.of(Namespace.AUDIO_QUALITY, "voice-prompts")

    /** The listener's own voice returned into the ear, a call-path quality. */
    val SIDETONE: FeatureId = FeatureId.of(Namespace.AUDIO_QUALITY, "sidetone")

    /** Every core identity, in one place, each of them distinct. */
    val all: List<FeatureId> = listOf(
        ANC,
        TRANSPARENCY,
        ADAPTIVE_ANC,
        EQUALIZER,
        GESTURES,
        WEAR_DETECTION,
        SPATIAL_AUDIO,
        HEAD_TRACKING,
        MULTIPOINT,
        BATTERY,
        CASE_BATTERY,
        FIRMWARE_INFO,
        GAMING_MODE,
        VOICE_PROMPTS,
        SIDETONE,
    )

    /**
     * A definition for every identity in [all], exactly once.
     *
     * The hints describe shapes seen across devices and are documentation for a reader;
     * none of them says the connected device offers anything.
     */
    val definitions: List<CapabilityDefinition> = listOf(
        CapabilityDefinition(
            feature = ANC,
            displayName = "Active noise control",
            category = FeatureCategory.NOISE_CONTROL,
            supportedValueHint = "Commonly a mode choice among off, ANC and transparency, " +
                "sometimes with device-specific levels or wind and environment handling. " +
                "The set a device actually offers is discovered, never assumed.",
        ),
        CapabilityDefinition(
            feature = TRANSPARENCY,
            displayName = "Transparency",
            category = FeatureCategory.NOISE_CONTROL,
            supportedValueHint = "Usually a mode within the same control as ANC rather than an " +
                "independent switch; some devices add adjustable transparency levels.",
        ),
        CapabilityDefinition(
            feature = ADAPTIVE_ANC,
            displayName = "Adaptive noise control",
            category = FeatureCategory.NOISE_CONTROL,
            supportedValueHint = "Typically a switch that hands the device's own analysis control, " +
                "occasionally with a sensitivity value attached.",
        ),
        CapabilityDefinition(
            feature = EQUALIZER,
            displayName = "Equalizer",
            category = FeatureCategory.EQUALIZATION,
            supportedValueHint = "Presets, a small bass/mid/treble set, or banded graphic and " +
                "parametric controls; band counts, ranges and units are entirely device-specific.",
        ),
        CapabilityDefinition(
            feature = GESTURES,
            displayName = "Gesture controls",
            category = FeatureCategory.INPUT,
            supportedValueHint = "Single, double and triple taps and long presses, often assigned " +
                "independently per side, with an action vocabulary that varies by manufacturer.",
        ),
        CapabilityDefinition(
            feature = WEAR_DETECTION,
            displayName = "Wear detection",
            category = FeatureCategory.SENSING,
            supportedValueHint = "Normally a reported in-or-out state that other behaviour such as " +
                "auto-pause depends on; some devices expose only the switch, some only the reading.",
        ),
        CapabilityDefinition(
            feature = SPATIAL_AUDIO,
            displayName = "Spatial audio",
            category = FeatureCategory.AUDIO_QUALITY,
            supportedValueHint = "Usually a mode switch, sometimes with a head-tracked variant; " +
                "whether it is device-side or phone-side rendering changes what a control can mean.",
        ),
        CapabilityDefinition(
            feature = HEAD_TRACKING,
            displayName = "Head tracking",
            category = FeatureCategory.SENSING,
            supportedValueHint = "A sensor reading that may be reportable while no control over it exists.",
        ),
        CapabilityDefinition(
            feature = MULTIPOINT,
            displayName = "Multipoint",
            category = FeatureCategory.CONNECTIVITY,
            supportedValueHint = "Typically an on/off behaviour whose effect depends on the codecs and " +
                "profiles the session actually negotiated, so it interacts with other claims.",
        ),
        CapabilityDefinition(
            feature = BATTERY,
            displayName = "Battery",
            category = FeatureCategory.POWER,
            supportedValueHint = "Per-side percentages plus charging state where reported. A level nobody " +
                "reported stays unknown — never 0, never a guess.",
        ),
        CapabilityDefinition(
            feature = CASE_BATTERY,
            displayName = "Case battery",
            category = FeatureCategory.POWER,
            supportedValueHint = "A case level and charging state, reported only when the case itself is " +
                "reachable; the buds' battery does not imply the case is known.",
        ),
        CapabilityDefinition(
            feature = FIRMWARE_INFO,
            displayName = "Firmware information",
            category = FeatureCategory.FIRMWARE,
            supportedValueHint = "Version strings as the device reports them. A change outside a recorded " +
                "compatible range revokes claims above INFERRED for the capabilities it governed.",
        ),
        CapabilityDefinition(
            feature = GAMING_MODE,
            displayName = "Low-latency mode",
            category = FeatureCategory.CONNECTIVITY,
            supportedValueHint = "A latency-oriented link behaviour, frequently vendor-named; it is listed " +
                "by its function so no particular manufacturer's wording becomes the identity.",
        ),
        CapabilityDefinition(
            feature = VOICE_PROMPTS,
            displayName = "Voice prompts",
            category = FeatureCategory.AUDIO_QUALITY,
            supportedValueHint = "A switch, and on some devices a volume or language choice, for the " +
                "prompts the device speaks itself.",
        ),
        CapabilityDefinition(
            feature = SIDETONE,
            displayName = "Sidetone",
            category = FeatureCategory.AUDIO_QUALITY,
            supportedValueHint = "Own-voice return in the call path, usually off/on or a small level range; " +
                "its availability differs between ANC modes rather than being global.",
        ),
    )

    /** Functional namespace roots in use above, kept next to the ids that use them. */
    private object Namespace {
        const val NOISE_CONTROL: String = "noise-control"
        const val EQUALIZATION: String = "equalization"
        const val INPUT: String = "input"
        const val SENSING: String = "sensing"
        const val CONNECTIVITY: String = "connectivity"
        const val POWER: String = "power"
        const val AUDIO_QUALITY: String = "audio-quality"
        const val FIRMWARE: String = "firmware"
    }

    private val coreFeatures: Set<FeatureId> = all.toSet()

    init {
        check(all.size == coreFeatures.size) { "core feature identities are not distinct: $all" }
        check(definitions.map { it.feature }.toSet() == coreFeatures) {
            "the definition catalogue and the identity catalogue describe different feature sets"
        }
        check(definitions.size == all.size) {
            "a core feature is defined more than once, so definitions no longer cover all exactly once"
        }
        check(all.none { it.isVendorExtension }) {
            "a vendor-namespaced identity cannot be a core feature"
        }
        check(all.none { it.namespace == VendorExtension.VENDOR_ROOT }) {
            "the vendor root namespace is reserved for vendor extensions, not core features"
        }
    }

    /**
     * Whether [feature] is one of the universal identities in [all].
     *
     * Identity only — this says nothing about whether any device supports it.
     */
    fun isCore(feature: FeatureId): Boolean = feature in coreFeatures
}
