package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.capability.CapabilitySnapshot
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * The hardware feature control engine: the layer that turns "set ANC on" into a
 * validated, executed, reconciled operation against a device.
 *
 * The engine sits above the protocol layer and below any future UI, exactly as
 * the Phase 9 architecture demands:
 *
 * ```text
 * Device Session
 *       |
 * Capability Snapshot (handed in per call — capability truth is never cached here)
 *       |
 * FeatureEngine
 *       |-- FeatureValidator      (may this be attempted?)
 *       |-- FeatureStateRepository (what is the control state?)
 *       |
 * FeatureProtocolPort (handed in — the later phase binds it to a real protocol)
 *       |
 * Transport (unseen from here; the port hides it)
 * ```
 *
 * The engine owns the *operation lifecycle* and nothing else: capability truth
 * stays with discovery, transport truth with the transport layer, protocol truth
 * with the protocol session (ARCH-LAYER-003). It never caches a snapshot —
 * every call takes the current one — so a capability change is honoured on the
 * very next operation instead of being argued with.
 *
 * ### The guarantees, each tested
 *
 * - **Requested is not confirmed.** A write moves the feature to
 *   [FeatureState.Pending] carrying the requested value; [FeatureState.Confirmed]
 *   is written only when the device reports the value — via read-back after a
 *   write, or via [onDeviceReported] when the hardware changes on its own.
 * - **A command acceptance is not an applied change.** After a successful write
 *   the engine reads the feature back. Read-back equal to the request confirms;
 *   read-back different confirms *the device's value* and fails the operation
 *   with [FeatureErrorCode.STATE_VERIFICATION_FAILED]; read-back failed leaves
 *   the state [FeatureState.Unknown] — never a guess.
 * - **A timed-out write is never re-sent.** The engine follows a timeout with
 *   one read: value as requested confirms (the goal state holds, however it got
 *   there); a different value confirms that value and reports
 *   [FeatureErrorCode.OPERATION_TIMEOUT]; a failed read leaves the state unknown
 *   and reports the timeout (PROTO-ERR-002).
 * - **Retries follow the error, not the call site.**
 *   [com.omnibuds.core.common.RetryClass.SAFE_TO_RETRY] failures on reads may be retried
 *   by the caller under a bounded policy;
 *   [com.omnibuds.core.common.RetryClass.RETRY_AFTER_REREAD] resolves by re-reading first;
 *   [com.omnibuds.core.common.RetryClass.NEVER_RETRY] is never repeated automatically.
 * - **One operation per feature at a time.** Operations on the same feature
 *   serialize; different features proceed concurrently. A second write does not
 *   interleave with the first — it waits, then validates against fresh state.
 * - **Stale responses cannot overwrite device truth.** Every operation captures
 *   the feature's generation; [onDeviceReported] bumps it. A completion that
 *   finds its generation superseded leaves the newer device-reported state
 *   alone and reports [FeatureErrorCode.DEVICE_STATE_UNKNOWN].
 * - **Cancellation restores.** An externally cancelled operation returns the
 *   feature to the state it held before the attempt (unless a device report
 *   arrived meanwhile, which wins) and rethrows — cancellation is
 *   [OperationOutcome.Cancelled], never a fabricated failure (ADR-P1-004).
 * - **Session invalidation poisons in-flight work.** [onSessionInvalidated]
 *   advances the session epoch: in-flight operations complete as
 *   [OmniBudsErrorCategory.DEVICE_DISCONNECTED] without touching state, and
 *   every tracked feature moves to [FeatureState.Unknown] keeping its last
 *   confirmed value as stale knowledge.
 * - **Untrusted values are validated both ways.** Requested values pass the
 *   definition's shape and constraints; device-reported values pass the same
 *   check before they may become [FeatureState.Confirmed]. A malformed device
 *   value never crashes the engine and never becomes state.
 */
class FeatureEngine(
    private val definitions: Map<FeatureId, FeatureDefinition>,
    private val port: FeatureProtocolPort,
    private val repository: FeatureStateRepository = InMemoryFeatureStateRepository(),
    private val validator: FeatureValidator = FeatureValidator(),
) {

    /** The current control state per feature, reactively. */
    val states: StateFlow<Map<FeatureId, FeatureState>> = repository.states

    private val guard = Mutex()
    private val featureLocks = mutableMapOf<FeatureId, Mutex>()
    private val generations = mutableMapOf<FeatureId, Long>()
    // The session epoch is plain state behind [guard]: no java.util.concurrent
    // atomics, so the module stays Kotlin/Multiplatform-clean (the architecture
    // test forbids java.* imports in :core).
    private var sessionEpoch: Long = 0

    /**
     * Seeds control states from a discovery snapshot, once per feature.
     *
     * Established and available features become [FeatureState.Available];
     * established-but-unavailable ones become [FeatureState.Unavailable] with
     * the reason discovery reported; unknown features become
     * [FeatureState.Unknown]. Positively unsupported features are not tracked —
     * the validator rejects operations on them, and tracking them would invite
     * a UI to render a control for something proven absent.
     *
     * Features already tracked are left alone: re-seeding would discard live
     * [FeatureState.Pending] or [FeatureState.Confirmed] state.
     */
    suspend fun adoptSnapshot(snapshot: CapabilitySnapshot) {
        for ((feature, _) in definitions) {
            if (repository.stateOf(feature) != null) continue
            val record = snapshot.capabilities[feature]
            val state = record?.state ?: CapabilityState.UNKNOWN
            if (state == CapabilityState.UNSUPPORTED) continue
            val availability = snapshot.availabilityOf(feature)
            val seeded: FeatureState = when {
                availability == CapabilityAvailability.UNAVAILABLE ->
                    FeatureState.Unavailable(feature, "discovery reported the feature unavailable")

                availability == CapabilityAvailability.TEMPORARILY_UNAVAILABLE ->
                    FeatureState.Unavailable(feature, "discovery reported the feature temporarily unavailable")

                state == CapabilityState.UNKNOWN ->
                    FeatureState.Unknown(feature)

                else -> FeatureState.Available(feature)
            }
            repository.update(feature) { seeded }
        }
    }

    /**
     * Reads [feature]'s current value through the protocol port.
     *
     * Validation first; then the port read under the operation's timeout. A
     * failed read moves the feature to [FeatureState.Failed] but keeps the last
     * confirmed value as stale knowledge — a transient failure does not erase
     * what the device previously reported.
     */
    suspend fun read(
        feature: FeatureId,
        snapshot: CapabilitySnapshot,
        operationId: String,
        timeoutMillis: Long? = null,
    ): OperationOutcome<FeatureState> {
        val operation = FeatureOperation.read(feature, operationId, timeoutMillis)
        return execute(
            operation = operation,
            snapshot = snapshot,
            attempt = { port.read(feature) },
            finish = { outcome, _ -> when (outcome) {
                    is OperationOutcome.Success -> {
                        val definition = definitions[feature]
                        val detail = definition?.validationDetail(outcome.value)
                        if (detail != null) {
                            // Untrusted device value that the definition cannot interpret.
                            // Recorded as a failure; the last confirmed value is kept.
                            val error = FeatureErrorCode.MALFORMED_RESPONSE.toError(
                                operationId,
                                "device reported an uninterpretable value for " +
                                    "${feature.qualifiedName}: $detail",
                            )
                            failCurrent(feature, error)
                            OperationOutcome.Failure(error)
                        } else {
                            val confirmed = FeatureState.Confirmed(feature, outcome.value)
                            repository.update(feature) { confirmed }
                            OperationOutcome.Success(confirmed)
                        }
                    }

                    is OperationOutcome.Failure -> {
                        failCurrent(feature, outcome.error)
                        OperationOutcome.Failure(outcome.error)
                    }

                    OperationOutcome.Cancelled -> OperationOutcome.Cancelled
                }
            },
        )
    }

    /**
     * Requests [feature] be set to [value].
     *
     * Validation first — including value shape, constraints, dependencies and
     * conflicts — then [FeatureState.Pending], then the port write under the
     * operation's timeout, then the mandatory read-back. See the class KDoc for
     * the timeout, staleness and retry rules.
     */
    suspend fun write(
        feature: FeatureId,
        value: ConfigurationValue,
        snapshot: CapabilitySnapshot,
        operationId: String,
        timeoutMillis: Long? = null,
    ): OperationOutcome<FeatureState> {
        val operation = FeatureOperation.write(feature, value, operationId, timeoutMillis)
        return execute(
            operation = operation,
            snapshot = snapshot,
            attempt = { port.write(feature, value) },
            finish = { outcome, epoch ->
                when (outcome) {
                    is OperationOutcome.Success -> verifyWrite(feature, value, operation, epoch)
                    is OperationOutcome.Failure -> {
                        failCurrent(feature, outcome.error)
                        OperationOutcome.Failure(outcome.error)
                    }

                    OperationOutcome.Cancelled -> OperationOutcome.Cancelled
                }
            },
        )
    }

    /**
     * Records a device-reported value — the hardware changed on its own, e.g.
     * the user pressed the earbud, or the protocol pushed a state update.
     *
     * Device truth wins: the feature becomes [FeatureState.Confirmed] whatever
     * it held before, including [FeatureState.Pending] — a stale request is
     * dropped, never applied over the report. The generation bumps so any
     * in-flight operation recognises its response as superseded.
     *
     * Reports for features without a definition, or values the definition
     * cannot interpret, are ignored: the engine cannot give meaning to what it
     * has no contract for, and a malformed report must not become state.
     */
    suspend fun onDeviceReported(feature: FeatureId, value: ConfigurationValue) {
        val definition = definitions[feature] ?: return
        if (definition.validateValue(value) != null) return
        bumpGeneration(feature)
        if (repository.stateOf(feature) == null) {
            // A validated report for a feature discovery never mentioned: the
            // report itself establishes the capability.
            repository.update(feature) { FeatureState.Available(feature) }
        }
        repository.update(feature) { FeatureState.Confirmed(feature, value) }
    }

    /**
     * Tears down control state when the session is invalidated.
     *
     * Every tracked feature moves to [FeatureState.Unknown], keeping its last
     * confirmed value as stale knowledge. In-flight operations notice the epoch
     * advance on completion and report
     * [OmniBudsErrorCategory.DEVICE_DISCONNECTED] without touching state.
     */
    suspend fun onSessionInvalidated() {
        guard.withLock {
            sessionEpoch++
            for (feature in generations.keys.toList()) {
                generations[feature] = (generations[feature] ?: 0L) + 1
            }
        }
        val tracked = repository.states.value.keys.toList()
        for (feature in tracked) {
            repository.update(feature) { current ->
                FeatureState.Unknown(feature, lastConfirmed = current?.lastConfirmed)
            }
        }
    }

    /** Observes one feature's control state reactively. */
    fun observe(feature: FeatureId): Flow<FeatureState?> =
        states.map { it[feature] }.distinctUntilChanged()

    /** The last confirmed value the engine holds for [feature], if any. */
    fun lastConfirmedOf(feature: FeatureId): ConfigurationValue? =
        repository.stateOf(feature)?.lastConfirmed

    // ---- internals -------------------------------------------------------

    /**
     * The shared operation skeleton: validate, serialize per feature, run under
     * timeout with cancellation-restore, then reconcile staleness and session
     * validity before the state transition.
     */
    private suspend fun <T> execute(
        operation: FeatureOperation,
        snapshot: CapabilitySnapshot,
        attempt: suspend () -> OperationOutcome<T>,
        // The epoch is handed to the finisher so verification that spans the
        // attempt (the mandatory write read-back) can notice a session death
        // that happened *during* verification, not just during the attempt.
        finish: suspend (OperationOutcome<T>, Long) -> OperationOutcome<FeatureState>,
    ): OperationOutcome<FeatureState> {
        val feature = operation.feature
        val definition = definitions[feature]
        val allRelations = definitions.values.flatMap { it.relations }

        when (
            val validation = validator.validate(
                operation,
                definition,
                snapshot,
                port,
                states.value,
                allRelations,
            )
        ) {
            is FeatureValidation.Invalid -> {
                val error = validation.code.toError(operation.operationId, validation.detail)
                // Record the refusal. If the repository never heard of this
                // feature, seed the honest prior state first: the transition
                // table only seeds Unknown/Available/Unavailable from nothing.
                if (repository.stateOf(feature) == null) {
                    repository.update(feature) { FeatureState.Unknown(feature) }
                }
                failCurrent(feature, error)
                return OperationOutcome.Failure(error)
            }

            FeatureValidation.Valid -> Unit
        }

        val lock = featureLock(feature)
        return lock.withLock {
            val epoch = currentEpoch()
            val generation = bumpGeneration(feature)
            val previous = repository.stateOf(feature)

            if (operation.type == FeatureOperationType.WRITE) {
                val requested = operation.requestedValue
                    ?: throw IllegalStateException("a validated WRITE always carries a value")
                // The validator just established the capability and the access; if
                // control state was lost or concluded meanwhile (invalidation,
                // a failed verification, a finished operation), re-assert
                // availability before going pending, so the transition table
                // never sees a non-Available -> Pending move. The confirmed
                // value, if any, is kept as lastConfirmed.
                val before = repository.stateOf(feature)
                if (before !is FeatureState.Available) {
                    repository.update(feature) {
                        FeatureState.Available(feature, it?.lastConfirmed)
                    }
                }
                repository.update(feature) {
                    FeatureState.Pending(
                        feature = feature,
                        requested = requested,
                        lastConfirmed = repository.stateOf(feature)?.lastConfirmed,
                        operationId = operation.operationId,
                    )
                }
            }

            val outcome: OperationOutcome<T> = try {
                withTimeout(operation.timeoutMillis ?: FeatureOperation.DEFAULT_TIMEOUT_MILLIS) {
                    attempt()
                }
            } catch (e: TimeoutCancellationException) {
                return@withLock onTimeout(operation, epoch)
            } catch (e: CancellationException) {
                // Untouched by definition: restore what was there, unless a device
                // report arrived meanwhile — device truth always wins.
                if (generationOf(feature) == generation) {
                    repository.update(feature) { previous ?: FeatureState.Unknown(feature) }
                }
                throw e
            }

            if (currentEpoch() != epoch) {
                val error = OmniBudsError.of(
                    OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                    operation.operationId,
                    "the session was invalidated while the operation was in flight; " +
                        "its result cannot be trusted",
                )
                return@withLock OperationOutcome.Failure(error)
            }

            if (generationOf(feature) != generation) {
                // A device report superseded this attempt. The state already holds
                // the newer truth; the attempt cannot claim a confirmed result.
                val current = repository.stateOf(feature)
                val error = FeatureErrorCode.DEVICE_STATE_UNKNOWN.toError(
                    operation.operationId,
                    "a device-reported update superseded this operation; the current " +
                        "state holds the newer truth",
                )
                return@withLock if (current is FeatureState.Confirmed) {
                    OperationOutcome.Success(current)
                } else {
                    OperationOutcome.Failure(error)
                }
            }

            finish(outcome, epoch)
        }
    }

    /**
     * The mandatory read-back after a write was accepted, plus the timeout path.
     *
     * - Write accepted, read-back equals the request: [FeatureState.Confirmed].
     * - Write accepted, read-back differs: the device's value is confirmed and
     *   the operation fails with [FeatureErrorCode.STATE_VERIFICATION_FAILED] —
     *   the goal was not achieved, but the truth is known.
     * - Write accepted, read-back failed: [FeatureState.Unknown]; the write may
     *   or may not have applied, and the engine will not guess.
     * - Write timed out: never re-sent. One read decides: value as requested
     *   confirms the goal state; a different value confirms that value and the
     *   operation reports the timeout; a failed read leaves the state unknown
     *   and reports the timeout.
     */
    private suspend fun verifyWrite(
        feature: FeatureId,
        requested: ConfigurationValue,
        operation: FeatureOperation,
        epoch: Long,
    ): OperationOutcome<FeatureState> {
        val reread = readBack(feature, operation)
        if (currentEpoch() != epoch) {
            // The session died during verification. The read-back cannot be
            // trusted, and the invalidation already moved the feature to
            // Unknown — this completion touches nothing.
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                    operation.operationId,
                    "the session was invalidated during write verification; " +
                        "the read-back cannot be trusted",
                ),
            )
        }
        return when (reread) {
            is OperationOutcome.Success -> {
                val definition = definitions[feature]
                if (definition?.validateValue(reread.value) != null) {
                    val error = FeatureErrorCode.MALFORMED_RESPONSE.toError(
                        operation.operationId,
                        "read-back for ${feature.qualifiedName} returned an uninterpretable value",
                    )
                    failUnknown(feature)
                    OperationOutcome.Failure(error)
                } else if (reread.value == requested) {
                    val confirmed = FeatureState.Confirmed(feature, requested)
                    repository.update(feature) { confirmed }
                    OperationOutcome.Success(confirmed)
                } else {
                    val confirmed = FeatureState.Confirmed(feature, reread.value)
                    repository.update(feature) { confirmed }
                    val error = FeatureErrorCode.STATE_VERIFICATION_FAILED.toError(
                        operation.operationId,
                        "device confirmed ${describeValue(reread.value)} for " +
                            "${feature.qualifiedName}, not the requested ${describeValue(requested)}",
                    )
                    OperationOutcome.Failure(error)
                }
            }

            is OperationOutcome.Failure -> {
                val error = FeatureErrorCode.DEVICE_STATE_UNKNOWN.toError(
                    operation.operationId,
                    "write was accepted but the verification read failed (${reread.error.category}); " +
                        "the device state is unknown",
                )
                failUnknown(feature)
                OperationOutcome.Failure(error)
            }

            OperationOutcome.Cancelled -> {
                // The verification read was cancelled: the write's fate is unknown.
                val error = FeatureErrorCode.DEVICE_STATE_UNKNOWN.toError(
                    operation.operationId,
                    "write was accepted but the verification read was cancelled; " +
                        "the device state is unknown",
                )
                failUnknown(feature)
                OperationOutcome.Failure(error)
            }
        }
    }

    /** The timeout path: one read, never a re-send (PROTO-ERR-002). */
    private suspend fun onTimeout(
        operation: FeatureOperation,
        epoch: Long,
    ): OperationOutcome<FeatureState> {
        val feature = operation.feature
        if (operation.type != FeatureOperationType.WRITE) {
            val error = FeatureErrorCode.OPERATION_TIMEOUT.toError(
                operation.operationId,
                "${operation.type} on ${feature.qualifiedName} exceeded its bound",
            )
            failCurrent(feature, error)
            return OperationOutcome.Failure(error)
        }
        val requested = operation.requestedValue
            ?: throw IllegalStateException("a validated WRITE always carries a value")
        val reread = readBack(feature, operation)
        if (currentEpoch() != epoch) {
            // The session died during the timeout's read-back. Nothing here can
            // be trusted; the invalidation already moved the feature to Unknown.
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.DEVICE_DISCONNECTED,
                    operation.operationId,
                    "the session was invalidated while resolving a timed-out write; " +
                        "the device state cannot be established",
                ),
            )
        }
        return when (reread) {
            is OperationOutcome.Success -> {
                val definition = definitions[feature]
                if (definition?.validateValue(reread.value) != null) {
                    val error = FeatureErrorCode.MALFORMED_RESPONSE.toError(
                        operation.operationId,
                        "post-timeout read for ${feature.qualifiedName} returned an " +
                            "uninterpretable value",
                    )
                    failUnknown(feature)
                    OperationOutcome.Failure(error)
                } else if (reread.value == requested) {
                    // The goal state holds on the device, however it got there.
                    // Attribution is not claimed; the state is confirmed.
                    val confirmed = FeatureState.Confirmed(feature, requested)
                    repository.update(feature) { confirmed }
                    OperationOutcome.Success(confirmed)
                } else {
                    val confirmed = FeatureState.Confirmed(feature, reread.value)
                    repository.update(feature) { confirmed }
                    val error = FeatureErrorCode.OPERATION_TIMEOUT.toError(
                        operation.operationId,
                        "write to ${feature.qualifiedName} timed out; the device now " +
                            "reports ${describeValue(reread.value)}, not the requested " +
                            describeValue(requested),
                    )
                    OperationOutcome.Failure(error)
                }
            }

            is OperationOutcome.Failure,
            OperationOutcome.Cancelled,
            -> {
                val error = FeatureErrorCode.OPERATION_TIMEOUT.toError(
                    operation.operationId,
                    "write to ${feature.qualifiedName} timed out and the follow-up read " +
                        "could not establish the device state; the write was not re-sent",
                )
                failUnknown(feature)
                OperationOutcome.Failure(error)
            }
        }
    }

    /**
     * One bounded read-back through the port, bypassing validation: the write
     * was already validated, and this read is the engine's own verification, not
     * a new user operation. A failure here is evidence about the device, not a
     * state transition — the caller decides the transition.
     */
    private suspend fun readBack(
        feature: FeatureId,
        operation: FeatureOperation,
    ): OperationOutcome<ConfigurationValue> {
        if (!port.supportsRead) {
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                    operation.operationId,
                    "the bound protocol cannot read ${feature.qualifiedName} back",
                ),
            )
        }
        return try {
            withTimeout(operation.timeoutMillis ?: FeatureOperation.DEFAULT_TIMEOUT_MILLIS) {
                port.read(feature)
            }
        } catch (e: TimeoutCancellationException) {
            OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.TIMEOUT,
                    operation.operationId,
                    "verification read for ${feature.qualifiedName} exceeded its bound",
                ),
            )
        }
    }

    private suspend fun failCurrent(feature: FeatureId, error: OmniBudsError) {
        repository.update(feature) { current ->
            FeatureState.Failed(feature, error, lastConfirmed = current?.lastConfirmed)
        }
    }

    private suspend fun failUnknown(feature: FeatureId) {
        // The error travels in the returned outcome; the *state* the engine
        // believes becomes Unknown, because the device's true value cannot be
        // established. The last confirmed value is kept as stale knowledge.
        repository.update(feature) { current ->
            FeatureState.Unknown(feature, lastConfirmed = current?.lastConfirmed)
        }
    }

    private suspend fun featureLock(feature: FeatureId): Mutex = guard.withLock {
        featureLocks.getOrPut(feature) { Mutex() }
    }

    private suspend fun bumpGeneration(feature: FeatureId): Long = guard.withLock {
        val next = (generations[feature] ?: 0L) + 1
        generations[feature] = next
        next
    }

    private suspend fun generationOf(feature: FeatureId): Long = guard.withLock {
        generations[feature] ?: 0L
    }

    private suspend fun currentEpoch(): Long = guard.withLock { sessionEpoch }

    /**
     * Renders a value for diagnostics without leaking payload bytes: shapes and
     * scalar contents are safe to log; structured/custom contents are summarized
     * by kind and size (SEC-LOG-004).
     */
    private fun describeValue(value: ConfigurationValue): String = when (value) {
        is ConfigurationValue.BooleanValue -> value.value.toString()
        is ConfigurationValue.IntValue -> value.value.toString()
        is ConfigurationValue.FloatValue -> value.value.toString()
        is ConfigurationValue.ModeValue -> "mode(${value.technicalName})"
        is ConfigurationValue.StringValue -> "string(len=${value.value.length})"
        is ConfigurationValue.RangeValue -> "range(${value.min}..${value.max})"
        is ConfigurationValue.StructuredValue -> "structured(${value.fields.size} fields)"
        is ConfigurationValue.BitmaskValue -> "bitmask(${value.flags.size} flags)"
        is ConfigurationValue.CustomValue -> "custom(len=${value.payload.length})"
    }
}
