package com.omnibuds.android.bluetooth.transport

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.transport.RfcommEndpoint
import com.omnibuds.core.transport.RfcommTransport
import com.omnibuds.core.transport.TransportAvailability
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportResponse
import com.omnibuds.core.transport.TransportState
import com.omnibuds.core.transport.TransportStateTransitions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The narrow seam over one RFCOMM socket, phrased without a framework type, for the same reason
 * [GattTransportHandle] exists: the lifecycle and error decisions stay testable above the socket.
 *
 * An RFCOMM channel is a raw byte stream with no message boundaries, so the seam offers only
 * connect/read/write/close and deliberately nothing that could imply a packet — framing is the protocol
 * layer's (PROTO-ABST-006), and a read here may return a partial or concatenated run exactly as the
 * socket delivered it.
 */
interface RfcommTransportHandle {
    val endpoint: RfcommEndpoint

    /** Whether the socket *could* be opened (adapter on, permission, API present); creates no socket. */
    suspend fun probe(): RawChannelResult

    /** Open the socket against [endpoint]. [RawConnectResult] distinguishes confirmed from refused from silent. */
    suspend fun connect(): RawConnectResult

    /** Read whatever bytes are currently available; an empty run means "nothing right now", not a failure. */
    suspend fun read(): RawReadResult

    suspend fun write(bytes: ByteArray): RawChannelResult

    suspend fun close(): RawChannelResult
}

/**
 * The Android-driven RFCOMM control channel, implementing the `:core` [RfcommTransport] over a
 * [RfcommTransportHandle].
 *
 * Like [AndroidGattTransport] this holds the testable logic and no framework; `BluetoothSocket` is confined
 * to [SystemRfcommTransportHandle] (ADR-P6-008, ceiling `IMPLEMENTED`). RFCOMM *is* a byte stream, so unlike
 * GATT it can carry the generic [exchange]: a request's bytes are written whole and the response is the next
 * run the socket yields — still without the transport inventing where a message ends, which stays the
 * protocol layer's concern.
 */
class AndroidRfcommTransport(
    private val handle: RfcommTransportHandle,
) : RfcommTransport {

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val operationLock = Mutex()

    override val kind: TransportKind get() = TransportKind.RFCOMM
    override val isOpen: Boolean get() = _state.value == TransportState.CONNECTED
    override val endpoint: RfcommEndpoint get() = handle.endpoint

    override suspend fun open(): OperationOutcome<Unit> = operationLock.withLock {
        if (_state.value == TransportState.CONNECTED) return@withLock OperationOutcome.Success(Unit)
        if (!TransportStateTransitions.canTransition(_state.value, TransportState.CONNECTING)) {
            return@withLock OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "transport.rfcomm.open",
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
            is RawConnectResult.Refused -> fail(TransportErrorMapping.connectCategory(result.status), "rfcomm connect refused")
            RawConnectResult.Unanswered -> fail(OmniBudsErrorCategory.TIMEOUT, "rfcomm connect unresolved")
        }
    }

    override suspend fun close(): OperationOutcome<Unit> = operationLock.withLock {
        if (_state.value == TransportState.CLOSED) return@withLock OperationOutcome.Success(Unit)
        moveTo(TransportState.CLOSING)
        val result = handle.close()
        _state.value = if (result.ok) TransportState.CLOSED else TransportState.FAILED
        if (result.ok) OperationOutcome.Success(Unit) else OperationOutcome.Failure(err("rfcomm close failed"))
    }

    override suspend fun probeAvailability(): OperationOutcome<TransportAvailability> {
        val result = handle.probe()
        return if (result.ok) {
            OperationOutcome.Success(TransportAvailability.available(TransportKind.RFCOMM))
        } else {
            val category = result.status?.let(TransportErrorMapping::gattCategory)
                ?: OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE
            OperationOutcome.Success(TransportAvailability.unavailable(TransportKind.RFCOMM, category))
        }
    }

    override suspend fun read(): OperationOutcome<ByteArray> = guard {
        when (val result = handle.read()) {
            is RawReadResult.Value -> OperationOutcome.Success(result.bytes)
            RawReadResult.Missing -> OperationOutcome.Success(ByteArray(0))
            is RawReadResult.Failed -> failReturning(OmniBudsErrorCategory.RFCOMM_FAILURE, "rfcomm read failed")
        }
    }

    override suspend fun write(bytes: ByteArray): OperationOutcome<Unit> = guard {
        val result = handle.write(bytes)
        if (result.ok) OperationOutcome.Success(Unit) else failReturning(OmniBudsErrorCategory.RFCOMM_FAILURE, "rfcomm write rejected")
    }

    /**
     * Send one request's bytes and await the next delivered run — the byte-stream reading of
     * [com.omnibuds.core.transport.TransportContract.exchange]. It writes whole and reads once; it does not
     * loop until a delimiter arrives, because knowing where a response ends is a framing decision this layer
     * does not own. The returned [TransportResponse.acknowledged] is delivery-level only (prompt §10).
     */
    override suspend fun exchange(request: TransportRequest, timeoutMillis: Long): OperationOutcome<TransportResponse> =
        guard {
            val written = handle.write(request.payload)
            if (!written.ok) {
                return@guard OperationOutcome.Failure(err("rfcomm exchange write rejected"))
            }
            when (val read = handle.read()) {
                is RawReadResult.Value -> OperationOutcome.Success(
                    TransportResponse(commandId = request.commandId, payload = read.bytes, acknowledged = true),
                )
                RawReadResult.Missing -> OperationOutcome.Success(
                    TransportResponse(commandId = request.commandId, payload = null, acknowledged = true),
                )
                is RawReadResult.Failed -> failReturning(OmniBudsErrorCategory.RFCOMM_FAILURE, "rfcomm exchange read failed")
            }
        }

    // --- shared internals, mirroring AndroidGattTransport's discipline ------------------------------------------

    private suspend fun <T> guard(block: suspend () -> OperationOutcome<T>): OperationOutcome<T> {
        if (!TransportStateTransitions.canExchange(_state.value)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.RESOURCE_UNAVAILABLE,
                    operationId = "transport.rfcomm.guard",
                    detail = "channel is ${_state.value}; a transport that is not connected accepts no operation",
                ),
            )
        }
        return operationLock.withLock { block() }
    }

    private fun moveTo(next: TransportState) {
        check(TransportStateTransitions.canTransition(_state.value, next)) { "illegal transport move ${_state.value} -> $next" }
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
                operationId = "transport.rfcomm",
                detail = detail,
            ),
        )
    }

    private fun <T> failReturning(category: OmniBudsErrorCategory, detail: String): OperationOutcome<T> {
        _state.value = TransportState.FAILED
        return OperationOutcome.Failure(err(detail))
    }

    private fun err(detail: String) = OmniBudsError(
        category = OmniBudsErrorCategory.RFCOMM_FAILURE,
        operationId = "transport.rfcomm",
        detail = detail,
    )
}
