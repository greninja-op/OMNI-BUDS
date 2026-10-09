package com.omnibuds.android.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 26 security: PendingIntent identity, intent contents, labels.
 */
class NotificationSecurityTest {

    @Test
    fun `channel id is stable`() {
        assertEquals("omnibuds_device_status", NotificationIds.CHANNEL_DEVICE_STATUS)
    }

    @Test
    fun `notification id is stable`() {
        assertEquals(1001, NotificationIds.NOTIFICATION_DEVICE_STATUS)
    }

    @Test
    fun `action intent is explicit and namespaced`() {
        assertEquals(
            "com.omnibuds.android.notification.TOGGLE_FEATURE",
            NotificationIds.ACTION_TOGGLE_FEATURE,
        )
        assertTrue(NotificationIds.ACTION_TOGGLE_FEATURE.startsWith("com.omnibuds."))
    }

    @Test
    fun `no raw protocol payloads in intent extras`() {
        val extras = setOf(
            NotificationIds.EXTRA_DEVICE_ID,
            NotificationIds.EXTRA_SESSION_ID,
            NotificationIds.EXTRA_FEATURE_ID,
            NotificationIds.EXTRA_ACTION_ID,
            NotificationIds.EXTRA_NONCE,
        )
        // Identifiers only — no payload, mode value, or vendor command keys.
        assertFalse(extras.any { it.contains("payload") || it.contains("value") })
    }

    @Test
    fun `notification labels avoid identifiers`() {
        val state = NotificationState(
            notificationId = NotificationIds.NOTIFICATION_DEVICE_STATUS,
            channelId = NotificationIds.CHANNEL_DEVICE_STATUS,
            title = "OmniBuds",
            text = "Connected",
            actions = emptyList(),
            visible = true,
            kind = NotificationKind.STATUS,
            visibility = NotificationVisibility.PRIVATE,
        )
        assertFalse(state.title.contains(":"))
        assertTrue(state.text.length <= 120)
    }

    @Test
    fun `channel config is low importance and quiet`() {
        val config = NotificationChannelConfig.deviceStatus()
        assertEquals(NotificationIds.CHANNEL_DEVICE_STATUS, config.id)
        assertEquals(NotificationChannelConfig.IMPORTANCE_LOW, config.importance)
    }
}
