package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState

/**
 * A tile action: a validated hardware operation the tile may offer.
 *
 * Phase 25 (OB-P25-REQ-005): never a blind toggle. The [nextValue] is
 * computed from the latest verified observation and the feature's real
 * mode set.
 */
data class TileAction(
    val deviceId: GlobalDeviceId,
    val sessionId: String,
    val featureId: String,
    /** The value to write, computed from verified state. */
    val nextValue: String,
    /** The observed value the next value was computed from. */
    val basedOn: String,
)

/**
 * The outcome of dispatching a tile action.
 */
sealed interface TileDispatchOutcome {
    /** Dispatched; the operation is now pending. */
    data class Dispatched(val action: TileAction) : TileDispatchOutcome

    /** Refused before dispatch; [reason] explains why. */
    data class Refused(val reason: TileDispatchRefusal) : TileDispatchOutcome
}

/**
 * Typed refusal reasons.
 */
sealed interface TileDispatchRefusal {
    data class NoTarget(val reason: String) : TileDispatchRefusal
    data class AmbiguousTarget(val candidates: List<GlobalDeviceId>) : TileDispatchRefusal
    data class SessionInvalid(val reason: String) : TileDispatchRefusal
    data class Unsupported(val featureId: String) : TileDispatchRefusal
    data class ReadOnly(val featureId: String) : TileDispatchRefusal
    data class StaleState(val featureId: String) : TileDispatchRefusal
    data class UnknownState(val featureId: String) : TileDispatchRefusal
    data class AccessDenied(val reason: String) : TileDispatchRefusal
    data class DuplicateClick(val featureId: String) : TileDispatchRefusal
}

/**
 * Dispatches tile actions through the existing feature engine.
 *
 * Phase 25 (OB-P25-REQ-004/005/006/008): all pre-execution checks happen
 * here. The target device is bound at dispatch — no redirection.
 *
 * Dependencies are injected seams so this is unit-testable with fakes:
 * @param accessCheck returns true when the centralized access policy
 *   authorizes the write, with a reason.
 * @param writeExecutor performs the write through the feature engine and
 *   returns the outcome.
 */
class TileActionDispatcher(
    private val accessCheck: suspend (deviceId: GlobalDeviceId, featureId: String) -> AccessCheckResult,
    private val writeExecutor: suspend (action: TileAction) -> WriteOutcome,
) {
    /** Feature ids with an in-flight operation (duplicate-click guard). */
    private val inFlight = mutableSetOf<String>()
    private val lock = Any()

    /**
     * Compute the action for a feature toggle, or refuse.
     *
     * @param state the target device's current state.
     * @param featureId the feature to act on.
     * @param modes the feature's real mode set, in cycle order.
     * @param writable whether the feature is writable per capability.
     */
    suspend fun prepareToggle(
        state: GlobalDeviceState,
        featureId: String,
        modes: List<String>,
        writable: Boolean,
    ): TileDispatchOutcome {
        // Session must be valid and ready.
        val connection = state.connection
        if (connection !is ConnectionState.Connected) {
            return TileDispatchOutcome.Refused(
                TileDispatchRefusal.SessionInvalid("device is not connected"),
            )
        }

        if (!writable) {
            return TileDispatchOutcome.Refused(TileDispatchRefusal.ReadOnly(featureId))
        }

        // Current mode must be known and fresh — never guess.
        val observed = state.features.observed[featureId]
            ?: return TileDispatchOutcome.Refused(TileDispatchRefusal.UnknownState(featureId))
        if (!observed.isUsable) {
            return TileDispatchOutcome.Refused(TileDispatchRefusal.StaleState(featureId))
        }
        val current = observed.value
        if (current !in modes) {
            return TileDispatchOutcome.Refused(
                TileDispatchRefusal.UnknownState(
                    "$featureId: observed mode '$current' not in mode set",
                ),
            )
        }

        // Duplicate-click guard.
        synchronized(lock) {
            if (featureId in inFlight) {
                return TileDispatchOutcome.Refused(TileDispatchRefusal.DuplicateClick(featureId))
            }
            inFlight.add(featureId)
        }

        // Access policy checked immediately before execution.
        val access = accessCheck(state.deviceId, featureId)
        if (!access.allowed) {
            synchronized(lock) { inFlight.remove(featureId) }
            return TileDispatchOutcome.Refused(TileDispatchRefusal.AccessDenied(access.reason))
        }

        // Compute the next mode from the real mode set.
        val nextIndex = (modes.indexOf(current) + 1) % modes.size
        val action = TileAction(
            deviceId = state.deviceId,
            sessionId = connection.sessionId,
            featureId = featureId,
            nextValue = modes[nextIndex],
            basedOn = current,
        )

        return when (val outcome = writeExecutor(action)) {
            is WriteOutcome.Accepted -> TileDispatchOutcome.Dispatched(action)
            is WriteOutcome.Rejected -> {
                synchronized(lock) { inFlight.remove(featureId) }
                TileDispatchOutcome.Refused(TileDispatchRefusal.AccessDenied(outcome.reason))
            }
            is WriteOutcome.Failed -> {
                synchronized(lock) { inFlight.remove(featureId) }
                TileDispatchOutcome.Refused(TileDispatchRefusal.SessionInvalid(outcome.reason))
            }
        }
    }

    /** Mark an operation complete (success, failure, or cancellation). */
    fun complete(featureId: String) {
        synchronized(lock) { inFlight.remove(featureId) }
    }
}

/** The result of an access-policy check. */
data class AccessCheckResult(val allowed: Boolean, val reason: String)

/** The result of executing a write. */
sealed interface WriteOutcome {
    data object Accepted : WriteOutcome
    data class Rejected(val reason: String) : WriteOutcome
    data class Failed(val reason: String) : WriteOutcome
}
