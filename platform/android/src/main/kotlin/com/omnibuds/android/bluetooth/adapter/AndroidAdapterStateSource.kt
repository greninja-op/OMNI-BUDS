package com.omnibuds.android.bluetooth.adapter

import com.omnibuds.android.bluetooth.mapping.bluetoothAdapterStateOf
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.AdapterStateChangeChannel
import com.omnibuds.core.platform.AdapterStateSource
import com.omnibuds.core.platform.BluetoothAdapterState
import com.omnibuds.core.platform.PlatformRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

/**
 * The Android implementation of the adapter-state seam.
 *
 * It adds no platform knowledge of its own - [BluetoothAdapterHandle] holds that - and turns the
 * handle into the contracts `:core` defines: a one-shot read, and a live stream paired with the
 * registration that owns it. Keeping that split here is what lets the reading and the machine that
 * consumes it be tested independently of each other and of any phone.
 *
 * Every platform call is offloaded to [dispatcher], because `getState()` and receiver registration are
 * binder work and Phase 2 prompt section 5.8 forbids blocking the main thread. Nothing is retried: a
 * failed read is reported once, as a structured failure, and the observer decides what to do next.
 *
 * A thrown platform exception becomes [OmniBudsErrorCategory.PLATFORM_EXCEPTION], not a guess about
 * the adapter. The exception itself is preserved in the error's cause for diagnostics only; no raw
 * stack trace reaches a UI-facing string, and no device identifier appears in one at all
 * (Phase 2 prompt sections 5.7, 9; SEC-LOG-001).
 */
class AndroidAdapterStateSource(
    private val handle: BluetoothAdapterHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AdapterStateSource {

    override suspend fun readState(): OperationOutcome<BluetoothAdapterState> =
        withContext(dispatcher) {
            runCatching { bluetoothAdapterStateOf(handle.adapterPresent, handle.readRawState()) }
                .fold(
                    onSuccess = { state -> OperationOutcome.Success(state) },
                    onFailure = { problem ->
                        OperationOutcome.Failure(
                            OmniBudsError(
                                category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
                                operationId = OPERATION_ID,
                                detail = "the adapter-state read reported ${problem::class.simpleName}",
                            ),
                        )
                    },
                )
        }

    /**
     * Opens the change stream and registers for it immediately.
     *
     * The registration is created here rather than on first collection because the caller has to be
     * able to prove a registration exists, and `AdapterStateObserver` disposes it in a `finally` even
     * when collection never started. Disposal closes the channel, so a stream that has been torn down
     * ends instead of leaving a collector suspended on a receiver that no longer exists.
     *
     * The channel is conflated: adapter state is a latest-wins value, and the observer already drops
     * consecutive duplicates. Buffering transitions would let a slow collector act on states the
     * phone stopped being in several seconds ago, and would suspend the broadcast thread once the
     * buffer filled - neither is a thing this phase needs.
     */
    override suspend fun openStateChanges(): OperationOutcome<AdapterStateChangeChannel> =
        withContext(dispatcher) {
            val states = Channel<BluetoothAdapterState>(Channel.CONFLATED)
            val emission: (Int) -> Unit = { raw ->
                states.trySend(bluetoothAdapterStateOf(handle.adapterPresent, raw))
            }
            runCatching { handle.openStateChanges(emission) }
                .fold(
                    onSuccess = { registration ->
                        OperationOutcome.Success(
                            ConflatedAdapterStateChannel(registration, states),
                        )
                    },
                    onFailure = { problem ->
                        states.close()
                        OperationOutcome.Failure(
                            OmniBudsError(
                                category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
                                operationId = OPERATION_ID,
                                detail = "the adapter-state registration reported ${problem::class.simpleName}",
                            ),
                        )
                    },
                )
        }

    private companion object {
        const val OPERATION_ID = "android-adapter-source"
    }
}

/**
 * A live state stream whose disposal also ends the stream.
 *
 * The registration handed to the caller is [ClosingRegistration], not the platform's own handle, so a
 * caller that tears the registration down cannot leave a collector suspended on a receiver that has
 * already gone away.
 */
private class ConflatedAdapterStateChannel(
    registration: PlatformRegistration,
    private val channel: Channel<BluetoothAdapterState>,
) : AdapterStateChangeChannel {
    override val registration: PlatformRegistration = ClosingRegistration(registration, channel)

    override val states: Flow<BluetoothAdapterState> = channel.receiveAsFlow()
}

/**
 * Disposes the platform registration and closes the stream it feeds.
 *
 * Safe to call twice, which the observer relies on: it disposes in a `finally`, and a cancellation
 * path may have disposed already. Closing an open-once channel a second time returns false rather
 * than raising, so a repeat call cannot mask the first one's outcome.
 */
private class ClosingRegistration(
    private val registration: PlatformRegistration,
    private val states: Channel<BluetoothAdapterState>,
) : PlatformRegistration {
    override val isActive: Boolean
        get() = registration.isActive

    override suspend fun dispose() {
        registration.dispose()
        states.close()
    }
}
