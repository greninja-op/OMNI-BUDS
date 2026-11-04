package com.omnibuds.core.presentation.battery

import com.omnibuds.core.globalstate.BatteryState

/**
 * Single battery component reading (e.g. left bud, right bud, charging case, or whole device).
 */
enum class BatteryComponentKind { LEFT, RIGHT, CASE, DEVICE }

data class UnifiedBatteryComponent(
    val componentName: String,
    val levelPercent: Int?,
    val isCharging: Boolean?,
    val isPresent: Boolean = true,
    val kind: BatteryComponentKind = BatteryComponentKind.DEVICE,
) {
    init {
        levelPercent?.let {
            require(it in 0..100) { "levelPercent must be between 0 and 100 or null, was $it" }
        }
    }

    val displayString: String
        get() = when {
            levelPercent != null -> "$levelPercent%${if (isCharging == true) " ⚡" else ""}"
            else -> "Unknown"
        }
}

/**
 * Honest, evidence-backed battery and power presentation model.
 * Preserves nullability strictly: never substitutes 0% for missing telemetry.
 */
data class UnifiedBatteryModel(
    val isAvailable: Boolean = false,
    val components: List<UnifiedBatteryComponent> = emptyList(),
    val overallPercent: Int? = null,
    val isCharging: Boolean? = null,
    val lastUpdatedMillis: Long? = null,
    val isStale: Boolean = false,
    val unavailableReason: String? = null,
) {
    val hasAnyData: Boolean
        get() = overallPercent != null || components.any { it.levelPercent != null }

    val summaryText: String
        get() {
            if (!isAvailable || !hasAnyData) {
                return unavailableReason ?: "Battery telemetry unavailable"
            }
            if (components.isNotEmpty()) {
                val parts = components.filter { it.levelPercent != null }.map { "${it.componentName}: ${it.displayString}" }
                if (parts.isNotEmpty()) {
                    val suffix = if (isStale) " (stale)" else ""
                    return parts.joinToString(" • ") + suffix
                }
            }
            val base = overallPercent?.let { "$it%${if (isCharging == true) " ⚡" else ""}" } ?: "Unknown"
            val suffix = if (isStale) " (stale)" else ""
            return base + suffix
        }

    val accessibilityAnnouncement: String
        get() {
            if (!isAvailable || !hasAnyData) {
                return unavailableReason ?: "Battery level not reported by device"
            }
            val parts = mutableListOf<String>()
            if (components.isNotEmpty()) {
                for (comp in components) {
                    comp.levelPercent?.let {
                        parts.add("${comp.componentName} $it percent${if (comp.isCharging == true) ", charging" else ""}")
                    }
                }
            } else overallPercent?.let {
                parts.add("Battery $it percent${if (isCharging == true) ", charging" else ""}")
            }
            if (isStale) parts.add("reading may be outdated")
            return parts.joinToString(", ")
        }

    companion object {
        const val STALE_THRESHOLD_MS: Long = 60_000L

        fun unavailable(
            reason: String = "Battery telemetry unavailable: not exposed by this device or host OS adapter.",
        ): UnifiedBatteryModel = UnifiedBatteryModel(
            isAvailable = false,
            components = emptyList(),
            overallPercent = null,
            isCharging = null,
            lastUpdatedMillis = null,
            isStale = false,
            unavailableReason = reason,
        )

        fun fromCoreState(
            batteryState: BatteryState,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = STALE_THRESHOLD_MS,
        ): UnifiedBatteryModel = when (batteryState) {
            is BatteryState.Unknown -> unavailable()
            is BatteryState.Known -> {
                val observedAt = batteryState.observation?.provenance?.observedAtMillis
                val isStale = if (observedAt != null) {
                    (currentEpochMillis - observedAt) > staleThresholdMillis
                } else {
                    false
                }

                // Per-component readings map by kind — never by name
                // matching, and never derived from the aggregate.
                val components = batteryState.components.map { component ->
                    val (name, kind) = when (component.component) {
                        BatteryState.Component.LEFT_EARBUD ->
                            "Left" to BatteryComponentKind.LEFT
                        BatteryState.Component.RIGHT_EARBUD ->
                            "Right" to BatteryComponentKind.RIGHT
                        BatteryState.Component.CHARGING_CASE ->
                            "Case" to BatteryComponentKind.CASE
                    }
                    UnifiedBatteryComponent(
                        componentName = name,
                        levelPercent = component.levelPercent,
                        isCharging = component.charging,
                        kind = kind,
                    )
                }

                val hasData = batteryState.levelPercent != null ||
                    components.any { it.levelPercent != null }
                UnifiedBatteryModel(
                    isAvailable = hasData,
                    components = components,
                    overallPercent = batteryState.levelPercent,
                    isCharging = batteryState.charging,
                    lastUpdatedMillis = observedAt,
                    isStale = isStale,
                    unavailableReason = if (!hasData) "Battery level was not reported." else null,
                )
            }
        }

        fun fromComponents(
            left: Int?,
            right: Int?,
            case: Int?,
            leftCharging: Boolean? = null,
            rightCharging: Boolean? = null,
            caseCharging: Boolean? = null,
            observedAt: Long? = null,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = STALE_THRESHOLD_MS,
        ): UnifiedBatteryModel {
            val isStale = if (observedAt != null) {
                (currentEpochMillis - observedAt) > staleThresholdMillis
            } else {
                false
            }

            val components = mutableListOf<UnifiedBatteryComponent>()
            if (left != null) components.add(UnifiedBatteryComponent("Left", left, leftCharging, kind = BatteryComponentKind.LEFT))
            if (right != null) components.add(UnifiedBatteryComponent("Right", right, rightCharging, kind = BatteryComponentKind.RIGHT))
            if (case != null) components.add(UnifiedBatteryComponent("Case", case, caseCharging, kind = BatteryComponentKind.CASE))

            val hasAny = components.isNotEmpty()
            return UnifiedBatteryModel(
                isAvailable = hasAny,
                components = components,
                // Components are not an aggregate: with no device-level
                // reading, overall stays unknown instead of borrowing
                // the first component's value.
                overallPercent = null,
                isCharging = components.any { it.isCharging == true },
                lastUpdatedMillis = observedAt,
                isStale = isStale,
                unavailableReason = if (!hasAny) "No component battery telemetry reported." else null,
            )
        }
    }
}
