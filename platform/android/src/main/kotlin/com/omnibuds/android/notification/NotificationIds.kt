package com.omnibuds.android.notification

/**
 * Stable notification and channel identifiers.
 *
 * Phase 26 (OB-P26-REQ-007): documented, stable across sessions.
 */
object NotificationIds {
    /** Primary device-status channel. */
    const val CHANNEL_DEVICE_STATUS = "omnibuds_device_status"

    /** Device status notification id. */
    const val NOTIFICATION_DEVICE_STATUS = 1001

    /** Action for toggling a feature mode. */
    const val ACTION_TOGGLE_FEATURE = "com.omnibuds.android.notification.TOGGLE_FEATURE"

    /** Intent extra keys. */
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_FEATURE_ID = "feature_id"
    const val EXTRA_ACTION_ID = "action_id"
    const val EXTRA_NONCE = "nonce"
}

/**
 * Notification channel configuration (pure data).
 */
data class NotificationChannelConfig(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
) {
    companion object {
        /** Importance values mirror NotificationManager constants without the import. */
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3

        fun deviceStatus(): NotificationChannelConfig = NotificationChannelConfig(
            id = NotificationIds.CHANNEL_DEVICE_STATUS,
            name = "Device status",
            description = "Connection status and verified earbud controls",
            importance = IMPORTANCE_LOW,
        )
    }
}
