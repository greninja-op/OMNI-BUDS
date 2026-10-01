package com.omnibuds.core.device

/**
 * Battery readings exactly as they were reported, with unknown kept unknown.
 *
 * Three levels and three charging flags, each independent, because a device may
 * report the case and say nothing about the earbuds, or report a level while saying
 * nothing about whether it is charging. The flags are nullable for the same reason
 * the levels are: `false` is "the device said it is not charging", `null` is "the
 * device did not say", and reading "did not say" as "not charging" is precisely the
 * mistake this model exists to prevent (Phase 1 prompt section 15, master section 23,
 * ADR-P0-016).
 *
 * `0` is a real reading — a flat battery — and is preserved as `0`. Unknown is null,
 * never `0`, never `-1`, never an empty string.
 */
data class BatteryState(
    /** Left earbud level as a percentage, or null when it was not reported. */
    val leftLevel: Int? = null,

    /** Right earbud level as a percentage, or null when it was not reported. */
    val rightLevel: Int? = null,

    /** Charging case level as a percentage, or null when it was not reported. */
    val caseLevel: Int? = null,

    /** Left charging state as reported: null means not reported, not "not charging". */
    val leftCharging: Boolean? = null,

    /** Right charging state as reported: null means not reported, not "not charging". */
    val rightCharging: Boolean? = null,

    /** Case charging state as reported: null means not reported, not "not charging". */
    val caseCharging: Boolean? = null,
) {
    init {
        requireLevelInRange("leftLevel", leftLevel)
        requireLevelInRange("rightLevel", rightLevel)
        requireLevelInRange("caseLevel", caseLevel)
    }

    /** True when at least one level was reported, i.e. there is something to display. */
    val hasAnyKnownLevel: Boolean
        get() = leftLevel != null || rightLevel != null || caseLevel != null

    /**
     * True only when both earbud levels are known and differ.
     *
     * A missing side is not asymmetry: comparing a known level against an unknown one
     * would invent a difference the device never reported (master section 23 lists
     * left/right asymmetry and the unknown state as separate facts).
     */
    val isAsymmetric: Boolean
        get() {
            val left = leftLevel ?: return false
            val right = rightLevel ?: return false
            return left != right
        }

    /** True when nothing at all was reported about power, levels and flags alike. */
    val isEntirelyUnknown: Boolean
        get() = !hasAnyKnownLevel && leftCharging == null && rightCharging == null && caseCharging == null

    /** How many of the six fields hold a reported value. */
    val knownFieldCount: Int
        get() = listOf(leftLevel, rightLevel, caseLevel, leftCharging, rightCharging, caseCharging)
            .count { it != null }

    companion object {
        private val LEVEL_RANGE: IntRange = 0..100

        /** Every field unknown. All six null — never six zeros. */
        fun unknown(): BatteryState = BatteryState(
            leftLevel = null,
            rightLevel = null,
            caseLevel = null,
            leftCharging = null,
            rightCharging = null,
            caseCharging = null,
        )

        private fun requireLevelInRange(name: String, level: Int?) {
            require(level == null || level in LEVEL_RANGE) {
                "$name must be null or within 0..100, was $level"
            }
        }
    }
}
