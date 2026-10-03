package com.omnibuds.core.device

/**
 * Which piece of identity evidence a signal carries.
 *
 * Nine kinds, and the set is closed by what this phase can actually obtain. Most of prompt §6's
 * fourteen categories are reachable without touching the air — `connection-observation-research.md`
 * §5 established that device type, class, alias, bond state, the cached service-UUID list and the
 * name are all reads covered by the one `BLUETOOTH_CONNECT` already declared, and that
 * `getUuids()` is documented as *not* starting a discovery procedure. Two kinds remain
 * unobtainable: advertisement manufacturer data needs a scan the user declined (ADR-P5-007) and
 * characteristic UUIDs need the GATT lookup prompt §17 forbids. Both keep a kind here precisely so
 * that [IdentitySignal.quality] can answer `UNAVAILABLE` for them: a signal the phase cannot obtain
 * is a statement about the phase, and the only way to keep it from being read as "this device has no
 * manufacturer data" is to keep asking the question and recording the answer. Platform metadata has
 * no kind at all, because `getMetadata(int)` is `@SystemApi` with `BLUETOOTH_PRIVILEGED` and is not
 * callable from this module — a missing kind is the honest shape for an unreachable read.
 *
 * No kind holds a Bluetooth address, in its value or in its text. Attribution - "the same device as
 * that earlier report" - is [com.omnibuds.core.platform.DeviceObservationKey]'s job and nothing here
 * duplicates it (ADR-P5-010, ADR-P3-010, SEC-ID-003).
 */
enum class IdentitySignalKind {
    /** The name the device reports for itself. Device-chosen, and the only kind with vendor meaning. */
    REPORTED_NAME,

    /** The name the *user* gave this device in their Bluetooth settings. Least trustworthy of names. */
    USER_ALIAS,

    /**
     * The class-of-device integer, as reported.
     *
     * Corroborating only, and never primary: Phase 3 refused this signal on the platform's own words
     * that it "does not reliably describe which profiles or services are actually supported"
     * (`connection-observation-research.md` §5.2), and ADR-P5-007 keeps it at that status here.
     */
    DEVICE_CLASS,

    /** The platform's device type - classic, low-energy, or dual. */
    DEVICE_TYPE,

    /** Whether a pairing record exists, from the bond axis. */
    BOND_STATE,

    /** One profile that reported a link to this device. */
    OBSERVED_PROFILE,

    /** Advertisement manufacturer data. Reachable only by scanning: `UNAVAILABLE` in Phase 5. */
    MANUFACTURER_DATA,

    /**
     * A service identifier from the platform's local cache.
     *
     * Readable without any air traffic (`getUuids()` is documented as not starting a discovery
     * procedure), but it is a cache: an empty list on a device nobody has queried is `UNKNOWN`, and
     * prompt §7's warning that this is the normal case for an app that does not scan is the reason
     * a rule may not read it as a services list.
     */
    SERVICE_UUID,

    /** A discovered characteristic identifier. `UNAVAILABLE` in Phase 5, and a GATT read besides. */
    CHARACTERISTIC_UUID,
}

/**
 * How one identity signal is known, in six distinctions that do not collapse into each other.
 *
 * This is ADR-P0-016's three-tier unknown widened to the identity layer (ADR-P5-003). The pair that
 * matters most is the last two: `UNAVAILABLE` means this platform and this phase cannot answer, and
 * `INVALID` means an answer arrived and was rejected. Both stand in for a family of failures that
 * would otherwise be recorded as absence, and absence read as a negative is how a device ends up
 * reported as *not having* something nobody was able to look for.
 */
enum class SignalQuality {
    /** The platform stated it, about this device, now. */
    OBSERVED,

    /** Computed from an observed signal by a named, versioned rule - a normalised name, only. */
    DERIVED,

    /** Evidence leans this way without establishing it: a transport candidate from a device type. */
    INFERRED,

    /** No reading was obtained. Not "none exists". */
    UNKNOWN,

    /** This phase has no way to ask, whatever the device does or does not have. */
    UNAVAILABLE,

    /** A value arrived and failed validation. Kept, because dropping it silently is the loss. */
    INVALID,
}

/**
 * Where a signal came from, because two sources give the same word different weight.
 *
 * `USER_ALIAS` and `REPORTED_NAME` are both strings a device is called by, and a matcher that could
 * not tell them apart would treat a name the user typed in Settings as vendor evidence. Prompt §9
 * names that exact error in its first example.
 */
enum class SignalSource {
    /** Read from the platform about the device itself. */
    PLATFORM,

    /** Read from the platform's pairing records. */
    PAIRING_RECORD,

    /** Computed by [IdentityNormalizer]; never a new fact. */
    NORMALIZED,

    /** Carried up from connected-device observation (Phase 3). */
    OBSERVATION,
}

/**
 * One unit of identity evidence, with its provenance attached.
 *
 * [rawValue] is untrusted input and this type is where it stops being input: the factory validates
 * length and character content, and a value that fails is kept as [SignalQuality.INVALID] with the
 * rejected text discarded rather than stored - prompt §15's requirement that malformed metadata
 * cannot crash or mislead the matcher, and the reason `INVALID` is a quality rather than an
 * exception.
 *
 * Equality covers the evidence, not the observation: two signals of one kind holding one value from
 * one source are the same evidence, while [observedAtEpochMillis] is metadata for reporting
 * freshness. A fingerprint key derived over a set of these is therefore stable across rounds that
 * re-read the same device at different times, which is what
 * [DeviceFingerprint.identityKey]'s "same device across reconnects" promise depends on.
 */
data class IdentitySignal(
    val kind: IdentitySignalKind,
    val quality: SignalQuality,
    val rawValue: String?,
    val source: SignalSource,
    val reliability: SignalReliability,
    val observedAtEpochMillis: Long? = null,
) {
    /** True where this signal can carry a matcher's decision at all. */
    val isUsable: Boolean
        get() = quality == SignalQuality.OBSERVED ||
            quality == SignalQuality.DERIVED ||
            quality == SignalQuality.INFERRED

    /** True where a value was looked for and nothing usable came back, for whichever reason. */
    val answersNothing: Boolean
        get() = rawValue == null || quality == SignalQuality.INVALID

    companion object {
        /** Longest accepted vendor text. Longer input is rejected as malformed, not truncated. */
        const val MAX_VALUE_LENGTH: Int = 128

        /**
         * Validates platform input. A value with control characters, or longer than
         * [MAX_VALUE_LENGTH], becomes [SignalQuality.INVALID] with its text dropped: keeping the text
         * would put unvalidated vendor bytes into anything that prints the signal, and dropping the
         * signal entirely would turn a hostile or broken value into an absence.
         */
        fun observed(
            kind: IdentitySignalKind,
            value: String?,
            source: SignalSource,
            reliability: SignalReliability,
            atEpochMillis: Long? = null,
        ): IdentitySignal {
            val trimmed = value?.trim()
            val quality = when {
                trimmed == null || trimmed.isEmpty() -> SignalQuality.UNKNOWN
                trimmed.length > MAX_VALUE_LENGTH -> SignalQuality.INVALID
                trimmed.any { char -> char.isISOControl() } -> SignalQuality.INVALID
                else -> SignalQuality.OBSERVED
            }
            return IdentitySignal(
                kind = kind,
                quality = quality,
                // Text is kept only when it was actually retained as a fact: an INVALID value is
                // discarded because it is untrusted and already rejected, and a blank value that fell
                // to UNKNOWN is discarded so an empty string never stands in for "nothing was read"
                // (prompt section 6: missing information is not a fabricated default).
                rawValue = if (quality == SignalQuality.OBSERVED) trimmed else null,
                source = source,
                reliability = if (quality == SignalQuality.OBSERVED) reliability else SignalReliability.NONE,
                observedAtEpochMillis = atEpochMillis,
            )
        }

        /** A signal for a kind this phase cannot ask about. About the phase, never the device. */
        fun unavailable(kind: IdentitySignalKind): IdentitySignal = IdentitySignal(
            kind = kind,
            quality = SignalQuality.UNAVAILABLE,
            rawValue = null,
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.NONE,
        )

        /** A signal for a kind that was askable and returned nothing. */
        fun unknown(kind: IdentitySignalKind): IdentitySignal = IdentitySignal(
            kind = kind,
            quality = SignalQuality.UNKNOWN,
            rawValue = null,
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.NONE,
        )

        /** A value computed from another signal by a versioned rule. Never a new fact. */
        fun derived(
            kind: IdentitySignalKind,
            value: String,
            reliability: SignalReliability,
            atEpochMillis: Long?,
        ): IdentitySignal = IdentitySignal(
            kind = kind,
            quality = SignalQuality.DERIVED,
            rawValue = value,
            source = SignalSource.NORMALIZED,
            reliability = reliability,
            observedAtEpochMillis = atEpochMillis,
        )

        /** A candidate the evidence leans toward without establishing. Confidence-capped by ADR-P5-004. */
        fun inferred(
            kind: IdentitySignalKind,
            value: String,
            reliability: SignalReliability,
            atEpochMillis: Long?,
        ): IdentitySignal = IdentitySignal(
            kind = kind,
            quality = SignalQuality.INFERRED,
            rawValue = value,
            source = SignalSource.PLATFORM,
            reliability = reliability,
            observedAtEpochMillis = atEpochMillis,
        )
    }
}

/**
 * How much weight a kind of evidence carries, stated per kind rather than per value.
 *
 * This is the matcher's independence rule (ADR-P5-004) made inspectable: two `HIGH` signals that are
 * both `USER_CHOSEN_TEXT` are not two independent signals, and the ranking lets the engine say so
 * without a special case. [NONE] is the floor an unusable or absent signal carries, so a rule cannot
 * count an `UNAVAILABLE` field toward a threshold.
 */
enum class SignalReliability {
    /** Vendor-chosen text: a reported name. Meaningful, editable, unverifiable at runtime. */
    VENDOR_REPORTED_TEXT,

    /** A platform-computed attribute that is stable but coarse: class, device type. */
    PLATFORM_ATTRIBUTE,

    /** A pairing-state fact, established by the phone rather than the device. */
    PAIRING_FACT,

    /** One service the phone reports a link through. Narrow, and about the phone as much as the device. */
    PROFILE_OBSERVATION,

    /** User-chosen text: the alias typed into Settings. Never vendor evidence. */
    USER_CHOSEN_TEXT,

    /** No weight at all: unknown, unavailable or invalid. */
    NONE,
}
