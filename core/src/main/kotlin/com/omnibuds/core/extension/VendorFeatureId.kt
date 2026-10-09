package com.omnibuds.core.extension

/**
 * Namespaced vendor feature identifier.
 *
 * Phase 23 (OB-P23-REQ-002): format `vendor.<manufacturer>.<family>.<feature>`.
 * Stable across sessions, independent of display labels, unique within the
 * knowledge database, serialization-safe, version-compatible.
 */
@JvmInline
value class VendorFeatureId(val value: String) {
    init {
        require(isValid(value)) { "invalid vendor feature id: $value" }
    }

    companion object {
        private val pattern = Regex("^[a-z0-9]+(\\.[a-z0-9]+){3}$")

        /** Validate the identifier format without constructing. */
        fun isValid(value: String): Boolean =
            value.startsWith("vendor.") && pattern.matches(value)

        /** Build from parts, or null when the parts are invalid. */
        fun of(manufacturer: String, family: String, feature: String): VendorFeatureId? {
            val id = "vendor.$manufacturer.$family.$feature"
            return if (isValid(id)) VendorFeatureId(id) else null
        }
    }

    /** The manufacturer segment. */
    val manufacturer: String get() = value.split(".")[1]

    /** The product-family segment. */
    val family: String get() = value.split(".")[2]

    /** The feature segment. */
    val feature: String get() = value.split(".")[3]
}

/**
 * Stable extension identifier.
 * Format: `ext.<manufacturer>.<name>` (e.g. `ext.acme.budsproto`).
 */
@JvmInline
value class VendorExtensionId(val value: String) {
    init {
        require(isValid(value)) { "invalid vendor extension id: $value" }
    }

    companion object {
        private val pattern = Regex("^ext\\.[a-z0-9]+\\.[a-z0-9]+$")

        fun isValid(value: String): Boolean = pattern.matches(value)
    }
}
