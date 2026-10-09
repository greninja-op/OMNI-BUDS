package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState

/**
 * Multi-device target resolution policy.
 *
 * Phase 25 (OB-P25-REQ-007/008): never silently first-matches. Prefers an
 * explicit selection; verifies eligibility; ambiguous → refuse.
 */
object TileTargetResolver {

    /**
     * Resolve the target device for a tile action.
     *
     * @param devices all tracked device states.
     * @param selectedId explicitly selected device, or null.
     */
    fun resolve(
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
        selectedId: GlobalDeviceId?,
    ): TargetResolution {
        if (devices.isEmpty()) {
            return TargetResolution.NoDevice("no tracked devices")
        }

        // Explicit selection preferred — but must still be eligible.
        if (selectedId != null) {
            val selected = devices[selectedId]
                ?: return TargetResolution.Unresolvable(
                    "selected device is no longer tracked",
                )
            return if (isEligible(selected)) {
                TargetResolution.Resolved(selectedId)
            } else {
                TargetResolution.Unresolvable(
                    "selected device is not connected and eligible",
                )
            }
        }

        val eligible = devices.values.filter(::isEligible)
        return when {
            eligible.isEmpty() -> TargetResolution.Unresolvable(
                "no connected and eligible device",
            )
            eligible.size == 1 -> TargetResolution.Resolved(eligible[0].deviceId)
            else -> TargetResolution.Ambiguous(
                eligible.map { it.deviceId },
                "multiple eligible devices and no selection",
            )
        }
    }

    /** Eligible = connected. Further checks happen at dispatch time. */
    private fun isEligible(state: GlobalDeviceState): Boolean =
        state.connection is ConnectionState.Connected
}

/**
 * The result of target resolution.
 */
sealed interface TargetResolution {
    /** Exactly one target. */
    data class Resolved(val deviceId: GlobalDeviceId) : TargetResolution

    /** No devices at all. */
    data class NoDevice(val reason: String) : TargetResolution

    /** Multiple candidates; hardware action refused. */
    data class Ambiguous(val candidates: List<GlobalDeviceId>, val reason: String) : TargetResolution

    /** A target was requested but is not usable. */
    data class Unresolvable(val reason: String) : TargetResolution
}
