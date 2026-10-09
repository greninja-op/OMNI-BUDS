package com.omnibuds.core.extension

/**
 * Namespaced vendor feature identifier.
 *
 * Phase 23 (OB-P23-REQ-002): aligned with the established project grammar
 * `vendor.<vendor>.<feature>` (see `common.FeatureId.ofVendor` and
 * `feature.VendorFeatureContract`). The product family is metadata on the
 * extension descriptor, not an identifier segment — a second, incompatible
 * grammar would diverge from the enforced convention.
 *
 * Stable across sessions, independent of display labels, unique within the
 * knowledge database, serialization-safe, version-compatible.
 */
@JvmInline
value class VendorFeatureId(val value: String) {
    init {
        require(isValid(value)) { "invalid vendor feature id: $value" }
    }

    companion object {
        private val segmentPattern = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

        /** Validate the identifier format without constructing. */
        fun isValid(value: String): Boolean {
            val segments = value.split(".")
            return segments.size == 3 &&
                segments[0] == "vendor" &&
                segments.all { it.matches(segmentPattern) }
        }

        /** Build from parts, or null when the parts are invalid. */
        fun of(vendor: String, feature: String): VendorFeatureId? {
            val id = "vendor.$vendor.$feature"
            return if (isValid(id)) VendorFeatureId(id) else null
        }
    }

    /** The vendor segment — matches `FeatureId.vendorName`. */
    val vendor: String get() = value.split(".")[1]

    /** The feature segment. */
    val feature: String get() = value.split(".")[2]
}

/**
 * Stable extension identifier.
 * Format: `ext.<vendor>.<name>` (e.g. `ext.sony.budsproto`).
 */
@JvmInline
value class VendorExtensionId(val value: String) {
    init {
        require(isValid(value)) { "invalid vendor extension id: $value" }
    }

    companion object {
        private val segmentPattern = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

        fun isValid(value: String): Boolean {
            val segments = value.split(".")
            return segments.size == 3 &&
                segments[0] == "ext" &&
                segments.all { it.matches(segmentPattern) }
        }
    }
}
