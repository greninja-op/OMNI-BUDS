package com.omnibuds.core.device

/**
 * The one place device text is made comparable, and ADR-P5-005's proof that this is a small job.
 *
 * Prompt §7 requires that normalization "must not destroy meaningful identity distinctions", and the
 * distinctions a headphone name carries are exactly the ones a helpful normalizer erases:
 * `WF-1000XM3` and `WF-1000XM4` differ only by what a strip-the-digits rule would remove, and
 * `LinkBuds` versus `LinkBuds S` differ only by a token a collapse-brand-and-model rule would merge.
 * So the rules are the near-nothing below, and the version is published into every derived signal
 * because a rule set written against version 1 must not silently match evidence normalized as
 * version 2.
 *
 * What is deliberately absent, each for a stated reason rather than by oversight:
 *  - **No transliteration or accent folding.** Unicode normalization needs `java.text.Normalizer`,
 *    which `coreMainSourcesDoNotImportJvmOnlyLibraries` forbids in `:core` - and locale-sensitive
 *    folding of a name a vendor chose is the destruction prompt §7 names.
 *  - **No brand-token stripping, no camelCase splitting, no punctuation removal.** Each produces a
 *    comparison that a rule could pass on evidence a human would not accept.
 *  - **No fuzzy or edit-distance matching.** That belongs to a scorer, and ADR-P5-004 refuses scores
 *    with no calibrated meaning (prompt §9).
 */
object IdentityNormalizer {
    /** The version of these rules. Bumping it invalidates every derived signal produced under 1. */
    const val NORMALIZATION_VERSION: Int = 1

    /**
     * Trim, collapse internal whitespace runs to one space, case-fold. Locale-independent by
     * construction: [lowercase] is Kotlin's, and a name matched differently depending on the phone's
     * language setting would be a different device to the engine.
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.replace(WHITESPACE_RUN, SINGLE_SPACE).lowercase()
    }

    /**
     * The text a rule compares against, tagged with the version that produced it.
     *
     * Kept as a pair rather than a bare string because a `HIGH`-confidence rule cites its evidence,
     * and evidence whose normalization version is unknown cannot be re-derived later.
     */
    fun normalizeVersioned(raw: String?): NormalizedText? = normalize(raw)?.let { value ->
        NormalizedText(value = value, normalizationVersion = NORMALIZATION_VERSION)
    }

    private val WHITESPACE_RUN = Regex("\\s+")
    private const val SINGLE_SPACE = " "
}

/**
 * A normalized value plus the version that made it.
 *
 * The version travels with the text for one reason: [DeviceFingerprint.identityKey] is used to
 * recognise the same device across reconnects, so a key built from normalization v1 and a key built
 * from v2 must not look like evidence that changed when only the reading rule did.
 */
data class NormalizedText(
    val value: String,
    val normalizationVersion: Int,
)
