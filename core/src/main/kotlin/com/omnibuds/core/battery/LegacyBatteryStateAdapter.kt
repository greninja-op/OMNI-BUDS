package com.omnibuds.core.battery

import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.device.BatteryState as LegacyBatteryState

/**
 * Adapts the Phase 1 [LegacyBatteryState] (flat left/right/case struct) into
 * Phase 16 per-component [BatteryUpdate]s.
 *
 * Mapping rules (no inference):
 * - Level int? → BatteryLevel (validated; invalid values are dropped and
 *   reported, never zeroed).
 * - Charging Boolean? → CHARGING / NOT_CHARGING / UNKNOWN. There is no FULL
 *   in the legacy model — it is never manufactured.
 * - Each reported field becomes its own update; omitted fields stay omitted.
 */
object LegacyBatteryStateAdapter {

    /**
     * Convert a legacy state into per-component updates.
     * @return updates for components the legacy state actually reported,
     * plus warnings for any invalid values that were dropped.
     */
    fun toUpdates(
        legacy: LegacyBatteryState,
        sessionGeneration: Long,
        observedAtMillis: Long,
        evidence: CodecEvidence?,
        sourceName: String,
    ): Adaptation {
        val updates = mutableListOf<BatteryUpdate>()
        val warnings = mutableListOf<String>()

        fun chargingOf(flag: Boolean?): UpdateField<ChargingState> = when (flag) {
            true -> UpdateField.Set(ChargingState.CHARGING)
            false -> UpdateField.Set(ChargingState.NOT_CHARGING)
            null -> UpdateField.Omitted
        }

        fun levelOf(raw: Int?, name: String): UpdateField<BatteryLevel> {
            if (raw == null) return UpdateField.Omitted
            val level = BatteryLevel.parseLenient(raw)
            if (level == null) {
                warnings.add("Dropped invalid $name battery value: $raw (kept as unknown)")
                return UpdateField.ExplicitUnknown
            }
            return UpdateField.Set(level)
        }

        val components = listOf(
            Triple(BatteryComponent.LEFT_EARBUD, legacy.leftLevel, legacy.leftCharging),
            Triple(BatteryComponent.RIGHT_EARBUD, legacy.rightLevel, legacy.rightCharging),
            Triple(BatteryComponent.CHARGING_CASE, legacy.caseLevel, legacy.caseCharging),
        )
        for ((component, rawLevel, rawCharging) in components) {
            val levelField = levelOf(rawLevel, component.name.lowercase())
            val chargingField = chargingOf(rawCharging)
            if (levelField is UpdateField.Omitted && chargingField is UpdateField.Omitted) {
                continue // Nothing reported for this component.
            }
            updates.add(
                BatteryUpdate(
                    component = component,
                    sessionGeneration = sessionGeneration,
                    level = levelField,
                    charging = chargingField,
                    observedAtMillis = observedAtMillis,
                    evidence = evidence,
                    sourceName = sourceName,
                ),
            )
        }
        return Adaptation(updates, warnings)
    }
}

/** Updates plus warnings from adapting a legacy battery state. */
data class Adaptation(
    val updates: List<BatteryUpdate>,
    val warnings: List<String>,
)
