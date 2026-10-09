package com.omnibuds.core.battery

import com.omnibuds.core.audio.CodecEvidence

/**
 * Resolves conflicting battery reports from multiple sources.
 *
 * Phase 16 (OB-P16-REQ-014): documented precedence —
 * 1. Fresher observations beat staler ones.
 * 2. Among equally fresh observations, verified vendor protocol beats
 *    platform inference (but never blindly: the conflict is recorded).
 * 3. When evidence cannot resolve safely, the conflict is reported in the
 *    snapshot warnings and the fresher value is kept — never a silent
 *    arbitrary pick.
 */
object BatteryConflictResolver {

    /**
     * Choose between two component states for the same component.
     * @return the winner and an optional conflict warning (null when no
     * meaningful conflict existed).
     */
    fun resolve(
        existing: ComponentBatteryState,
        incoming: ComponentBatteryState,
        incomingSourceRank: Int,
        existingSourceRank: Int,
    ): Resolution {
        // No conflict when one side has no observation.
        if (existing.observedAtMillis == null) {
            return Resolution(incoming, null)
        }
        if (incoming.observedAtMillis == null) {
            return Resolution(existing, null)
        }
        // Fresher wins.
        if (incoming.observedAtMillis > existing.observedAtMillis) {
            val warning = if (existing.level != null && incoming.level != null &&
                existing.level != incoming.level
            ) {
                "Conflicting ${existing.component} levels: " +
                    "existing=${existing.level} vs incoming=${incoming.level}; " +
                    "kept fresher observation."
            } else {
                null
            }
            return Resolution(incoming, warning)
        }
        if (incoming.observedAtMillis < existing.observedAtMillis) {
            return Resolution(existing, null)
        }
        // Same timestamp: higher-ranked source wins, conflict recorded.
        return if (incomingSourceRank >= existingSourceRank) {
            val warning = if (existing.level != incoming.level) {
                "Same-timestamp conflict for ${existing.component}: kept " +
                    "higher-ranked source."
            } else {
                null
            }
            Resolution(incoming, warning)
        } else {
            Resolution(existing, "Same-timestamp conflict for ${existing.component}: kept existing source.")
        }
    }

    /** Source ranks: higher = more authoritative. Documented, not blind. */
    object SourceRank {
        const val UNKNOWN = 0
        const val INFERRED = 1
        const val ANDROID_PLATFORM = 2
        const val VERIFIED_PROTOCOL = 3
    }
}

/** The outcome of conflict resolution. */
data class Resolution(
    val winner: ComponentBatteryState,
    /** Non-null when a real conflict was resolved and must be surfaced. */
    val warning: String?,
)
