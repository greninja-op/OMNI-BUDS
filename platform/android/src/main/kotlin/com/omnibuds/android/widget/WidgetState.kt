package com.omnibuds.android.widget

/**
 * Platform-independent widget presentation state.
 *
 * Phase 27 (OB-P27-REQ-003): deterministic mapping target. No Android
 * dependencies so the mapping is fully unit-testable.
 */
data class WidgetState(
    /** App-widget instance id. */
    val widgetId: Int,
    /** Target device, or null when none. */
    val deviceId: String?,
    /** Session at render time, or null. */
    val sessionId: String?,
    /** Connection/status line. */
    val statusText: String,
    /** Battery readings, or null when unknown. */
    val battery: WidgetBattery?,
    /** Actions to expose (small set). */
    val actions: List<WidgetAction>,
    /** Machine-readable kind. */
    val kind: WidgetKind,
)

/**
 * Battery presentation — left/right/case kept separate, nullable.
 */
data class WidgetBattery(
    val leftPercent: Int?,
    val rightPercent: Int?,
    val casePercent: Int?,
)

/**
 * A widget action.
 */
data class WidgetAction(
    /** Stable action id, e.g. "toggle-anc". */
    val actionId: String,
    /** Display label. */
    val label: String,
    /** Content description for accessibility. */
    val contentDescription: String,
    /** Feature id this acts on. */
    val featureId: String,
)

/**
 * Widget kinds.
 */
enum class WidgetKind {
    /** Initial load. */
    LOADING,

    /** No eligible device — honest unavailable state. */
    UNAVAILABLE,

    /** Status only, no actions. */
    STATUS,

    /** Status with verified actions. */
    CONTROLS,

    /** Operation in progress. */
    PROGRESS,

    /** Last operation failed. */
    FAILED,
}
