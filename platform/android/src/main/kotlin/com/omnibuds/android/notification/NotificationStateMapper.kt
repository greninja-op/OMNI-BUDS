package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState

/**
 * Deterministic mapping from engine state to notification presentation.
 *
 * Phase 26 (OB-P26-REQ-003/027): pure function. Honest representation —
 * requested is never presented as confirmed, unknown is never fabricated.
 */
object NotificationStateMapper {

    /**
     * Map a device state to a notification.
     *
     * @param state the device state, or null when no device.
     * @param actionableFeatures feature ids with verified actions, already
     *   filtered to supported + authorized by the caller.
     * @param lastFailure optional failure to surface.
     */
    fun map(
        state: GlobalDeviceState?,
        actionableFeatures: Map<String, ActionMetadata> = emptyMap(),
        lastFailure: String? = null,
    ): NotificationState {
        val base = NotificationState(
            notificationId = NotificationIds.NOTIFICATION_DEVICE_STATUS,
            channelId = NotificationIds.CHANNEL_DEVICE_STATUS,
            title = "OmniBuds",
            text = "",
            actions = emptyList(),
            visible = false,
            kind = NotificationKind.HIDDEN,
            visibility = NotificationVisibility.PRIVATE,
        )

        if (state == null) {
            return base // No eligible device → no notification.
        }

        if (lastFailure != null) {
            return base.copy(
                text = "Last action failed",
                visible = true,
                kind = NotificationKind.FAILED,
                actions = buildActions(state, actionableFeatures),
            )
        }

        if (state.features.executing.values.any { it is OperationStatus.Pending }) {
            return base.copy(
                text = "Applying change…",
                visible = true,
                kind = NotificationKind.PROGRESS,
            )
        }

        val connection = state.connection
        if (connection !is ConnectionState.Connected) {
            return base // Disconnected → remove controls, no stale notification.
        }

        if (state.identity !is IdentityState.Identified) {
            return base.copy(
                text = "Device connected, identifying…",
                visible = true,
                kind = NotificationKind.STATUS,
            )
        }

        val protocol = state.protocol
        if (protocol !is ProtocolState.Resolved || !protocol.compatible) {
            return base.copy(
                text = "Device connected, protocol unavailable",
                visible = true,
                kind = NotificationKind.STATUS,
            )
        }

        if (state.capabilities !is CapabilityState.Ready) {
            return base.copy(
                text = "Discovering capabilities…",
                visible = true,
                kind = NotificationKind.STATUS,
            )
        }

        val actions = buildActions(state, actionableFeatures)
        val batteryText = batteryText(state)
        val text = if (batteryText != null) "Connected · $batteryText" else "Connected"

        return if (actions.isEmpty()) {
            base.copy(text = text, visible = true, kind = NotificationKind.STATUS)
        } else {
            base.copy(
                text = text,
                visible = true,
                kind = NotificationKind.CONTROLS,
                actions = actions,
            )
        }
    }

    private fun buildActions(
        state: GlobalDeviceState,
        actionableFeatures: Map<String, ActionMetadata>,
    ): List<NotificationAction> {
        val ready = state.capabilities as? CapabilityState.Ready ?: return emptyList()
        val connection = state.connection as? ConnectionState.Connected ?: return emptyList()
        return actionableFeatures.mapNotNull { (featureId, meta) ->
            if (featureId !in ready.capabilityIds) return@mapNotNull null
            // Current mode must be known and fresh.
            val observed = state.features.observed[featureId] ?: return@mapNotNull null
            if (!observed.isUsable) return@mapNotNull null
            if (observed.value !in meta.modes) return@mapNotNull null
            NotificationAction(
                actionId = "toggle-$featureId",
                label = meta.actionLabel,
                featureId = featureId,
                deviceId = state.deviceId.value,
                sessionId = connection.sessionId,
            )
        }.take(MAX_ACTIONS)
    }

    private fun batteryText(state: GlobalDeviceState): String? {
        val battery = state.battery as? BatteryState.Known ?: return null
        val level = battery.levelPercent ?: return null // Unknown stays unknown.
        val observation = battery.observation
        if (observation != null) {
            val freshness = observation.freshness
            if (freshness == Freshness.STALE || freshness == Freshness.EXPIRED) {
                return null // Stale readings never shown as current.
            }
        }
        return "Battery $level%"
    }

    /** Small relevant action set — never a notification per feature. */
    const val MAX_ACTIONS = 3
}

/**
 * Metadata for an actionable feature.
 */
data class ActionMetadata(
    /** Display label for the action. */
    val actionLabel: String,
    /** The feature's real mode set, in cycle order. */
    val modes: List<String>,
)
