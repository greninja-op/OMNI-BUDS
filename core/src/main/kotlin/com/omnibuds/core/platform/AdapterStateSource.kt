package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * The platform seam the adapter-state observer reads through.
 *
 * Every fact here is one the *phone* reports about itself, obtained without touching any headset:
 * on Android this wraps `BluetoothAdapter.getState()` and an `ACTION_STATE_CHANGED` receiver, but
 * core code never learns those names, which is what lets the observer machine be unit-tested
 * without a device (Phase 2 prompt sections 5.1, 9).
 *
 * A failure here is a structured outcome, never a thrown framework exception and never a defaulted
 * [BluetoothAdapterState.ENABLED]: an unreadable adapter is unknown, not working (ADR-P0-016).
 */
interface AdapterStateSource {
    /** Read the adapter state once. */
    suspend fun readState(): OperationOutcome<BluetoothAdapterState>

    /**
     * Open a live stream of adapter-state changes and return its registration handle.
     *
     * The returned [Flow] must be cold: opening it registers with the platform, and losing its
     * collector - by cancellation or completion - must unregister. [registration] is handed back so
     * the owner can prove the teardown happened even if the flow itself was never collected to
     * completion.
     */
    suspend fun openStateChanges(): OperationOutcome<AdapterStateChangeChannel>

    companion object {
        /** A source that knows nothing, for tests and for platforms with no Bluetooth at all. */
        fun unavailable(): AdapterStateSource = UnavailableAdapterStateSource
    }
}

/** A live adapter-state event stream together with the handle that owns the platform registration. */
interface AdapterStateChangeChannel {
    val registration: PlatformRegistration

    /** Emitted when the platform reports a new adapter state; duplicates are the observer's problem. */
    val states: Flow<BluetoothAdapterState>
}

private object UnavailableAdapterStateSource : AdapterStateSource {
    override suspend fun readState(): OperationOutcome<BluetoothAdapterState> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                operationId = "adapter-source.read-state",
                detail = "no adapter state source was available",
            ),
        )

    override suspend fun openStateChanges(): OperationOutcome<AdapterStateChangeChannel> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
                operationId = "adapter-source.open-state-changes",
                detail = "no adapter state source was available",
            ),
        )
}
