package com.omnibuds.android.notification

/**
 * Platform-independent notification presentation state.
 *
 * Phase 26 (OB-P26-REQ-003): deterministic mapping target. No Android
 * dependencies so the mapping is fully unit-testable.
 */
data class NotificationState(
    /** Stable notification id. */
    val notificationId: Int,
    /** Channel id. */
    val channelId: String,
    /** Title — concise, never sensitive. */
    val title: String,
    /** Body text — honest, never fabricated. */
    val text: String,
    /** Actions to expose (small set). */
    val actions: List<NotificationAction>,
    /** Whether this notification should be shown at all. */
    val visible: Boolean,
    /** Machine-readable kind. */
    val kind: NotificationKind,
    /** Lock-screen visibility. */
    val visibility: NotificationVisibility,
)

/**
 * A notification action.
 */
data class NotificationAction(
    /** Stable action id, e.g. "toggle-anc". */
    val actionId: String,
    /** Display label. */
    val label: String,
    /** Feature id this acts on. */
    val featureId: String,
    /** Target device. */
    val deviceId: String,
    /** Session at notification build time. */
    val sessionId: String?,
)

/**
 * Notification kinds.
 */
enum class NotificationKind {
    /** No notification should be shown. */
    HIDDEN,

    /** Status only, no actions. */
    STATUS,

    /** Status with verified actions. */
    CONTROLS,

    /** Operation in progress. */
    PROGRESS,

    /** Last operation failed. */
    FAILED,
}

/**
 * Lock-screen visibility.
 */
enum class NotificationVisibility {
    /** Show fully. */
    PUBLIC,

    /** Redact sensitive content. */
    PRIVATE,
}
