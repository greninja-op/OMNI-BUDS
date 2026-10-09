package com.omnibuds.android.widget

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState

/**
 * Deterministic mapping from engine state to widget presentation.
 *
 * Phase 27 (OB-P27-REQ-003/004/018): pure function. Battery unknown stays
 * unknown; requested never presented as confirmed.
 */
object WidgetStateMapper {

    /**
     * Map a device state to a widget state.
     *
     * @param widgetId the widget instance.
     * @param state the device state, or null when no target.
     * @param actionableFeatures feature ids with verified actions, already
     *   filtered to supported + authorized by the caller.
     * @param lastFailure optional failure to surface.
     */
    fun map(
        widgetId: Int,
        state: GlobalDeviceState?,
        actionableFeatures: Map<String, WidgetActionMetadata> = emptyMap(),
        lastFailure: String? = null,
    ): WidgetState {
        val base = WidgetState(
            widgetId = widgetId,
            deviceId = null,
            sessionId = null,
            statusText = "No device",
            battery = null,
            actions = emptyList(),
            kind = WidgetKind.UNAVAILABLE,
        )

        if (state == null) return base

        val deviceId = state.deviceId.value
        val connection = state.connection as? ConnectionState.Connected

        if (lastFailure != null) {
            return base.copy(
                deviceId = deviceId,
                sessionId = connection?.sessionId,
                statusText = "Action failed",
                battery = batteryOf(state),
                kind = WidgetKind.FAILED,
                actions = buildActions(state, actionableFeatures),
            )
        }

        if (state.features.executing.values.any { it is OperationStatus.Pending }) {
            return base.copy(
                deviceId = deviceId,
                sessionId = connection?.sessionId,
                statusText = "Applying change…",
                battery = batteryOf(state),
                kind = WidgetKind.PROGRESS,
            )
        }

        if (connection == null) {
            return base.copy(deviceId = deviceId, statusText = "Disconnected")
        }

        if (state.identity !is IdentityState.Identified) {
            return base.copy(
                deviceId = deviceId,
                sessionId = connection.sessionId,
                statusText = "Identifying…",
                kind = WidgetKind.STATUS,
            )
        }

        val protocol = state.protocol
        if (protocol !is ProtocolState.Resolved || !protocol.compatible) {
            return base.copy(
                deviceId = deviceId,
                sessionId = connection.sessionId,
                statusText = "Protocol unavailable",
                kind = WidgetKind.STATUS,
            )
        }

        if (state.capabilities !is CapabilityState.Ready) {
            return base.copy(
                deviceId = deviceId,
                sessionId = connection.sessionId,
                statusText = "Discovering…",
                kind = WidgetKind.STATUS,
            )
        }

        val actions = buildActions(state, actionableFeatures)
        return if (actions.isEmpty()) {
            base.copy(
                deviceId = deviceId,
                sessionId = connection.sessionId,
                statusText = "Connected",
                battery = batteryOf(state),
                kind = WidgetKind.STATUS,
            )
        } else {
            base.copy(
                deviceId = deviceId,
                sessionId = connection.sessionId,
                statusText = "Connected",
                battery = batteryOf(state),
                kind = WidgetKind.CONTROLS,
                actions = actions,
            )
        }
    }

    private fun buildActions(
        state: GlobalDeviceState,
        actionableFeatures: Map<String, WidgetActionMetadata>,
    ): List<WidgetAction> {
        val ready = state.capabilities as? CapabilityState.Ready ?: return emptyList()
        return actionableFeatures.mapNotNull { (featureId, meta) ->
            if (featureId !in ready.capabilityIds) return@mapNotNull null
            val observed = state.features.observed[featureId] ?: return@mapNotNull null
            if (!observed.isUsable) return@mapNotNull null
            if (observed.value !in meta.modes) return@mapNotNull null
            WidgetAction(
                actionId = "toggle-$featureId",
                label = meta.actionLabel,
                contentDescription = "${meta.actionLabel}. Current: ${observed.value}.",
                featureId = featureId,
            )
        }.take(MAX_ACTIONS)
    }

    private fun batteryOf(state: GlobalDeviceState): WidgetBattery? {
        val battery = state.battery as? BatteryState.Known ?: return null
        val observation = battery.observation
        if (observation != null) {
            val freshness = observation.freshness
            if (freshness == Freshness.STALE || freshness == Freshness.EXPIRED) {
                return null // Stale readings never shown as current.
            }
        }
        val level = battery.levelPercent ?: return null
        // Single aggregate level shown as the combined reading; per-bud
        // split only when the engine exposes it (it currently exposes one).
        return WidgetBattery(leftPercent = level, rightPercent = null, casePercent = null)
    }

    /** Small relevant action set. */
    const val MAX_ACTIONS = 2
}

/**
 * Metadata for an actionable feature.
 */
data class WidgetActionMetadata(
    /** Display label for the action. */
    val actionLabel: String,
    /** The feature's real mode set, in cycle order. */
    val modes: List<String>,
)
