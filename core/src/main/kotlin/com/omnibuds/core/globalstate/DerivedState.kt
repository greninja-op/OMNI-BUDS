package com.omnibuds.core.globalstate

/**
 * Derived state: deterministic computations over a snapshot.
 *
 * Phase 24 (OB-P24-REQ-012): every derived property has an explicit
 * definition. No single "READY" implying everything is supported.
 */
object DerivedState {

    /** True when the device has a live connection. */
    fun isConnected(state: GlobalDeviceState): Boolean =
        state.connection is ConnectionState.Connected

    /** True when identity is established (not unknown/ambiguous). */
    fun isIdentityComplete(state: GlobalDeviceState): Boolean =
        state.identity is IdentityState.Identified

    /** True when a compatible protocol is resolved. */
    fun isProtocolCompatible(state: GlobalDeviceState): Boolean =
        (state.protocol as? ProtocolState.Resolved)?.compatible == true

    /** True when capability discovery completed. */
    fun isCapabilitiesReady(state: GlobalDeviceState): Boolean =
        state.capabilities is CapabilityState.Ready

    /**
     * True when hardware control is currently permitted: connected,
     * identity complete, protocol compatible, capabilities ready.
     * This is a state summary — it does not bypass the access policy.
     */
    fun isControlPermitted(state: GlobalDeviceState): Boolean =
        isConnected(state) && isIdentityComplete(state) &&
            isProtocolCompatible(state) && isCapabilitiesReady(state)

    /** True when any feature operation is in flight. */
    fun hasPendingOperation(state: GlobalDeviceState): Boolean =
        state.features.executing.values.any { it is OperationStatus.Pending }

    /**
     * Feature ids where the desired value differs from the last valid
     * device observation.
     */
    fun desiredDiffersFromObserved(state: GlobalDeviceState): Set<String> =
        state.features.desired.mapNotNull { (id, desired) ->
            val observed = state.features.observed[id]?.value
            if (observed != null && observed != desired.value) id else null
        }.toSet()

    /** True when any observation is stale or expired. */
    fun hasStaleObservations(state: GlobalDeviceState): Boolean {
        val all = state.features.observed.values +
            state.features.desired.values +
            state.vendorFeatures.values.values
        return all.any { it.freshness == Freshness.STALE || it.freshness == Freshness.EXPIRED }
    }

    /** True when identity is ambiguous. */
    fun hasConflictingEvidence(state: GlobalDeviceState): Boolean =
        state.identity is IdentityState.Ambiguous ||
            state.protocol is ProtocolState.Ambiguous

    /**
     * Per-capability readiness: connected + identity complete + protocol
     * compatible + capability discovered.
     */
    fun isCapabilityReady(state: GlobalDeviceState, capabilityId: String): Boolean =
        isControlPermitted(state) &&
            (state.capabilities as? CapabilityState.Ready)?.capabilityIds?.contains(capabilityId) == true
}
