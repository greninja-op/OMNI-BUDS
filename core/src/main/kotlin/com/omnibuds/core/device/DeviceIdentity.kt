package com.omnibuds.core.device

/**
 * What OmniBuds has established about *which* device this is, in words that a human
 * or a future protocol record can check.
 *
 * Every field is nullable because every field may genuinely not have been reported:
 * a device often gives a display name and almost never gives a manufacturer, and a
 * missing manufacturer stays missing. It does not become an empty string, a
 * whitespace placeholder, the literal text `"unknown"`, or a guess inferred from a
 * brand-typical name (Phase 1 prompt sections 7 and 9.1, master section 53,
 * ADR-P0-016).
 *
 * Deliberately absent:
 *
 * - the Bluetooth address — identity here is descriptive text, and an address is a
 *   rotating handle on a person's belongings that must not be treated as identity
 *   (`docs/phases/phase-0/security-governance.md` SEC-ID-001, SEC-ID-003, SEC-ID-004);
 * - firmware, hardware and protocol versions, which describe the build the device is
 *   running rather than which device it is, and live in [FirmwareInfo].
 */
data class DeviceIdentity(
    /** Reported manufacturer, or null when the platform did not report one. */
    val manufacturer: String? = null,

    /** Reported model name, or null when it was not reported. */
    val model: String? = null,

    /** Name the device advertises for itself, or null when it was not reported. */
    val displayName: String? = null,

    /** Model identifier as reported by the device, or null when it was not reported. */
    val modelId: String? = null,
) {

    /** How many of the four fields hold an actual value. Blank text counts as unknown. */
    val knownFieldCount: Int
        get() = listOf(manufacturer, model, displayName, modelId).count { normalised(it) != null }

    /** True only when all four fields are known; one blank field makes this false. */
    val isFullyKnown: Boolean
        get() = knownFieldCount == FIELD_COUNT

    /** True when nothing at all has been established about the device. */
    val isEntirelyUnknown: Boolean
        get() = knownFieldCount == 0

    /**
     * Fills this identity's unknown fields from [other] and restates nothing else.
     *
     * Merge reduces ignorance; it never edits an established fact. A field already
     * known here keeps its value even when [other] disagrees, because two different
     * known values are a conflict for a human or a protocol record to resolve — not
     * something this type may silently overwrite, and certainly not something it may
     * splice into a hybrid string. To restate a known field deliberately, build the
     * new identity with `copy()`.
     *
     * The result is lossless for the receiver and never fabricates a value: every
     * field of the result is either a value one of the two inputs actually reported,
     * or null. Blank text on either side normalises to unknown first, so a padded
     * string cannot win a field.
     */
    fun mergedWith(other: DeviceIdentity): DeviceIdentity = DeviceIdentity(
        manufacturer = fillIfUnknown(manufacturer, other.manufacturer),
        model = fillIfUnknown(model, other.model),
        displayName = fillIfUnknown(displayName, other.displayName),
        modelId = fillIfUnknown(modelId, other.modelId),
    )

    private fun fillIfUnknown(existing: String?, incoming: String?): String? =
        normalised(existing) ?: normalised(incoming)

    companion object {
        private const val FIELD_COUNT = 4

        /** A device about which nothing has been established: all null, never empty strings. */
        fun unknown(): DeviceIdentity = DeviceIdentity(
            manufacturer = null,
            model = null,
            displayName = null,
            modelId = null,
        )

        /**
         * Builds an identity from reported values, demoting blank or whitespace-only
         * text to null and trimming what is kept.
         *
         * The primary constructor stores exactly what it is given, because a domain
         * type may not silently rewrite the evidence a platform reported; this is the
         * path that normalises it. The knownness properties above and [mergedWith]
         * apply the same rule either way, so a blank value can never be read as a
         * known field by this type.
         */
        fun of(
            manufacturer: String? = null,
            model: String? = null,
            displayName: String? = null,
            modelId: String? = null,
        ): DeviceIdentity = DeviceIdentity(
            manufacturer = normalised(manufacturer),
            model = normalised(model),
            displayName = normalised(displayName),
            modelId = normalised(modelId),
        )

        /**
         * Trims a reported field and maps blank text to null, so that `"  "` cannot
         * masquerade as a known manufacturer.
         *
         * Internal rather than private so that the sibling domain packages normalise
         * evidence the same way instead of inventing a second rule for it.
         */
        internal fun normalised(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
