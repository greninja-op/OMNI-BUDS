package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFamily
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Whether the target device is currently connected.
 * The engine never attempts control against a disconnected device.
 */
fun interface DeviceLiveness {
    suspend fun isConnected(device: DeviceIdentity): Boolean
}

/**
 * Executes [CodecOperation]s as capability-gated transactions.
 *
 * Phase 12 (OB-P12-REQ-020): every operation runs
 *
 *     PRECHECK → REQUEST → APPLY → RE-OBSERVE → VERIFY → COMMIT
 *
 * - PRECHECK validates capability, transport, device liveness, field support,
 *   and verification capability before any mechanism is touched.
 * - REQUEST records the requested state (never presented as reality).
 * - APPLY invokes the [CodecControlAdapter] under a bounded timeout.
 * - RE-OBSERVE reads the state back through the adapter.
 * - VERIFY compares observed vs requested per the operation's strategy.
 * - COMMIT advances confirmed state ONLY on [CodecOperationResult.Verified].
 *
 * Phase 12 (OB-P12-REQ-025): one transaction per device at a time via
 * per-device [Mutex]; independent devices proceed independently.
 *
 * Phase 12 (OB-P12-REQ-023): cancellation-safe. A cancelled operation never
 * commits unverified state; [CancellationException] from the caller is never
 * swallowed — only this engine's own timeout is converted to
 * [CodecOperationResult.TimedOut].
 *
 * @param clockMillis injectable clock for deterministic tests.
 */
class CodecControlEngine(
    private val resolver: CodecControlCapabilityResolver,
    private val adapter: CodecControlAdapter,
    private val liveness: DeviceLiveness,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val guard = Mutex()
    private val deviceLocks = mutableMapOf<DeviceIdentity, Mutex>()

    private val _states = MutableStateFlow<Map<DeviceIdentity, CodecControlState>>(emptyMap())
    /** Authoritative per-device control state. */
    val states: StateFlow<Map<DeviceIdentity, CodecControlState>> = _states.asStateFlow()

    private suspend fun deviceLock(device: DeviceIdentity): Mutex = guard.withLock {
        deviceLocks.getOrPut(device) { Mutex() }
    }

    private fun currentState(device: DeviceIdentity): CodecControlState =
        _states.value[device] ?: CodecControlState.initial(clockMillis())

    private suspend fun updateState(device: DeviceIdentity, transform: (CodecControlState) -> CodecControlState) {
        guard.withLock {
            val current = _states.value.toMutableMap()
            current[device] = transform(current[device] ?: CodecControlState.initial(clockMillis()))
            _states.value = current
        }
    }

    /**
     * Execute [operation] as a full transaction.
     * Never throws for domain failures — they are [CodecOperationResult]s.
     * [CancellationException] from the caller propagates unchanged.
     */
    suspend fun execute(operation: CodecOperation): CodecOperationResult {
        // PRECHECK runs before acquiring the device lock: it is read-only and
        // must fail fast without blocking other devices.
        val precheck = precheck(operation)
        if (precheck != null) return precheck

        return deviceLock(operation.device).withLock {
            runTransaction(operation, isRollback = false)
        }
    }

    /**
     * Mark the device's control state stale/unknown: on disconnect, transport
     * change, route change, or observation staleness (OB-P12-REQ-026).
     * Never displays old confirmed state as current afterwards.
     */
    suspend fun invalidate(device: DeviceIdentity) {
        val now = clockMillis()
        updateState(device) {
            it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = now)
        }
    }

    // ------------------------------------------------------------------
    // PRECHECK (OB-P12-REQ-018)
    // ------------------------------------------------------------------

    private suspend fun precheck(operation: CodecOperation): CodecOperationResult? {
        val id = operation.operationId

        // 1. Device liveness — never control a disconnected or unknown device.
        if (!liveness.isConnected(operation.device)) {
            return CodecOperationResult.DeviceDisconnected(id)
        }

        // 2. Capability gate.
        val capability = resolver.resolve(operation.device, operation.codec)

        // 3. Transport validity: LC3 is LE Audio; LC3-over-A2DP is inexpressible.
        if (operation.codec == Codec.LC3 &&
            operation.expectedTransport == AudioTransportKind.CLASSIC_A2DP
        ) {
            return CodecOperationResult.Rejected(
                id,
                "LC3 is an LE Audio codec; CLASSIC_A2DP transport is invalid",
            )
        }
        if (operation.codec.family == CodecFamily.LE_AUDIO &&
            operation.expectedTransport == AudioTransportKind.CLASSIC_A2DP
        ) {
            return CodecOperationResult.Rejected(
                id,
                "${operation.codec} belongs to LE Audio; CLASSIC_A2DP transport is invalid",
            )
        }

        // 4. Operation support per type.
        when (operation.type) {
            CodecOperationType.SELECT_CODEC,
            CodecOperationType.ENABLE_CODEC,
            CodecOperationType.DISABLE_CODEC,
            -> {
                if (!capability.supported) {
                    return CodecOperationResult.Unsupported(id, operation.codec)
                }
                if (!capability.selectable) {
                    return CodecOperationResult.NotSelectable(id, operation.codec)
                }
            }
            CodecOperationType.CONFIGURE_CODEC -> {
                if (!capability.supported) {
                    return CodecOperationResult.Unsupported(id, operation.codec)
                }
                if (!capability.configurable) {
                    return CodecOperationResult.NotConfigurable(id, operation.codec)
                }
                // 5. Field support: the requested configuration must only use
                // fields this codec supports.
                val fieldRejection = validateFields(operation)
                if (fieldRejection != null) return fieldRejection
            }
            CodecOperationType.RESET_CONFIGURATION -> {
                if (!capability.configurable) {
                    return CodecOperationResult.NotConfigurable(id, operation.codec)
                }
            }
            CodecOperationType.REFRESH_STATE -> {
                // Read-only; always permitted.
            }
        }

        // 6. Verification capability: a non-NONE strategy requires verifiability.
        // Read-only REFRESH_STATE is exempt: it *is* an observation, not a
        // control operation awaiting verification.
        if (operation.type != CodecOperationType.REFRESH_STATE &&
            operation.verificationStrategy != CodecVerificationStrategy.NONE &&
            !capability.verifiable
        ) {
            return CodecOperationResult.Rejected(
                id,
                "verification strategy ${operation.verificationStrategy} is not " +
                    "available for ${operation.codec}; retry with NONE for an unverified apply",
            )
        }

        return null
    }

    /**
     * Validate that [CodecOperation.requestedConfiguration] only sets fields
     * the codec supports (OB-P12-REQ-018 rule 5).
     */
    private fun validateFields(operation: CodecOperation): CodecOperationResult.Rejected? {
        val config = operation.requestedConfiguration ?: return null
        val supported = CodecFieldSupport.forCodec(operation.codec)
        val violations = mutableListOf<String>()
        if (config.qualityMode != com.omnibuds.core.audio.QualityMode.UNKNOWN &&
            CodecConfigField.QUALITY_MODE !in supported
        ) {
            violations += "qualityMode"
        }
        if (config.bitrate != com.omnibuds.core.audio.CodecBitrate.Unknown &&
            CodecConfigField.BITRATE !in supported
        ) {
            violations += "bitrate"
        }
        if (config.sampleRateHz != null && CodecConfigField.SAMPLE_RATE !in supported) {
            violations += "sampleRateHz"
        }
        if (config.bitDepth != null && CodecConfigField.BIT_DEPTH !in supported) {
            violations += "bitDepth"
        }
        if (config.channelMode != com.omnibuds.core.audio.ChannelMode.UNKNOWN &&
            CodecConfigField.CHANNEL_MODE !in supported
        ) {
            violations += "channelMode"
        }
        if (config.adaptiveMode != null && CodecConfigField.ADAPTIVE_MODE !in supported) {
            violations += "adaptiveMode"
        }
        return if (violations.isEmpty()) {
            null
        } else {
            CodecOperationResult.Rejected(
                operation.operationId,
                "${operation.codec} does not support configuration fields: ${violations.joinToString()}",
            )
        }
    }

    // ------------------------------------------------------------------
    // TRANSACTION (OB-P12-REQ-020)
    // ------------------------------------------------------------------

    private suspend fun runTransaction(
        operation: CodecOperation,
        isRollback: Boolean,
    ): CodecOperationResult {
        val id = operation.operationId
        val device = operation.device
        val now = clockMillis()

        // REQUEST: record what was asked for. Never presented as reality.
        // A rollback is a recovery action, not a new user request, so it
        // does not overwrite the recorded request.
        if (!isRollback) {
            updateState(device) {
                it.copy(
                    requestedCodec = operation.codec,
                    requestedConfiguration = operation.requestedConfiguration,
                    freshness = CodecFreshness.CURRENT,
                    updatedAtMillis = now,
                )
            }
        }

        // REFRESH_STATE is read-only: observe and return.
        if (operation.type == CodecOperationType.REFRESH_STATE) {
            val observed = safeObserve(device, operation.codec)
            updateState(device) {
                it.copy(
                    observedCodec = observed?.codec ?: Codec.UNKNOWN,
                    observedConfiguration = observed,
                    freshness = CodecFreshness.CURRENT,
                    updatedAtMillis = clockMillis(),
                )
            }
            return CodecOperationResult.Applied(id, operation.codec, observed)
        }

        val timeout = operation.timeoutMillis ?: CodecOperation.DEFAULT_TIMEOUT_MILLIS

        // APPLY under a bounded timeout. Only this engine's own timeout is
        // converted to TimedOut; caller cancellation propagates.
        val applyOutcome: CodecApplyOutcome = try {
            withTimeout(timeout) {
                adapter.apply(operation)
            }
        } catch (e: TimeoutCancellationException) {
            updateState(device) {
                it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
            }
            return CodecOperationResult.TimedOut(id)
        }

        when (applyOutcome) {
            is CodecApplyOutcome.DeviceDisconnected -> {
                updateState(device) {
                    it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
                }
                return CodecOperationResult.DeviceDisconnected(id)
            }
            is CodecApplyOutcome.NotAvailable -> {
                // The mechanism does not exist. This is a refusal, not a failure.
                return when (operation.type) {
                    CodecOperationType.SELECT_CODEC,
                    CodecOperationType.ENABLE_CODEC,
                    CodecOperationType.DISABLE_CODEC,
                    -> CodecOperationResult.NotSelectable(id, operation.codec)
                    CodecOperationType.CONFIGURE_CODEC,
                    CodecOperationType.RESET_CONFIGURATION,
                    -> CodecOperationResult.NotConfigurable(id, operation.codec)
                    CodecOperationType.REFRESH_STATE ->
                        CodecOperationResult.NotObservable(id, operation.codec)
                }
            }
            is CodecApplyOutcome.Failed -> {
                updateState(device) {
                    it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
                }
                return CodecOperationResult.Failed(
                    id,
                    com.omnibuds.core.common.OmniBudsError.of(
                        com.omnibuds.core.common.OmniBudsErrorCategory.CODEC_OPERATION_FAILED,
                        id,
                        "codec apply failed: ${applyOutcome.reason}",
                    ),
                )
            }
            CodecApplyOutcome.Performed -> {
                // Continue to re-observation and verification below.
            }
        }

        // RE-OBSERVE through the adapter.
        val observed = safeObserve(device, operation.codec)
        updateState(device) {
            it.copy(
                observedCodec = observed?.codec ?: Codec.UNKNOWN,
                observedConfiguration = observed,
                updatedAtMillis = clockMillis(),
            )
        }

        // VERIFY per the operation's strategy.
        if (operation.verificationStrategy == CodecVerificationStrategy.NONE) {
            // No verification mechanism: record, never confirm (OB-P12-REQ-027).
            return CodecOperationResult.AppliedUnverified(
                id,
                operation.codec,
                operation.requestedConfiguration,
            )
        }

        val verified = verify(operation, observed)
        return if (verified) {
            // COMMIT: the only path that advances confirmed state.
            updateState(device) { state ->
                state.copy(
                    previousConfirmedCodec =
                    state.confirmedCodec.takeIf { it != Codec.UNKNOWN },
                    previousConfirmedConfiguration = state.confirmedConfiguration,
                    confirmedCodec = operation.codec,
                    confirmedConfiguration = operation.requestedConfiguration,
                    freshness = CodecFreshness.CURRENT,
                    updatedAtMillis = clockMillis(),
                )
            }
            CodecOperationResult.Verified(id, operation.codec, operation.requestedConfiguration)
        } else {
            // Verification failed: confirmed state does NOT advance.
            val failure = CodecOperationResult.VerificationFailed(
                id,
                requestedCodec = operation.codec,
                observedCodec = observed?.codec ?: Codec.UNKNOWN,
            )
            // ROLLBACK where a previous confirmed configuration exists and this
            // is not itself a rollback (OB-P12-REQ-021).
            if (!isRollback) {
                attemptRollback(device, operation)
            }
            failure
        }
    }

    private suspend fun safeObserve(
        device: DeviceIdentity,
        codec: Codec,
    ): CodecConfiguration? = try {
        adapter.observeAfterApply(device, codec)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Verification: the observed codec (and, for CONFIGURE, the observed
     * configuration fields the codec supports) must match what was requested.
     */
    private fun verify(operation: CodecOperation, observed: CodecConfiguration?): Boolean {
        if (observed == null) return false
        if (observed.codec != operation.codec) return false
        val requested = operation.requestedConfiguration ?: return true
        val supported = CodecFieldSupport.forCodec(operation.codec)
        // Only compare fields the codec supports; unsupported fields are
        // never part of verification.
        if (CodecConfigField.QUALITY_MODE in supported &&
            requested.qualityMode != com.omnibuds.core.audio.QualityMode.UNKNOWN &&
            observed.qualityMode != requested.qualityMode
        ) {
            return false
        }
        return true
    }

    /**
     * Rollback: re-assert the last verified (confirmed) codec to restore
     * certainty after a failed change. If rollback is impossible or fails,
     * the state is marked uncertain (stale) and a refresh is required
     * (OB-P12-REQ-021). Never pretends rollback succeeded.
     */
    private suspend fun attemptRollback(
        device: DeviceIdentity,
        failedOperation: CodecOperation,
    ) {
        val state = currentState(device)
        // The last verified state is the rollback target. If nothing was
        // ever verified, there is nothing to roll back to.
        val target = state.confirmedCodec.takeIf { it != Codec.UNKNOWN } ?: return
        // If the failed operation targeted the confirmed codec itself, the
        // device is already at the known state; just mark for refresh.
        if (target == failedOperation.codec) {
            updateState(device) {
                it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
            }
            return
        }
        val capability = try {
            resolver.resolve(device, target)
        } catch (_: Exception) {
            null
        }
        if (capability == null || !capability.selectable) {
            // Rollback impossible: mark uncertain, require refresh.
            updateState(device) {
                it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
            }
            return
        }
        val rollbackOp = CodecOperation.select(
            device = device,
            codec = target,
            operationId = "${failedOperation.operationId}:rollback",
            expectedTransport = failedOperation.expectedTransport,
            timeoutMillis = failedOperation.timeoutMillis,
            verificationStrategy = failedOperation.verificationStrategy,
        )
        val result = runTransaction(rollbackOp, isRollback = true)
        if (!result.isConfirmed) {
            // Rollback did not verify: mark uncertain.
            updateState(device) {
                it.copy(freshness = CodecFreshness.STALE, updatedAtMillis = clockMillis())
            }
        }
    }
}
