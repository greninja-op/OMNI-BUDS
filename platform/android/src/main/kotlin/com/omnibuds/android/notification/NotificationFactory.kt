package com.omnibuds.android.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Builds Android notifications from [NotificationState].
 *
 * Phase 26: platform APIs only (minSdk 26 → NotificationChannel is
 * always available; no androidx dependency needed).
 */
class NotificationFactory(
    private val context: Context,
) {
    /**
     * Ensure the device-status channel exists.
     */
    fun ensureChannels(manager: NotificationManager) {
        val config = NotificationChannelConfig.deviceStatus()
        val channel = NotificationChannel(
            config.id,
            config.name,
            config.importance,
        ).apply {
            description = config.description
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Build a notification from the presentation state.
     */
    fun build(state: NotificationState): Notification {
        val builder = Notification.Builder(context, state.channelId)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(state.title)
            .setContentText(state.text)
            .setOngoing(false)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setVisibility(
                when (state.visibility) {
                    NotificationVisibility.PUBLIC -> Notification.VISIBILITY_PUBLIC
                    NotificationVisibility.PRIVATE -> Notification.VISIBILITY_PRIVATE
                },
            )

        state.actions.forEachIndexed { index, action ->
            builder.addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        android.R.drawable.ic_media_play,
                    ),
                    action.label,
                    actionPendingIntent(action, index),
                ).build(),
            )
        }

        return builder.build()
    }

    /**
     * Build an immutable PendingIntent for an action.
     *
     * Request codes are derived from the action identity so actions for
     * different devices/features cannot collide.
     */
    fun actionPendingIntent(action: NotificationAction, index: Int): PendingIntent {
        val intent = Intent(context, OmniBudsNotificationReceiver::class.java).apply {
            this.action = NotificationIds.ACTION_TOGGLE_FEATURE
            putExtra(NotificationIds.EXTRA_ACTION_ID, action.actionId)
            putExtra(NotificationIds.EXTRA_DEVICE_ID, action.deviceId)
            putExtra(NotificationIds.EXTRA_SESSION_ID, action.sessionId)
            putExtra(NotificationIds.EXTRA_FEATURE_ID, action.featureId)
            putExtra(NotificationIds.EXTRA_NONCE, java.util.UUID.randomUUID().toString())
        }
        val requestCode = (action.deviceId + action.featureId + index).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
