package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Turns a raw adapter-state source into a single, duplicate-free, lifecycle-safe observation.
 *
 * This is where Phase 2 prompt section 5.4's requirements become behaviour, and it lives in core so
 * that they can be tested without a radio:
 *
 *  - **Initial state, then changes.** A fresh observation reports what the adapter is *now* before
 *    it reports what changed, so a consumer never learns "the adapter turned on" as its first fact
 *    about an adapter that was already on.
 *  - **Duplicate suppression.** The platform can announce the same state twice; emitting both would
 *    make a UI animate or a state engine re-run discovery for no reason. Consecutive repeats are
 *    dropped, while a genuine A-B-A sequence is preserved - those are different claims, and
 *    conflating them loses the transition back.
 *  - **One observer at a time.** Two concurrent collectors would mean two platform registrations
 *    for one fact, and the second is where a leaked receiver hides. The mutex is the whole
 *    mechanism: a second attempt fails with [OmniBudsErrorCategory.RESOURCE_UNAVAILABLE] instead of
 *    quietly double-registering.
 *  - **Cleanup on every exit path.** The platform registration handed back by the source is disposed
 *    in a `finally`, and the observer slot is released only after that, so cancellation, normal
 *    completion and an exception thrown by a downstream collector each tear the registration down
 *    exactly once.
 *
 * A failed initial read is not fatal: it is reported as a structured problem with the state left
 * [BluetoothAdapterState.UNKNOWN], because "we could not look" must never become "it is off"
 * (ADR-P0-016, master section 53).
 */
class AdapterStateObserver(
    private val source: AdapterStateSource,
    private val time: TimeProvider = NoTimeProvider,
) {
    private val slot = Mutex()

    /** Whether an observation currently holds the slot. Intended for diagnostics and tests. */
    val isObserving: Boolean
        get() = slot.isLocked

    /**
     * The structured reason the most recent teardown failed, or null when it completed cleanly.
     *
     * Read after a collector finishes to decide whether the platform registration really went away.
     * It is state on the observer rather than a flow element precisely because the stream may already
     * be closed at that moment.
     */
    var teardownProblem: OmniBudsError? = null
        private set

    /**
     * A cold flow of adapter-state observations.
     *
     * Each element is either a usable [AdapterStateObservation] or the structured reason this
     * attempt could not proceed. The flow ends when the platform stream ends, when the collector
     * stops, or when the single-observer slot could not be taken.
     */
    fun observe(): Flow<OperationOutcome<AdapterStateObservation>> = channelFlow {
        if (!slot.tryLock()) {
            send(
                OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = OPERATION_ID,
                        detail = "an adapter-state observation is already active",
                    ),
                ),
            )
            return@channelFlow
        }

        var registration: PlatformRegistration? = null
        var lastState: BluetoothAdapterState? = null
        teardownProblem = null
        try {
            when (val initial = source.readState()) {
                is OperationOutcome.Success -> {
                    lastState = initial.value
                    send(
                        OperationOutcome.Success(
                            AdapterStateObservation(
                                state = initial.value,
                                kind = ObservationKind.INITIAL_READ,
                                observedAtEpochMillis = time.nowEpochMillis(),
                            ),
                        ),
                    )
                }

                is OperationOutcome.Failure -> {
                    // The read failed, so the state is unknown. Reporting that and then continuing
                    // is deliberate: the platform may still announce a change, and a transition is
                    // worth more to the user than a stopped stream.
                    send(OperationOutcome.Failure(initial.error))
                }

                OperationOutcome.Cancelled -> {
                    send(
                        OperationOutcome.Failure(
                            OmniBudsError(
                                category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
                                operationId = OPERATION_ID,
                                detail = "the platform adapter-state read was cancelled",
                            ),
                        ),
                    )
                    return@channelFlow
                }
            }

            when (val channel = source.openStateChanges()) {
                is OperationOutcome.Success -> {
                    registration = channel.value.registration
                    var announcedFirstChange = false
                    channel.value.states.collect { state ->
                        if (state == lastState) return@collect
                        val alreadyAnnounced = announcedFirstChange
                        lastState = state
                        announcedFirstChange = true
                        send(
                            OperationOutcome.Success(
                                AdapterStateObservation(
                                    state = state,
                                    kind = if (alreadyAnnounced) {
                                        ObservationKind.PLATFORM_EVENT
                                    } else {
                                        ObservationKind.INITIAL_EVENT
                                    },
                                    observedAtEpochMillis = time.nowEpochMillis(),
                                ),
                            ),
                        )
                    }
                }

                is OperationOutcome.Failure -> send(OperationOutcome.Failure(channel.error))

                OperationOutcome.Cancelled -> Unit
            }
        } finally {
            // A registration that outlives its collector is the leak this class exists to prevent, so
            // a failed teardown is recorded rather than swallowed. It is captured instead of emitted:
            // by the time this block runs the channel may already be closed by cancellation, and a
            // send there would raise a second, unrelated failure on top of the real one.
            teardownProblem = runCatching { registration?.dispose() }
                .exceptionOrNull()
                ?.let { cause ->
                    OmniBudsError(
                        category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                        operationId = OPERATION_ID,
                        detail = "adapter-state registration teardown reported ${cause::class.simpleName}",
                    )
                }
            if (slot.isLocked) slot.unlock()
        }
    }

    /**
     * Read the adapter state once, without taking the observer slot.
     *
     * Safe to use while an observation is active, and the correct choice for a one-shot inspection,
     * since a single read does not need to reserve a registration.
     */
    suspend fun readOnce(): OperationOutcome<AdapterStateObservation> =
        source.readState().map { state ->
            AdapterStateObservation(
                state = state,
                kind = ObservationKind.PLATFORM_READ,
                observedAtEpochMillis = time.nowEpochMillis(),
            )
        }

    private companion object {
        const val OPERATION_ID = "adapter-observer.observe"
    }
}
