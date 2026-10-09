package com.omnibuds.core.battery

/**
 * Charging state, independent of battery percentage.
 *
 * Phase 16 (OB-P16-REQ-003, OB-P16-REQ-015): charging is observed, never
 * inferred. 100% does not imply FULL; null from a source means UNKNOWN,
 * never NOT_CHARGING.
 */
enum class ChargingState {
    /** The source reports the component is charging. */
    CHARGING,

    /** The source reports the component is not charging. */
    NOT_CHARGING,

    /** The source explicitly reports a full-charge state. */
    FULL,

    /** Charging state was not reported. The default. */
    UNKNOWN,
}
