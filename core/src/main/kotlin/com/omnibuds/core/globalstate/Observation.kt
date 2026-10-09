package com.omnibuds.core.globalstate

/**
 * Observation provenance and freshness.
 *
 * Phase 24 (OB-P24-REQ-006): every externally observed property carries
 * metadata to determine whether it remains usable.
 */

/**
 * Freshness classification for an observation.
 */
enum class Freshness {
    /** Observed within the property's validity window. */
    CURRENT,

    /** Observed recently; usable with caution. */
    RECENT,

    /** Older than the validity window; do not act on it. */
    STALE,

    /** Past the expiry threshold; treat as historical only. */
    EXPIRED,

    /** Freshness cannot be determined (e.g. no timestamp). */
    UNKNOWN,
}

/**
 * Provenance metadata for an observation.
 */
data class ObservationProvenance(
    /** Stable source identifier, e.g. "battery-engine". */
    val sourceId: String,
    /** Observation timestamp (source clock), or null when unavailable. */
    val observedAtMillis: Long?,
    /** Receive timestamp (engine clock), or null when unavailable. */
    val receivedAtMillis: Long?,
    /** Device-session identifier, or null when sessionless. */
    val sessionId: String?,
    /** Connection generation, or null when not applicable. */
    val connectionGeneration: Long?,
    /** Protocol version at observation time, or null. */
    val protocolVersion: String?,
    /** Known limitations of this observation. */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(sourceId.isNotBlank()) { "source id must not be blank" }
    }
}

/**
 * A single observed value with its provenance and freshness.
 *
 * @param T the value type.
 */
data class ObservedValue<T>(
    val value: T,
    val provenance: ObservationProvenance,
    val freshness: Freshness = Freshness.UNKNOWN,
) {
    /** True when this observation is usable for decisions. */
    val isUsable: Boolean get() = freshness == Freshness.CURRENT || freshness == Freshness.RECENT
}
