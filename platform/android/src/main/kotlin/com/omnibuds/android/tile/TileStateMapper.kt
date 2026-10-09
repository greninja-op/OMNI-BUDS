package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState

/**
 * Deterministic mapping from engine state to tile presentation.
 *
 * Phase 25 (OB-P25-REQ-003): pure function, no Android dependencies.
 * Unknown is never rendered as a concrete hardware value.
 *
 * @param controllableFeatureIds feature ids the tile may offer as actions,
 *   already filtered to verified + supported by the caller.
 * @param lastFailure optional failure to surface.
 */
object TileStateMapper {

    fun map(
        state: GlobalDeviceState?,
        controllableFeatureIds: Set<String> = emptySet(),
        lastFailure: String? = null,
    ): TileState {
        if (state == null) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "No device",
                clickable = false,
                kind = TileKind.NO_DEVICE,
            )
        }

        // Failure surfaces honestly, without claiming the requested state.
        if (lastFailure != null) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Action failed",
                clickable = isClickable(state, controllableFeatureIds),
                kind = TileKind.FAILED,
            )
        }

        // Operation in flight.
        if (state.features.executing.values.any { it is OperationStatus.Pending }) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Working…",
                clickable = false,
                kind = TileKind.OPERATION_PENDING,
            )
        }

        return when (val conn = state.connection) {
            is ConnectionState.Disconnected -> TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Disconnected",
                clickable = false,
                kind = TileKind.DISCONNECTED,
            )
            is ConnectionState.Connecting -> TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Connecting…",
                clickable = false,
                kind = TileKind.UNAVAILABLE,
            )
            is ConnectionState.Disconnecting -> TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Disconnecting…",
                clickable = false,
                kind = TileKind.UNAVAILABLE,
            )
            is ConnectionState.Connected -> mapConnected(state, controllableFeatureIds)
        }
    }

    private fun mapConnected(
        state: GlobalDeviceState,
        controllableFeatureIds: Set<String>,
    ): TileState {
        // Identity must be established.
        if (state.identity !is IdentityState.Identified) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Identifying…",
                clickable = false,
                kind = TileKind.UNIDENTIFIED,
            )
        }

        // Protocol must be resolved and compatible.
        val protocol = state.protocol
        if (protocol !is ProtocolState.Resolved || !protocol.compatible) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Protocol unavailable",
                clickable = false,
                kind = TileKind.UNAVAILABLE,
            )
        }

        // Capabilities must be ready.
        when (state.capabilities) {
            is CapabilityState.NotDiscovered,
            is CapabilityState.Discovering -> return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Discovering…",
                clickable = false,
                kind = TileKind.DISCOVERING,
            )
            is CapabilityState.Failed -> return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "Unavailable",
                clickable = false,
                kind = TileKind.UNAVAILABLE,
            )
            is CapabilityState.Ready -> Unit
        }

        // Stale observations → honest unknown, not a guessed value.
        if (hasStaleCriticalState(state)) {
            return TileState(
                active = false,
                label = "OmniBuds",
                subtitle = "State unknown",
                clickable = false,
                kind = TileKind.UNKNOWN,
            )
        }

        val availableActions = controllableFeatureIds.filter { id ->
            (state.capabilities as? CapabilityState.Ready)?.capabilityIds?.contains(id) == true
        }
        val batteryLabel = batterySubtitle(state)

        return if (availableActions.isEmpty()) {
            TileState(
                active = true,
                label = "OmniBuds",
                subtitle = batteryLabel ?: "Connected",
                clickable = false,
                kind = TileKind.READY_NO_ACTION,
            )
        } else {
            TileState(
                active = true,
                label = "OmniBuds",
                subtitle = batteryLabel ?: "Tap for options",
                clickable = true,
                kind = TileKind.READY_WITH_ACTION,
            )
        }
    }

    private fun hasStaleCriticalState(state: GlobalDeviceState): Boolean {
        val observed = state.features.observed.values
        return observed.any { it.freshness == Freshness.EXPIRED }
    }

    private fun batterySubtitle(state: GlobalDeviceState): String? {
        val battery = state.battery as? BatteryState.Known ?: return null
        val level = battery.levelPercent ?: return null
        // Never interpret missing battery as zero — null level means unknown.
        return "Battery $level%"
    }

    private fun isClickable(state: GlobalDeviceState, controllableFeatureIds: Set<String>): Boolean =
        map(state, controllableFeatureIds, lastFailure = null).clickable
}
