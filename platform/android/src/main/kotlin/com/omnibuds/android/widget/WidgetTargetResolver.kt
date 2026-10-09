package com.omnibuds.android.widget

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState

/**
 * Explicit widget target resolution.
 *
 * Phase 27 (OB-P27-REQ-007): consistent with Phases 24–26. Never picks the
 * first device. Prefers an explicitly selected eligible device; a single
 * eligible device resolves; multiple without selection → typed ambiguity.
 */
object WidgetTargetResolver {

    /**
     * Resolve the target for a widget instance.
     */
    fun resolve(
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
        selected: GlobalDeviceId?,
        bound: GlobalDeviceId?,
    ): WidgetTargetResolution {
        if (devices.isEmpty()) {
            return WidgetTargetResolution.None("no devices")
        }

        // A bound instance keeps its target while eligible.
        if (bound != null) {
            val state = devices[bound]
            if (state != null && state.connection is ConnectionState.Connected) {
                return WidgetTargetResolution.Target(bound)
            }
            // Bound target gone → fall through to re-resolve honestly.
        }

        // Explicit selection wins when eligible.
        if (selected != null) {
            val state = devices[selected]
            if (state != null && state.connection is ConnectionState.Connected) {
                return WidgetTargetResolution.Target(selected)
            }
        }

        val eligible = devices.values.filter {
            it.connection is ConnectionState.Connected
        }
        return when {
            eligible.size == 1 -> WidgetTargetResolution.Target(eligible[0].deviceId)
            eligible.isEmpty() -> WidgetTargetResolution.None("no connected devices")
            else -> WidgetTargetResolution.Ambiguous(eligible.map { it.deviceId.value })
        }
    }
}

/**
 * Target resolution outcomes.
 */
sealed interface WidgetTargetResolution {
    /** An explicit, unambiguous target. */
    data class Target(val deviceId: GlobalDeviceId) : WidgetTargetResolution

    /** No eligible device. */
    data class None(val reason: String) : WidgetTargetResolution

    /** Multiple eligible devices without an explicit selection. */
    data class Ambiguous(val candidates: List<String>) : WidgetTargetResolution
}
