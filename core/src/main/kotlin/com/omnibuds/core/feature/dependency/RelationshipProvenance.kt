package com.omnibuds.core.feature.dependency

/**
 * Verification status of a feature relationship.
 *
 * Phase 29 (OB-P29-REQ-001): mirrors the project's evidence ladder.
 * A relationship's trust is never higher than its evidence.
 */
enum class RelationshipVerification {
    /** Hypothesized from observation; never a hard constraint. */
    INFERRED,

    /** Implemented in a definition or protocol model. */
    IMPLEMENTED,

    /** Tested in a lab environment. */
    LAB_TESTED,

    /** Verified against real hardware. */
    HARDWARE_VERIFIED,

    /** Verified to persist across the relevant scopes. */
    PERSISTENCE_VERIFIED,
}

/** Whether the verification level may produce a hard conflict. */
val RelationshipVerification.mayBlock: Boolean
    get() = this == RelationshipVerification.HARDWARE_VERIFIED ||
        this == RelationshipVerification.PERSISTENCE_VERIFIED

/**
 * Device/firmware scope of a relationship.
 */
data class RelationshipScope(
    /** Manufacturer identifier, or null for any. */
    val manufacturer: String? = null,
    /** Model identifier, or null for any. */
    val model: String? = null,
    /** Firmware version range, e.g. ">=2.1.0", or null for any. */
    val firmwareRange: String? = null,
) {
    /** True when this scope applies to the given device description. */
    fun matches(
        manufacturer: String?,
        model: String?,
        firmware: String?,
    ): Boolean {
        if (this.manufacturer != null && this.manufacturer != manufacturer) return false
        if (this.model != null && this.model != model) return false
        // Firmware ranges are informational in this phase; exact range
        // evaluation belongs to a future versioning engine. A non-null
        // range that cannot be evaluated is treated as not-matching to
        // avoid applying a rule outside its scope (fail-closed).
        if (firmwareRange != null && firmware == null) return false
        return true
    }
}

/**
 * Provenance of one relationship rule.
 */
data class RelationshipProvenance(
    /** Stable rule identifier. */
    val ruleId: String,
    val verification: RelationshipVerification,
    val scope: RelationshipScope,
    /** Evidence or claim identifiers from the knowledge database. */
    val evidenceIds: List<String> = emptyList(),
    /** Rule-set version this rule belongs to. */
    val ruleSetVersion: Int = 1,
    /** True when superseded by a newer rule; superseded rules are inert. */
    val superseded: Boolean = false,
    /** Known limitations, free text. */
    val limitations: String? = null,
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
    }
}
