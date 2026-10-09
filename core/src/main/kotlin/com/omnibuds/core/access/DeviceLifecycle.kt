package com.omnibuds.core.access

/**
 * Device lifecycle event that may change classification or access.
 *
 * Phase 21 (OB-P21-REQ-016/017): deterministic transitions, no stale
 * evidence reuse, no auto-execution of previously denied operations.
 */
sealed interface DeviceLifecycleEvent {
    /** New identity evidence arrived. */
    data class IdentityEvidenceArrived(
        val deviceKey: String,
        val fingerprint: com.omnibuds.core.device.DeviceFingerprint,
    ) : DeviceLifecycleEvent

    /** Conflicting identity evidence arrived. */
    data class ConflictingEvidence(val deviceKey: String) : DeviceLifecycleEvent

    /** Evidence is stale (timeout, disconnect). */
    data class EvidenceStale(val deviceKey: String, val reason: String) : DeviceLifecycleEvent

    /** Protocol registration changed (added, removed, invalidated). */
    data class ProtocolRegistrationChanged(
        val deviceKey: String,
        val protocolVerified: Boolean,
    ) : DeviceLifecycleEvent

    /** Firmware or protocol version changed. */
    data class VersionChanged(val deviceKey: String) : DeviceLifecycleEvent

    /** Device disconnected. */
    data class Disconnected(val deviceKey: String) : DeviceLifecycleEvent
}

/**
 * Manages per-device classification state and re-evaluation.
 *
 * Phase 21 (OB-P21-REQ-018): state is per-device (keyed by identityKey),
 * never shared across devices.
 */
class DeviceLifecycleManager {

    private val states = mutableMapOf<String, DeviceAccessState>()
    private val matchedAdapters = mutableMapOf<String, List<String>>()
    private val protocolVerified = mutableMapOf<String, Boolean>()
    private val ambiguous = mutableMapOf<String, Boolean>()

    /** Current state for a device, or unknown when never seen. */
    fun stateFor(deviceKey: String): DeviceAccessState =
        states[deviceKey] ?: DeviceAccessState.unknown()

    /**
     * Handle a lifecycle event. Returns the new state and whether any
     * in-flight restricted operations must be cancelled.
     */
    fun handle(event: DeviceLifecycleEvent): LifecycleResult {
        val deviceKey = event.deviceKey()
        val previous = stateFor(deviceKey)

        when (event) {
            is DeviceLifecycleEvent.IdentityEvidenceArrived -> {
                matchedAdapters[deviceKey] = matchedAdapters[deviceKey] ?: emptyList()
                // Re-classify with the new fingerprint. Ambiguity and match
                // lists are preserved from prior evidence.
                val newState = DeviceClassifier.classify(
                    fingerprint = event.fingerprint,
                    matchedAdapters = matchedAdapters[deviceKey] ?: emptyList(),
                    ambiguous = ambiguous[deviceKey] == true,
                    protocolVerified = protocolVerified[deviceKey] == true,
                )
                states[deviceKey] = newState
                return LifecycleResult(
                    previous = previous,
                    current = newState,
                    cancelInFlight = classificationChangedRestrictively(previous, newState),
                    reEvaluate = previous != newState,
                )
            }
            is DeviceLifecycleEvent.ConflictingEvidence -> {
                ambiguous[deviceKey] = true
                val newState = previous.copy(
                    classification = DeviceClassification.AMBIGUOUS_IDENTITY,
                    writeAuthorized = false,
                )
                states[deviceKey] = newState
                return LifecycleResult(previous, newState, cancelInFlight = true, reEvaluate = true)
            }
            is DeviceLifecycleEvent.EvidenceStale -> {
                // Stale evidence is never reused as current: downgrade to
                // unknown rather than keeping the old classification.
                val newState = DeviceAccessState.unknown()
                states[deviceKey] = newState
                return LifecycleResult(previous, newState, cancelInFlight = true, reEvaluate = true)
            }
            is DeviceLifecycleEvent.ProtocolRegistrationChanged -> {
                protocolVerified[deviceKey] = event.protocolVerified
                // Re-evaluate: a removed/invalidated registration must
                // immediately revoke any derived authorization.
                val newState = if (!event.protocolVerified) {
                    previous.copy(protocolVerified = false, writeAuthorized = false)
                } else {
                    previous
                }
                states[deviceKey] = newState
                return LifecycleResult(
                    previous, newState,
                    cancelInFlight = !event.protocolVerified,
                    reEvaluate = previous != newState,
                )
            }
            is DeviceLifecycleEvent.VersionChanged -> {
                // Version changes invalidate protocol compatibility until
                // re-verified. Classification stays; write auth is revoked.
                val newState = previous.copy(protocolVerified = false, writeAuthorized = false)
                states[deviceKey] = newState
                return LifecycleResult(previous, newState, cancelInFlight = true, reEvaluate = true)
            }
            is DeviceLifecycleEvent.Disconnected -> {
                val newState = DeviceAccessState.unknown()
                states[deviceKey] = newState
                return LifecycleResult(previous, newState, cancelInFlight = true, reEvaluate = true)
            }
        }
    }

    /** True when the transition removed previously available access. */
    private fun classificationChangedRestrictively(
        previous: DeviceAccessState,
        current: DeviceAccessState,
    ): Boolean = previous.writeAuthorized && !current.writeAuthorized

    private fun DeviceLifecycleEvent.deviceKey(): String = when (this) {
        is DeviceLifecycleEvent.IdentityEvidenceArrived -> deviceKey
        is DeviceLifecycleEvent.ConflictingEvidence -> deviceKey
        is DeviceLifecycleEvent.EvidenceStale -> deviceKey
        is DeviceLifecycleEvent.ProtocolRegistrationChanged -> deviceKey
        is DeviceLifecycleEvent.VersionChanged -> deviceKey
        is DeviceLifecycleEvent.Disconnected -> deviceKey
    }
}

/** The result of handling a lifecycle event. */
data class LifecycleResult(
    val previous: DeviceAccessState,
    val current: DeviceAccessState,
    /** True when in-flight restricted operations must be cancelled. */
    val cancelInFlight: Boolean,
    /** True when affected operations/capabilities must be re-evaluated. */
    val reEvaluate: Boolean,
)
