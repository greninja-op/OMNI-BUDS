package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState

/**
 * A validated notification action request.
 */
data class ValidatedAction(
    val deviceId: GlobalDeviceId,
    val sessionId: String,
    val featureId: String,
    val nextValue: String,
    val basedOn: String,
)

/**
 * The outcome of action validation/dispatch.
 */
sealed interface ActionOutcome {
    /** Dispatched to the feature engine. */
    data class Dispatched(val action: ValidatedAction) : ActionOutcome

    /** Rejected; [reason] explains why. */
    data class Rejected(val reason: ActionRejection) : ActionOutcome
}

/**
 * Typed action rejection reasons.
 */
sealed interface ActionRejection {
    data class Malformed(val detail: String) : ActionRejection
    data class UnknownDevice(val deviceId: String) : ActionRejection
    data class StaleSession(val expected: String?, val got: String?) : ActionRejection
    data class AmbiguousTarget(val candidates: List<String>) : ActionRejection
    data class Unsupported(val featureId: String) : ActionRejection
    data class StaleState(val featureId: String) : ActionRejection
    data class UnknownState(val featureId: String) : ActionRejection
    data class AccessDenied(val reason: String) : ActionRejection
    data class Duplicate(val featureId: String) : ActionRejection
    data class Disconnected(val deviceId: String) : ActionRejection
}

/**
 * Secure notification action dispatcher.
 *
 * Phase 26 (OB-P26-REQ-009): every action is treated as external input.
 * Validation is pure and fully unit-tested; execution goes through
 * injected seams.
 */
class NotificationActionDispatcher(
    private val accessCheck: suspend (deviceId: GlobalDeviceId, featureId: String) -> Boolean,
    private val writeExecutor: suspend (action: ValidatedAction) -> Boolean,
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
        request: ActionRequest,
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
        modes: Map<String, List<String>>,
    ): ActionOutcome {
        // 1. Parse and validate the request shape.
        if (request.deviceId.isBlank() || request.featureId.isBlank() ||
            request.actionId.isBlank() || request.nonce.isBlank()
        ) {
            return ActionOutcome.Rejected(ActionRejection.Malformed("blank fields"))
        }

        // 2. Replay guard.
        synchronized(seenLock) {
            if (request.nonce in seenNonces) {
                return ActionOutcome.Rejected(ActionRejection.Duplicate(request.featureId))
            }
            seenNonces.add(request.nonce)
            if (seenNonces.size > MAX_NONCES) {
                seenNonces.remove(seenNonces.first())
            }
        }

        // 3. Resolve the device — exact match only, never first-match.
        val deviceId = GlobalDeviceId(request.deviceId)
        val state = devices[deviceId]
            ?: return ActionOutcome.Rejected(ActionRejection.UnknownDevice(request.deviceId))

        // 4. Session validation.
        val connection = state.connection
        if (connection !is ConnectionState.Connected) {
            return ActionOutcome.Rejected(ActionRejection.Disconnected(request.deviceId))
        }
        if (request.sessionId != null && request.sessionId != connection.sessionId) {
            return ActionOutcome.Rejected(
                ActionRejection.StaleSession(connection.sessionId, request.sessionId),
            )
        }

        // 5. Feature support and freshness.
        val featureModes = modes[request.featureId]
            ?: return ActionOutcome.Rejected(ActionRejection.Unsupported(request.featureId))
        val observed = state.features.observed[request.featureId]
            ?: return ActionOutcome.Rejected(ActionRejection.UnknownState(request.featureId))
        if (!observed.isUsable) {
            return ActionOutcome.Rejected(ActionRejection.StaleState(request.featureId))
        }
        if (observed.value !in featureModes) {
            return ActionOutcome.Rejected(
                ActionRejection.UnknownState("mode '${observed.value}' not in mode set"),
            )
        }

        // 6. Duplicate in-flight guard.
        synchronized(lock) {
            if (request.featureId in inFlight) {
                return ActionOutcome.Rejected(ActionRejection.Duplicate(request.featureId))
            }
            inFlight.add(request.featureId)
        }

        // 7. Access policy immediately before dispatch.
        if (!accessCheck(deviceId, request.featureId)) {
            synchronized(lock) { inFlight.remove(request.featureId) }
            return ActionOutcome.Rejected(ActionRejection.AccessDenied("policy denied"))
        }

        // 8. Compute next value from the real mode set.
        val nextIndex = (featureModes.indexOf(observed.value) + 1) % featureModes.size
        val action = ValidatedAction(
            deviceId = deviceId,
            sessionId = connection.sessionId,
            featureId = request.featureId,
            nextValue = featureModes[nextIndex],
            basedOn = observed.value,
        )

        // 9. Execute through the feature engine seam.
        val accepted = writeExecutor(action)
        if (!accepted) {
            synchronized(lock) { inFlight.remove(request.featureId) }
            return ActionOutcome.Rejected(ActionRejection.Disconnected("executor refused"))
        }
        return ActionOutcome.Dispatched(action)
    }

    /** Mark an operation complete. */
    fun complete(featureId: String) {
        synchronized(lock) { inFlight.remove(featureId) }
    }
}

/**
 * Raw action request (parsed from a PendingIntent).
 */
data class ActionRequest(
    val actionId: String,
    val deviceId: String,
    val sessionId: String?,
    val featureId: String,
    val nonce: String,
)
