package com.omnibuds.core.battery

import com.omnibuds.core.audio.CodecEvidence

/**
 * The battery truth for one component.
 *
 * Phase 16 (OB-P16-REQ-004): immutable. Level and charging are independent —
 * either may be unknown while the other is known.
 */
data class ComponentBatteryState(
    val component: BatteryComponent,
    /** Null when the level was not reported. */
    val level: BatteryLevel?,
    val charging: ChargingState,
    /** Null when never observed. */
    val observedAtMillis: Long?,
    val freshness: BatteryFreshness,
    val evidence: CodecEvidence?,
    /** Source-specific caveats, e.g. "HFP-derived, not vendor truth". */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(observedAtMillis == null || observedAtMillis >= 0) {
            "observedAtMillis must be non-negative"
        }
    }

    companion object {
        /** The honest starting point: nothing known. */
        fun unknown(component: BatteryComponent): ComponentBatteryState =
            ComponentBatteryState(
                component = component,
                level = null,
                charging = ChargingState.UNKNOWN,
                observedAtMillis = null,
                freshness = BatteryFreshness.UNKNOWN,
                evidence = null,
            )
    }
}
