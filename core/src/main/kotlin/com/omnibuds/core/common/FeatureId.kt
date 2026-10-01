package com.omnibuds.core.common

/**
 * A stable, namespaced identity for a feature.
 *
 * Features are addressed by identity rather than by brand conditional, so that
 * `if (sony) ... if (bose) ...` never appears in shared code (Phase 1 prompt
 * section 20, ADR-P0-007). Core features live under a functional namespace such as
 * `noise-control.anc`; vendor-specific functionality is namespaced by vendor and can
 * never collide with, or masquerade as, a core feature.
 *
 * The [qualifiedName] is a stable contract: renaming one is a breaking change and
 * requires an ADR, because it is also the key used by the future protocol database.
 */
@JvmInline
value class FeatureId private constructor(val qualifiedName: String) {

    val namespace: String
        get() = qualifiedName.substringBeforeLast('.')

    val localName: String
        get() = qualifiedName.substringAfterLast('.')

    /** Namespace segments in order, e.g. `vendor`, `sony`, `adaptive-sound-control`. */
    val segments: List<String>
        get() = qualifiedName.split('.')

    /** True for `vendor.<vendor>.<feature>` identifiers. */
    val isVendorExtension: Boolean
        get() = segments.size >= MIN_VENDOR_SEGMENTS && segments.first() == VENDOR_ROOT

    /** The vendor segment of a [isVendorExtension] id, or null for a core feature. */
    val vendorName: String?
        get() = if (isVendorExtension) segments[1] else null

    companion object {
        private const val VENDOR_ROOT = "vendor"
        private const val MIN_VENDOR_SEGMENTS = 3
        private const val MIN_SEGMENTS = 2
        private val segmentPattern = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

        /** Namespaced core feature, e.g. `of("noise-control", "anc")`. */
        fun of(vararg segments: String): FeatureId {
            require(segments.size >= 2) {
                "a feature id needs a namespace and a name, got ${segments.toList()}"
            }
            require(segments.all { it.matches(segmentPattern) }) {
                "illegal feature id segments: ${segments.toList()}"
            }
            return FeatureId(segments.joinToString("."))
        }

        /** Vendor extension feature, e.g. `ofVendor("sony", "adaptive-sound-control")`. */
        fun ofVendor(vendor: String, feature: String): FeatureId =
            of(VENDOR_ROOT, vendor, feature)

        /**
         * Decodes a previously stored identifier, returning null rather than guessing when
         * the text is not a valid feature id. This is the read path for protocol records
         * and persisted configuration; it enforces the same shape as [of], so a stored
         * one-segment name cannot become an identity.
         */
        fun parseOrNull(raw: String): FeatureId? {
            val parts = raw.split('.')
            if (parts.size < MIN_SEGMENTS) return null
            return if (parts.all { it.matches(segmentPattern) }) FeatureId(raw) else null
        }
    }
}
