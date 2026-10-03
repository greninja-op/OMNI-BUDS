package com.omnibuds.android.bluetooth.transport

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.transport.GattCharacteristic
import com.omnibuds.core.transport.GattService
import com.omnibuds.core.transport.GattTransport
import com.omnibuds.core.transport.MtuInfo
import com.omnibuds.core.transport.TransportAvailability
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportResponse
import com.omnibuds.core.transport.TransportState
import com.omnibuds.core.transport.TransportStateTransitions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Android-driven GATT control channel, implementing the `:core` contract over a [GattTransportHandle].
 *
 * This class holds the parts worth testing and none of the framework: the lifecycle machine
 * ([TransportStateTransitions]), the one-operation-in-flight serialisation, and the raw-status-to-category
 * mapping all live here as ordinary Kotlin, exercised against a scripted handle by
 * `AndroidGattTransportTest`. The framework — `BluetoothGatt`, its callback, `connectGatt` — is confined to
 * [SystemGattTransportHandle] behind the seam (ADR-P6-008). The ceiling on everything below is
 * `IMPLEMENTED`: it drives a mechanism that has never touched a radio, and no state above `CONNECTING` is
 * reachable except through what a handle reports, never through a guess (PROTO-VERIFY-001, prompt §8's "do
 * not report a transport connected before the platform confirms it").
 */
class AndroidGattTransport(
    private val handle: GattTransportHandle,
) : GattTransport {

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val operationLock = Mutex()
    private var negotiatedMtu: Int? = null

    override val kind: TransportKind get() = TransportKind.GATT
    override val isOpen: Boolean get() = _state.value == TransportState.CONNECTED
    override val mtu: MtuInfo? get() = negotiatedMtu?.let { size -> MtuInfo(negotiatedMtu = size) }

    override suspend fun open(): OperationOutcome<Unit> = operationLock.withLock {
        // Idempotent on a live channel, refused from a terminal one; otherwise the attempt is CONNECTING,
        // and only the platform's confirmation moves it to CONNECTED (prompt §8's central rule).
        if (_state.value == TransportState.CONNECTED) return@withLock OperationOutcome.Success(Unit)
        if (!TransportStateTransitions.canTransition(_state.value, TransportState.CONNECTING)) {
            return@withLock OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "transport.gatt.open",
                    detail = "cannot open a channel in ${_state.value}",
                ),
            )
        }
        moveTo(TransportState.CONNECTING)
        when (val result = handle.connect()) {
            RawConnectResult.Established -> {
                moveTo(TransportState.CONNECTED)
                OperationOutcome.Success(Unit)
            }
            is RawConnectResult.Refused -> fail(
                result.status?.let(TransportErrorMapping::gattCategory)
                    ?: TransportErrorMapping.connectCategory(result.status),
                "gatt connect refused (status=${result.status})",
            )
            RawConnectResult.Unanswered -> fail(
                OmniBudsErrorCategory.TIMEOUT,
                "gatt connect callback never resolved",
            )
        }
    }

    override suspend fun close(): OperationOutcome<Unit> = operationLock.withLock {
        // Idempotent teardown: an already-closed or never-opened channel reports success without re-closing,
        // because prompt §8's "repeated operation behaviour" forbids a close that fails the second time, and
        // a half-torn-down channel is reported, not re-driven in a loop (TransportContract.close rule).
        if (_state.value == TransportState.CLOSED) return@withLock OperationOutcome.Success(Unit)
        moveTo(TransportState.CLOSING)
        val result = handle.close()
        _state.value = if (result.ok) TransportState.CLOSED else TransportState.FAILED
        if (result.ok) {
            OperationOutcome.Success(Unit)
        } else {
            OperationOutcome.Failure(error("gatt close failed (status=${result.status})"))
        }
    }

    /**
     * Report whether this channel could be opened, from what the handle can see without opening it.
     *
     * The only honest pre-open answer: it consults adapter/permission/API facts via
     * [GattTransportHandle.probe] and never connects, so an unavailable result carries its category rather
     * than collapsing to a silent "no" (PROTO-XPORT-008). It does not change [state] — availability is a
     * question about the environment, not a transition of the channel.
     */
    override suspend fun probeAvailability(): OperationOutcome<TransportAvailability> {
        val result = handle.probe()
        return if (result.ok) {
            OperationOutcome.Success(TransportAvailability.available(TransportKind.GATT))
        } else {
            val category = result.status?.let(TransportErrorMapping::gattCategory)
                ?: OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE
            OperationOutcome.Success(TransportAvailability.unavailable(TransportKind.GATT, category))
        }
    }

    /**
     * The generic byte-exchange path is not how GATT is addressed: a GATT operation targets an attribute,
     * which an opaque [TransportRequest] does not carry. Refusing here rather than guessing a characteristic
     * keeps PROTO-ABST-006 (no protocol meaning smuggled into transport primitives) intact — [readCharacteristic]
     * and [writeCharacteristic] are GATT's real operation surface, and the byte-stream `exchange` belongs to
     * channels that actually are byte streams (RFCOMM, a vendor socket).
     */
    override suspend fun exchange(request: TransportRequest, timeoutMillis: Long): OperationOutcome<TransportResponse> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.UNSUPPORTED_OPERATION,
                operationId = "transport.gatt.exchange",
                detail = "GATT is addressed per characteristic, not by an opaque command; use read/writeCharacteristic",
            ),
        )

    override suspend fun discoverServices(): OperationOutcome<List<GattService>> = guard {
        when (val result = handle.discover()) {
            is RawDiscoveryResult.Reported -> OperationOutcome.Success(result.services)
            RawDiscoveryResult.Failed -> failAndReturn(OmniBudsErrorCategory.GATT_FAILURE, "gatt discovery failed")
            RawDiscoveryResult.Unanswered -> failAndReturn(OmniBudsErrorCategory.TIMEOUT, "gatt discovery unresolved")
        }
    }

    override suspend fun readCharacteristic(target: GattCharacteristic): OperationOutcome<ByteArray> = guard {
        if (!target.isReadable) {
            return@guard refused(OmniBudsErrorCategory.WRITE_REJECTED, "characteristic ${target.characteristic.value} is not readable")
        }
        when (val result = handle.read(target)) {
            is RawReadResult.Value -> OperationOutcome.Success(result.bytes)
            RawReadResult.Missing -> OperationOutcome.Failure(error("gatt read returned no value"))
            is RawReadResult.Failed ->
                failAndReturn(TransportErrorMapping.gattCategory(result.status ?: -1) ?: OmniBudsErrorCategory.GATT_FAILURE, "gatt read failed")
        }
    }

    override suspend fun writeCharacteristic(
        target: GattCharacteristic,
        value: ByteArray,
        withResponse: Boolean,
    ): OperationOutcome<Unit> = guard {
        if (!target.isWritable) {
            return@guard refused(OmniBudsErrorCategory.WRITE_REJECTED, "characteristic ${target.characteristic.value} is not writable")
        }
        val ceiling = mtu?.usablePayloadBytes
        if (ceiling != null && value.size > ceiling) {
            // Refuse rather than fragment: splitting a write is a framing decision the protocol layer owns,
            // not the transport (PROTO-ABST-006, prompt §9's payload-size constraint).
            return@guard refused(OmniBudsErrorCategory.INVALID_STATE, "payload ${value.size} exceeds usable MTU $ceiling")
        }
        val result = handle.write(target, value, withResponse)
        result.toOutcome("gatt write rejected")
    }

    override fun notifications(target: GattCharacteristic): Flow<OperationOutcome<ByteArray>> = callbackFlow {
        if (!target.isNotifiable) {
            close(IllegalStateException("characteristic ${target.characteristic.value} is not notifiable"))
            return@callbackFlow
        }
        val sink: (RawReadResult) -> Unit = { raw ->
            val outcome = when (raw) {
                is RawReadResult.Value -> OperationOutcome.Success(raw.bytes)
                RawReadResult.Missing -> OperationOutcome.Success(EMPTY)
                is RawReadResult.Failed ->
                    OperationOutcome.Failure(error("gatt notification failed (status=${raw.status})"))
            }
            trySend(outcome)
        }
        handle.subscribe(target, sink)
        awaitClose { handle.cancelSubscription(target) } // a dropped collector always unsubscribes: no leak
    }

    suspend fun requestMtu(requested: Int): OperationOutcome<MtuInfo> = guard {
        val granted = handle.requestMtu(requested)
        if (granted == null) {
            failAndReturn(OmniBudsErrorCategory.GATT_FAILURE, "mtu request refused")
        } else {
            negotiatedMtu = granted
            OperationOutcome.Success(MtuInfo(negotiatedMtu = granted))
        }
    }

    // --- shared internals ---------------------------------------------------------------------------------------

    /** Take the operation lock only from a live channel, so a closed transport rejects rather than queues. */
    private suspend fun <T> guard(block: suspend () -> OperationOutcome<T>): OperationOutcome<T> {
        if (!TransportStateTransitions.canExchange(_state.value)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                    operationId = "transport.gatt.guard",
                    detail = "channel is ${_state.value}; a transport that is not connected accepts no operation",
                ),
            )
        }
        return operationLock.withLock { block() }
    }

    private fun moveTo(next: TransportState) {
        check(TransportStateTransitions.canTransition(_state.value, next)) {
            "illegal transport move ${_state.value} -> $next"
        }
        _state.value = next
    }

    private fun fail(category: OmniBudsErrorCategory, detail: String): OperationOutcome<Unit> {
        _state.value = if (TransportStateTransitions.canTransition(_state.value, TransportState.FAILED)) {
            TransportState.FAILED
        } else {
            TransportState.DISCONNECTED
        }
        return OperationOutcome.Failure(
            OmniBudsError(
                category = category,
                operationId = "transport.gatt",
                detail = detail,
            ),
        )
    }

    private fun <T> failAndReturn(category: OmniBudsErrorCategory, detail: String): OperationOutcome<T> {
        _state.value = TransportState.FAILED
        return refused(category, detail)
    }

    private fun <T> refused(category: OmniBudsErrorCategory, detail: String): OperationOutcome<T> =
        OperationOutcome.Failure(
            OmniBudsError(
                category = category,
                operationId = "transport.gatt",
                detail = detail,
            ),
        )

    private fun RawChannelResult.toOutcome(detail: String): OperationOutcome<Unit> =
        if (ok) {
            OperationOutcome.Success(Unit)
        } else {
            OperationOutcome.Failure(
                OmniBudsError(
                    category = status?.let(TransportErrorMapping::gattCategory) ?: OmniBudsErrorCategory.GATT_FAILURE,
                    operationId = "transport.gatt",
                    detail = detail,
                ),
            )
        }

    private fun error(detail: String) = OmniBudsError(
        category = OmniBudsErrorCategory.GATT_FAILURE,
        operationId = "transport.gatt",
        detail = detail,
    )

    private companion object {
        private val EMPTY = ByteArray(0)
    }
}
