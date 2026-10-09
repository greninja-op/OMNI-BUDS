package com.omnibuds.core.battery

import com.omnibuds.core.audio.CodecEvidence

/**
 * Explicit partial-update semantics.
 *
 * Phase 16 (OB-P16-REQ-012): four distinct meanings —
 * - [Omitted]: the source said nothing about this field in this update.
 * - [ExplicitUnknown]: the source explicitly reported "unknown".
 * - [Set]: the source reported a concrete value (including zero / NOT_CHARGING).
 *
 * Nullable-field merging cannot express these; this sealed type can.
 */
sealed interface UpdateField<out T> {

    /** The source said nothing about this field; preserve existing state. */
    data object Omitted : UpdateField<Nothing>

    /** The source explicitly reported the value as unknown. */
    data object ExplicitUnknown : UpdateField<Nothing>

    /** The source reported a concrete value. */
    data class Set<T>(val value: T) : UpdateField<T>
}

/**
 * A partial battery observation from one source.
 *
 * Only the fields the source actually reported are [UpdateField.Set] or
 * [UpdateField.ExplicitUnknown]; everything else is [UpdateField.Omitted]
 * and must not disturb existing state.
 */
data class BatteryUpdate(
    val component: BatteryComponent,
    val sessionGeneration: Long,
    val level: UpdateField<BatteryLevel> = UpdateField.Omitted,
    val charging: UpdateField<ChargingState> = UpdateField.Omitted,
    val observedAtMillis: Long,
    val evidence: CodecEvidence?,
    val sourceName: String,
) {
    init {
        require(component != BatteryComponent.UNKNOWN) {
            "Updates must identify their component"
        }
        require(observedAtMillis >= 0) { "observedAtMillis must be non-negative" }
        require(sourceName.isNotBlank()) { "sourceName must identify the source" }
    }

    /** True when this update carries no information at all. */
    val isEmpty: Boolean
        get() = level is UpdateField.Omitted && charging is UpdateField.Omitted
}
