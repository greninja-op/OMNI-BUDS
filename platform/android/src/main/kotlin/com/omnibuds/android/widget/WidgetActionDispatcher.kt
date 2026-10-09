package com.omnibuds.android.widget

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState

/**
 * A validated widget action request.
 */
data class ValidatedWidgetAction(
    val widgetId: Int,
    val deviceId: GlobalDeviceId,
    val sessionId: String,
    val featureId: String,
    val nextValue: String,
    val basedOn: String,
)

/**
 * The outcome of widget action validation/dispatch.
 */
sealed interface WidgetActionOutcome {
    /** Dispatched to the feature engine. */
    data class Dispatched(val action: ValidatedWidgetAction) : WidgetActionOutcome

    /** Rejected; [reason] explains why. */
    data class Rejected(val reason: WidgetActionRejection) : WidgetActionOutcome
}

/**
 * Typed widget action rejection reasons.
 */
sealed interface WidgetActionRejection {
    data class Malformed(val detail: String) : WidgetActionRejection
    data class InvalidWidgetId(val widgetId: Int) : WidgetActionRejection
    data class UnknownDevice(val deviceId: String) : WidgetActionRejection
    data class TargetMismatch(val expected: String, val got: String) : WidgetActionRejection
    data class StaleSession(val expected: String?, val got: String?) : WidgetActionRejection
    data class Unsupported(val featureId: String) : WidgetActionRejection
    data class StaleState(val featureId: String) : WidgetActionRejection
    data class UnknownState(val featureId: String) : WidgetActionRejection
    data class AccessDenied(val reason: String) : WidgetActionRejection
    data class Duplicate(val featureId: String) : WidgetActionRejection
    data class Disconnected(val deviceId: String) : WidgetActionRejection
}

/**
 * Secure widget action dispatcher.
 *
 * Phase 27 (OB-P27-REQ-009/011): every action is treated as external input.
 * The widget instance is bound to its target at render time; execution
 * revalidates the binding. Cross-instance leakage is rejected.
 */
class WidgetActionDispatcher(
    private val accessCheck: suspend (deviceId: GlobalDeviceId, featureId: String) -> Boolean,
    private val writeExecutor: suspend (action: ValidatedWidgetAction) -> Boolean,
    /** Current widget-id → device binding, from the coordinator. */
    private val bindingOf: (widgetId: Int) -> GlobalDeviceId?,
) {
    private val inFlight = mutableSetOf<String>()
    private val lock = Any()
    private val seenNonces = LinkedHashSet<String>()
    private val seenLock = Any()

    companion object {
        const val MAX_NONCES = 1000
    }

    /**
     * Validate and dispatch an action request.
     *
     * @param request the raw request (from a PendingIntent).
     * @param devices current device states.
     * @param modes the feature's real mode set.
     */
    suspend fun dispatch(
        request: WidgetActionRequest,
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
        modes: Map<String, List<String>>,
    ): WidgetActionOutcome {
        // 1. Parse and validate the request shape.
        if (request.widgetId < 0 || request.deviceId.isBlank() ||
            request.featureId.isBlank() || request.nonce.isBlank()
        ) {
            return WidgetActionOutcome.Rejected(WidgetActionRejection.Malformed("blank fields"))
        }

        // 2. Replay guard.
        synchronized(seenLock) {
            if (request.nonce in seenNonces) {
                return WidgetActionOutcome.Rejected(WidgetActionRejection.Duplicate(request.featureId))
            }
            seenNonces.add(request.nonce)
            if (seenNonces.size > MAX_NONCES) seenNonces.remove(seenNonces.first())
        }

        // 3. Widget-instance binding — the instance's target is authoritative.
        val bound = bindingOf(request.widgetId)
            ?: return WidgetActionOutcome.Rejected(WidgetActionRejection.InvalidWidgetId(request.widgetId))
        if (bound.value != request.deviceId) {
            return WidgetActionOutcome.Rejected(
                WidgetActionRejection.TargetMismatch(bound.value, request.deviceId),
            )
        }

        // 4. Resolve the device — exact match only.
        val state = devices[bound]
            ?: return WidgetActionOutcome.Rejected(WidgetActionRejection.UnknownDevice(request.deviceId))

        // 5. Session validation.
        val connection = state.connection
        if (connection !is ConnectionState.Connected) {
            return WidgetActionOutcome.Rejected(WidgetActionRejection.Disconnected(request.deviceId))
        }
        if (request.sessionId != null && request.sessionId != connection.sessionId) {
            return WidgetActionOutcome.Rejected(
                WidgetActionRejection.StaleSession(connection.sessionId, request.sessionId),
            )
        }

        // 6. Feature support and freshness.
        val featureModes = modes[request.featureId]
            ?: return WidgetActionOutcome.Rejected(WidgetActionRejection.Unsupported(request.featureId))
        val observed = state.features.observed[request.featureId]
            ?: return WidgetActionOutcome.Rejected(WidgetActionRejection.UnknownState(request.featureId))
        if (!observed.isUsable) {
            return WidgetActionOutcome.Rejected(WidgetActionRejection.StaleState(request.featureId))
        }
        if (observed.value !in featureModes) {
            return WidgetActionOutcome.Rejected(
                WidgetActionRejection.UnknownState("mode '${observed.value}' not in mode set"),
            )
        }

        // 7. Duplicate in-flight guard (keyed by widget + feature).
        val key = "${request.widgetId}:${request.featureId}"
        synchronized(lock) {
            if (key in inFlight) {
                return WidgetActionOutcome.Rejected(WidgetActionRejection.Duplicate(request.featureId))
            }
            inFlight.add(key)
        }

        // 8. Access policy immediately before dispatch.
        if (!accessCheck(bound, request.featureId)) {
            synchronized(lock) { inFlight.remove(key) }
            return WidgetActionOutcome.Rejected(WidgetActionRejection.AccessDenied("policy denied"))
        }

        // 9. Compute next value from the real mode set — never a blind toggle.
        val nextIndex = (featureModes.indexOf(observed.value) + 1) % featureModes.size
        val action = ValidatedWidgetAction(
            widgetId = request.widgetId,
            deviceId = bound,
            sessionId = connection.sessionId,
            featureId = request.featureId,
            nextValue = featureModes[nextIndex],
            basedOn = observed.value,
        )

        // 10. Execute through the feature engine seam.
        val accepted = writeExecutor(action)
        if (!accepted) {
            synchronized(lock) { inFlight.remove(key) }
            return WidgetActionOutcome.Rejected(WidgetActionRejection.Disconnected("executor refused"))
        }
        return WidgetActionOutcome.Dispatched(action)
    }

    /** Mark an operation complete. */
    fun complete(widgetId: Int, featureId: String) {
        synchronized(lock) { inFlight.remove("$widgetId:$featureId") }
    }
}

/**
 * Raw widget action request (parsed from a PendingIntent).
 */
data class WidgetActionRequest(
    val widgetId: Int,
    val deviceId: String,
    val sessionId: String?,
    val featureId: String,
    val nonce: String,
)
