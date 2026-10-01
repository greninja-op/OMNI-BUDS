package com.omnibuds.core.audio

/**
 * The single place that answers "is this label a codec, and which one?".
 *
 * Membership here is vocabulary, not evidence: the registry containing LDAC yields no
 * `AVAILABLE`, `ENABLED`, `NEGOTIATED` or `ACTIVE` for any device (AUD-REG-006), and
 * a codec listed here but not actually exposed stays [CodecState.UNKNOWN] until a
 * real source reports it (AUD-REG-003).
 *
 * Extension point: a codec not yet modelled by [Codec] is represented by
 * [Codec.UNKNOWN] plus the vendor's own string kept in the protocol record — never by
 * guessing it onto a real codec, and never by scattering string comparisons such as
 * `if (label == "LDH")` through audio, protocol or UI code. Adding an entry to [Codec]
 * is the documented, registry-data change that makes a codec known (AUD-REG-004,
 * AUD-REG-005, master sections 51 and 52). Phase 11 moves the fuller entry shape —
 * stable codec id, parameter vocabulary, permitted state sources, OS restrictions,
 * confidence — out of the enum and into registry data; asking the registry instead of
 * branching on codec names is what keeps that swap containable.
 */
object CodecRegistry {

    /** Every named codec the model knows, in declaration order, without [Codec.UNKNOWN]. */
    val all: List<Codec> = Codec.entries.filter { it != Codec.UNKNOWN }

    /** Family is a first-class dimension of audio records, so the registry answers for it (AUD-XPORT-001). */
    fun familyOf(codec: Codec): CodecFamily = codec.family

    /**
     * Resolves a label to a [Codec] against either the stable enum name or the display
     * name, case-folded and with separators ignored, because platform and vendor
     * labels are inconsistent about both ("aptX HD", `APTX_HD`, "aptx-hd").
     *
     * Returns null for anything else — a marketing label, a vendor spelling such as
     * "LDH", a blank, a codec the model does not carry yet, and [Codec.UNKNOWN]
     * itself, which is not a codec but the absence of one. Null means "not one of
     * ours; keep the original string in the protocol record". It is never silently
     * mapped onto the nearest real codec, because that guess is exactly how a UI ends
     * up showing LDAC while the stack runs AAC (AUD-REG-005, master section 53).
     */
    fun byName(name: String): Codec? {
        val key = normalize(name)
        if (key.isEmpty()) {
            return null
        }
        return lookup[key]
    }

    /**
     * Built once from [all]. Both spellings of a codec register the same key, so
     * resolution is an exact map hit or a null.
     *
     * A key already claimed by a different codec is not stolen: the first claimant
     * wins, so an ambiguous future label resolves deterministically instead of
     * depending on declaration order at call time.
     */
    private val lookup: Map<String, Codec> = buildMap {
        for (codec in all) {
            for (label in listOf(codec.name, codec.displayName)) {
                val key = normalize(label)
                val claimant = this[key]
                if (claimant == null || claimant == codec) {
                    put(key, codec)
                }
            }
        }
    }

    /** Case-folded, separator-free form used on both sides of the lookup. */
    private fun normalize(label: String): String =
        label.lowercase().filter { it.isLetterOrDigit() }
}
