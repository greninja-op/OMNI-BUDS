package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue

/**
 * The standard hardware-feature control catalogue: the common contracts Phase 9
 * establishes for the features OmniBuds will eventually drive.
 *
 * Read this catalogue the way [com.omnibuds.core.capability.CoreFeature] asks to
 * be read: **listing a feature is not supporting it.** Every definition here
 * says "where this exists, this is how it is driven" — value shape, constraints
 * and declared relations. Whether the connected device implements any of it is
 * decided by discovery, per device, and recorded in
 * [com.omnibuds.core.capability.FeatureCapability]. Nothing here is a support
 * claim, a vendor command, an opcode, a UUID or a packet layout (Phase 9 prompt
 * section 27).
 *
 * ### How the catalogue answers the prompt's architecture sections
 *
 * - **ANC (§14).** `noise-control.anc` is the boolean switch; `noise-control.anc-mode`
 *   is the unified mode control some devices expose instead; `noise-control.anc-level`,
 *   `noise-control.adaptive-anc`, `noise-control.wind-reduction` and
 *   `noise-control.environment-mode` are the finer controls. The catalogue does not
 *   assume a device has all of them — that is precisely why they are separate
 *   definitions with relations instead of one assumed bundle.
 * - **Transparency (§15).** `noise-control.transparency` is boolean; some devices
 *   expose only on/off and others a level — both shapes exist, and discovery
 *   decides which (if either) a device has.
 * - **Equalizer (§16).** One identity, `equalization.equalizer`, carrying a
 *   structured value whose `form` field selects preset, graphic, parametric or
 *   tone-shaping — the [com.omnibuds.core.capability.CoreFeature] precedent that
 *   band layouts are *values*, not identities. No DSP runs on the phone: the
 *   value describes the setting the *device's* hardware applies.
 * - **Gestures (§17).** One identity, `input.gestures`, carrying a structured
 *   assignment list of gesture + side + action. The gesture and action
 *   vocabularies are documented sets, not constraints: the action vocabulary
 *   varies by manufacturer, so the definition cannot close it universally.
 * - **Other features (§18).** Wear detection, multipoint, spatial audio, head
 *   tracking, gaming mode, voice prompts and sidetone each get a contract with
 *   the shape the prompt describes.
 *
 * Numeric bounds are left unstated wherever the bound is device-specific: a
 * definition must not fabricate a maximum no device has demonstrated. The bounds
 * that *are* stated are safety limits (string lengths, entry counts), not
 * hardware claims.
 */
object StandardFeatures {

    // ---- noise control ---------------------------------------------------

    /** Active noise control: the device's own cancellation of outside sound. */
    val ANC: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "anc"),
        displayName = "Active noise control",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
        relations = listOf(
            FeatureRelation.ConflictsWith(
                feature = FeatureId.of("noise-control", "anc"),
                other = FeatureId.of("noise-control", "anc-mode"),
                reason = "the boolean switch and the unified mode control drive the same " +
                    "hardware; a device exposing both is contradictory",
            ),
        ),
    )

    /**
     * The unified noise-control mode some devices expose instead of separate
     * switches: off, ANC, transparency, or adaptive. The inactive mode is named
     * `off` per the [INACTIVE_MODE_NAME] convention so conflict evaluation can
     * recognise it.
     */
    val ANC_MODE: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "anc-mode"),
        displayName = "Noise control mode",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.ENUM,
        constraints = FeatureConstraints(
            allowedModes = setOf("off", "anc", "transparency", "adaptive"),
        ),
        relations = listOf(
            FeatureRelation.ConflictsWith(
                feature = FeatureId.of("noise-control", "anc-mode"),
                other = FeatureId.of("noise-control", "anc"),
                reason = "the unified mode control and the boolean switch drive the same " +
                    "hardware; a device exposing both is contradictory",
            ),
        ),
    )

    /** ANC intensity where the device exposes a stepped level. Bounds are discovered per device. */
    val ANC_LEVEL: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "anc-level"),
        displayName = "ANC level",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.INTEGER,
        constraints = FeatureConstraints(minInt = 0),
        relations = listOf(
            FeatureRelation.Requires(
                feature = FeatureId.of("noise-control", "anc-level"),
                prerequisite = FeatureId.of("noise-control", "anc"),
            ),
        ),
    )

    /** Noise control whose behaviour the device adapts on its own. */
    val ADAPTIVE_ANC: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "adaptive-anc"),
        displayName = "Adaptive noise control",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
        relations = listOf(
            FeatureRelation.Requires(
                feature = FeatureId.of("noise-control", "adaptive-anc"),
                prerequisite = FeatureId.of("noise-control", "anc"),
            ),
        ),
    )

    /** Reduction of wind noise, where the device exposes it as its own control. */
    val WIND_REDUCTION: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "wind-reduction"),
        displayName = "Wind reduction",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.ENUM,
        constraints = FeatureConstraints(
            allowedModes = setOf("off", "low", "high"),
        ),
    )

    /** Environment-aware mode selection, where the device exposes it. */
    val ENVIRONMENT_MODE: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "environment-mode"),
        displayName = "Environment mode",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.ENUM,
        constraints = FeatureConstraints(
            allowedModes = setOf("off", "indoor", "outdoor", "commute"),
        ),
    )

    /** Pass-through of outside sound, deliberately not modelled as "ANC turned off". */
    val TRANSPARENCY: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "transparency"),
        displayName = "Transparency",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** Transparency intensity where the device exposes a stepped level. */
    val TRANSPARENCY_LEVEL: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "transparency-level"),
        displayName = "Transparency level",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.INTEGER,
        constraints = FeatureConstraints(minInt = 0),
        relations = listOf(
            FeatureRelation.Requires(
                feature = FeatureId.of("noise-control", "transparency-level"),
                prerequisite = FeatureId.of("noise-control", "transparency"),
            ),
        ),
    )

    /** Automatic transparency, e.g. while speaking or in conversation. */
    val AUTO_TRANSPARENCY: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "auto-transparency"),
        displayName = "Automatic transparency",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
        relations = listOf(
            FeatureRelation.Requires(
                feature = FeatureId.of("noise-control", "auto-transparency"),
                prerequisite = FeatureId.of("noise-control", "transparency"),
            ),
        ),
    )

    /** Voice pass-through: ambient voice emphasis within transparency. */
    val VOICE_PASSTHROUGH: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("noise-control", "voice-passthrough"),
        displayName = "Voice pass-through",
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
        relations = listOf(
            FeatureRelation.Requires(
                feature = FeatureId.of("noise-control", "voice-passthrough"),
                prerequisite = FeatureId.of("noise-control", "transparency"),
            ),
        ),
    )

    // ---- equalization ----------------------------------------------------

    /**
     * Tone shaping as one structured value. The `form` field selects the shape:
     *
     * - `preset`: `preset-name` (string) — the device's named preset.
     * - `graphic`: `bands` (list of `{frequency, gain}`) — variable band count.
     * - `parametric`: `bands` (list of `{frequency, gain, q, filter-type}`).
     * - `tone`: `bass`, `mid`, `treble` integers — the small classic set.
     *
     * Frequencies, gains, Q factors and band counts are device-specific and
     * therefore unbounded here; the engine validates structure, not acoustics.
     * The value describes the setting the *device's* hardware applies — no DSP
     * runs on the phone (Phase 9 prompt section 16, Rule 6).
     */
    val EQUALIZER: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("equalization", "equalizer"),
        displayName = "Equalizer",
        category = FeatureCategory.EQUALIZATION,
        valueType = FeatureValueType.STRUCTURED,
        constraints = FeatureConstraints(maxEntries = 32),
    )

    // ---- gestures --------------------------------------------------------

    /**
     * Gesture controls as a structured assignment list: each entry carries
     * `gesture` (e.g. `double-tap`), `side` (e.g. `left`), and `action`
     * (e.g. `play-pause`).
     *
     * The known gesture vocabulary is documented below; the *action* vocabulary
     * is intentionally not closed into a constraint, because it varies by
     * manufacturer and a universal closed set would be a fabricated claim.
     * Vendor-specific actions ride as mode technical names under the same shape.
     */
    val GESTURES: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("input", "gestures"),
        displayName = "Gesture controls",
        category = FeatureCategory.INPUT,
        valueType = FeatureValueType.STRUCTURED,
        constraints = FeatureConstraints(maxEntries = 32),
    )

    /** Gestures the definition vocabulary recognises. Devices may expose a subset. */
    val KNOWN_GESTURES: Set<String> = setOf(
        "single-tap", "double-tap", "triple-tap", "long-press", "swipe", "custom",
    )

    /** Input sides the definition vocabulary recognises. */
    val KNOWN_GESTURE_SIDES: Set<String> = setOf("left", "right", "both", "case")

    /**
     * Actions the definition vocabulary recognises. Documented, not enforced:
     * the action set varies by manufacturer, so closing it would fabricate a
     * universal claim (Phase 9 prompt section 17).
     */
    val KNOWN_GESTURE_ACTIONS: Set<String> = setOf(
        "play-pause", "next", "previous", "volume-up", "volume-down",
        "anc", "transparency", "assistant", "gaming-mode", "custom-vendor-action",
    )

    // ---- sensing / connectivity / audio ----------------------------------

    /** In-ear presence detection. Usually reported; sometimes configurable. */
    val WEAR_DETECTION: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("sensing", "wear-detection"),
        displayName = "Wear detection",
        category = FeatureCategory.SENSING,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** Simultaneous connections to more than one source device. */
    val MULTIPOINT: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("connectivity", "multipoint"),
        displayName = "Multipoint",
        category = FeatureCategory.CONNECTIVITY,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** Head-relative audio placement. */
    val SPATIAL_AUDIO: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("audio-quality", "spatial-audio"),
        displayName = "Spatial audio",
        category = FeatureCategory.AUDIO_QUALITY,
        valueType = FeatureValueType.ENUM,
        constraints = FeatureConstraints(
            allowedModes = setOf("off", "fixed", "head-tracked"),
        ),
    )

    /** Sensing of head position or movement. */
    val HEAD_TRACKING: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("sensing", "head-tracking"),
        displayName = "Head tracking",
        category = FeatureCategory.SENSING,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** Latency-oriented link behaviour. */
    val GAMING_MODE: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("connectivity", "gaming-mode"),
        displayName = "Gaming mode",
        category = FeatureCategory.CONNECTIVITY,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** Spoken notifications the device produces itself. */
    val VOICE_PROMPTS: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("audio-quality", "voice-prompts"),
        displayName = "Voice prompts",
        category = FeatureCategory.AUDIO_QUALITY,
        valueType = FeatureValueType.BOOLEAN,
    )

    /** The listener's own voice returned into the ear. Bounds are device-specific. */
    val SIDETONE: FeatureDefinition = FeatureDefinition(
        feature = FeatureId.of("audio-quality", "sidetone"),
        displayName = "Sidetone",
        category = FeatureCategory.AUDIO_QUALITY,
        valueType = FeatureValueType.INTEGER,
        constraints = FeatureConstraints(minInt = 0),
    )

    /** Every standard definition, each feature exactly once. */
    val all: List<FeatureDefinition> = listOf(
        ANC,
        ANC_MODE,
        ANC_LEVEL,
        ADAPTIVE_ANC,
        WIND_REDUCTION,
        ENVIRONMENT_MODE,
        TRANSPARENCY,
        TRANSPARENCY_LEVEL,
        AUTO_TRANSPARENCY,
        VOICE_PASSTHROUGH,
        EQUALIZER,
        GESTURES,
        WEAR_DETECTION,
        MULTIPOINT,
        SPATIAL_AUDIO,
        HEAD_TRACKING,
        GAMING_MODE,
        VOICE_PROMPTS,
        SIDETONE,
    )

    /** The definitions as the engine consumes them: feature id to contract. */
    val asMap: Map<FeatureId, FeatureDefinition> = all.associateBy { it.feature }

    init {
        require(all.size == all.map { it.feature }.toSet().size) {
            "the standard catalogue must not define a feature twice"
        }
    }
}

/**
 * Builds a structured EQ value of the [StandardFeatures.EQUALIZER] contract.
 *
 * These are value constructors, not device claims: they shape values the engine
 * can validate, for tests and for the later phases that will read real device
 * state into them.
 */
object EqualizerValues {

    /** A preset selection: `form=preset`, `preset-name=<name>`. */
    fun preset(presetName: String): ConfigurationValue.StructuredValue {
        require(presetName.isNotBlank()) { "a preset name cannot be blank" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField(
                    "form",
                    ConfigurationValue.ModeValue("preset", "Preset"),
                ),
                ConfigurationValue.StructuredField(
                    "preset-name",
                    ConfigurationValue.StringValue(presetName),
                ),
            ),
        )
    }

    /** One graphic-EQ band: frequency in Hz, gain in dB. */
    fun graphicBand(frequencyHz: Double, gainDb: Double): ConfigurationValue.StructuredValue =
        ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField("frequency", ConfigurationValue.FloatValue(frequencyHz)),
                ConfigurationValue.StructuredField("gain", ConfigurationValue.FloatValue(gainDb)),
            ),
        )

    /** One parametric-EQ band: frequency in Hz, gain in dB, Q factor, filter type. */
    fun parametricBand(
        frequencyHz: Double,
        gainDb: Double,
        q: Double,
        filterType: String,
    ): ConfigurationValue.StructuredValue {
        require(filterType.isNotBlank()) { "a filter type cannot be blank" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField("frequency", ConfigurationValue.FloatValue(frequencyHz)),
                ConfigurationValue.StructuredField("gain", ConfigurationValue.FloatValue(gainDb)),
                ConfigurationValue.StructuredField("q", ConfigurationValue.FloatValue(q)),
                ConfigurationValue.StructuredField(
                    "filter-type",
                    ConfigurationValue.ModeValue(filterType, filterType),
                ),
            ),
        )
    }

    /** A graphic-EQ setting: `form=graphic`, `bands=[...]` with a variable count. */
    fun graphic(bands: List<ConfigurationValue.StructuredValue>): ConfigurationValue.StructuredValue {
        require(bands.isNotEmpty()) { "a graphic EQ needs at least one band" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField(
                    "form",
                    ConfigurationValue.ModeValue("graphic", "Graphic"),
                ),
                ConfigurationValue.StructuredField(
                    "bands",
                    ConfigurationValue.StructuredValue(
                        bands.mapIndexed { index, band ->
                            ConfigurationValue.StructuredField("band-$index", band)
                        },
                    ),
                ),
            ),
        )
    }

    /** A parametric-EQ setting: `form=parametric`, `bands=[...]`. */
    fun parametric(bands: List<ConfigurationValue.StructuredValue>): ConfigurationValue.StructuredValue {
        require(bands.isNotEmpty()) { "a parametric EQ needs at least one band" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField(
                    "form",
                    ConfigurationValue.ModeValue("parametric", "Parametric"),
                ),
                ConfigurationValue.StructuredField(
                    "bands",
                    ConfigurationValue.StructuredValue(
                        bands.mapIndexed { index, band ->
                            ConfigurationValue.StructuredField("band-$index", band)
                        },
                    ),
                ),
            ),
        )
    }
}

/**
 * Builds a structured gesture-assignment value of the [StandardFeatures.GESTURES]
 * contract: each entry carries `gesture`, `side` and `action` as mode values.
 */
object GestureValues {

    /** One assignment: gesture + side + action, all as mode technical names. */
    fun assignment(gesture: String, side: String, action: String): ConfigurationValue.StructuredValue {
        require(gesture.isNotBlank()) { "a gesture cannot be blank" }
        require(side.isNotBlank()) { "a side cannot be blank" }
        require(action.isNotBlank()) { "an action cannot be blank" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField(
                    "gesture",
                    ConfigurationValue.ModeValue(gesture, gesture),
                ),
                ConfigurationValue.StructuredField(
                    "side",
                    ConfigurationValue.ModeValue(side, side),
                ),
                ConfigurationValue.StructuredField(
                    "action",
                    ConfigurationValue.ModeValue(action, action),
                ),
            ),
        )
    }

    /** A gesture configuration: `assignments=[...]`. */
    fun configuration(
        assignments: List<ConfigurationValue.StructuredValue>,
    ): ConfigurationValue.StructuredValue {
        require(assignments.isNotEmpty()) { "a gesture configuration needs at least one assignment" }
        return ConfigurationValue.StructuredValue(
            listOf(
                ConfigurationValue.StructuredField(
                    "assignments",
                    ConfigurationValue.StructuredValue(
                        assignments.mapIndexed { index, assignment ->
                            ConfigurationValue.StructuredField("assignment-$index", assignment)
                        },
                    ),
                ),
            ),
        )
    }
}
