package com.omnibuds.core.battery

/**
 * A validated battery percentage.
 *
 * Phase 16 (OB-P16-REQ-002): 0–100 inclusive are valid. 0 is a real reading
 * (flat battery); null is "not reported". Invalid values are rejected at
 * construction — never silently converted to zero.
 */
@JvmInline
value class BatteryLevel private constructor(val percentage: Int) {

    companion object {
        /**
         * Create a level, or null when the source reported nothing.
         * @throws IllegalArgumentException for values outside 0..100.
         */
        fun of(percentage: Int?): BatteryLevel? {
            if (percentage == null) return null
            require(percentage in 0..100) {
                "Battery percentage must be 0..100, was $percentage"
            }
            return BatteryLevel(percentage)
        }

        /**
         * Parse a platform-reported value without throwing: returns null
         * for missing AND for malformed values (the caller records the
         * validation warning separately).
         */
        fun parseLenient(raw: Int?): BatteryLevel? =
            if (raw != null && raw in 0..100) BatteryLevel(raw) else null
    }

    override fun toString(): String = "$percentage%"
}
