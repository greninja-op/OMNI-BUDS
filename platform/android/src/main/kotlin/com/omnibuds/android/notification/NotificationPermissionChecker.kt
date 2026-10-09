package com.omnibuds.android.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Notification permission and channel checks.
 *
 * Phase 26 (OB-P26-REQ-008): POST_NOTIFICATIONS is runtime on API 33+,
 * install-time on older. Never assumes granted.
 */
class NotificationPermissionChecker(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    /**
     * True when the app may post notifications.
     */
    fun canPost(): Boolean {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true // Pre-33: install-time grant.
    }

    /**
     * True when the device-status channel is enabled.
     */
    fun isChannelEnabled(manager: android.app.NotificationManager): Boolean {
        val channel = manager.getNotificationChannel(NotificationIds.CHANNEL_DEVICE_STATUS)
            ?: return true // Not created yet — factory will create it.
        return channel.importance != android.app.NotificationManager.IMPORTANCE_NONE
    }
}
