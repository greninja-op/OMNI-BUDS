package com.omnibuds.core.battery

/**
 * Freshness of a battery observation.
 *
 * Phase 16 (OB-P16-REQ-010): a stale value may be retained for diagnostics
 * but must never be presented as current.
 */
enum class BatteryFreshness {
    /** Observed recently enough to trust per the freshness policy. */
    CURRENT,

    /** Observed but too old to present as current. */
    STALE,

    /** No observation exists. */
    UNKNOWN,
}
