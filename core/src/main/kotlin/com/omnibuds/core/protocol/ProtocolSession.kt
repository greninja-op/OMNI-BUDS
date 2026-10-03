package com.omnibuds.core.protocol

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The boundary through which a protocol session reaches a channel, stated without any transport-specific or
 * framework type.
 *
 * Prompt §5's chain has a "Transport Adapter" between the session and the Bluetooth transport, and prompt §16
 * requires that protocols depend on transport *abstractions*, never `android.bluetooth.*`. This is that
 * abstraction: a session sends encoded bytes and receives reply bytes plus a delivery verdict, and never
 * learns whether the wire is a GATT characteristic or an RFCOMM socket. Which concrete [TransportKind] a
 * protocol needs is [requiredTransport], checked before a session is created; a mismatch fails explicitly
 * rather than falling through to another channel (PROTO-XPORT-007 — a command written to a channel nobody
 * chose is a command written nowhere).
 *
 * Phase 7 defines this contract and ships no framework implementation, because no vendor protocol exists to
 * bind it to; a later phase provides one over Phase 6's `GattTransport`/`RfcommTransport`.
 */
interface ProtocolTransportAdapter {
    /** The channel this protocol must run over; refused at session creation if the transport is not this. */
    val requiredTransport: TransportKind

    /** Whether the underlying channel is currently attached (a read of the transport's own report). */
    val isConnected: Boolean

    /** Open/attach the underlying channel. Never called implicitly by [ProtocolSession.execute]. */
    suspend fun open(): OperationOutcome<Unit>

    /** Send one encoded request and await its raw reply, honouring the smaller of the caller and request bounds. */
    suspend fun exchange(
        commandId: String,
        payload: ByteArray,
        timeoutMillis: Long,
    ): OperationOutcome<ByteArray?>

    /** Detach and release the channel; idempotent (Phase 6's close contract). */
    suspend fun close(): OperationOutcome<Unit>
}

/**
 * A live protocol session: one resolved protocol, bound to one transport adapter, with its own lifecycle,
 * operation surface and event stream.
 *
 * This is Phase 7's contribution, kept deliberately separate from [EarbudProtocol]: that type is the
 * *stateless family contract* (identify / discover capabilities / read state), while a session is the
 * *runtime instance* that speaks the protocol against a connected channel. Bolting lifecycle onto
 * `EarbudProtocol` would make state mandatory on a contract meant to be a pure description and would drag
 * Phase 8's capability discovery into this phase (ADR-P7-002).
 *
 * What a session guarantees:
 *  - [state] is the single authority for where the session has got to; it reaches [ProtocolState.READY] only
 *    when [initialize] succeeds (prompt §12). Nothing outside the session writes it.
 *  - [execute] is refused unless the session is ready, and a command is issued at most once — a session never
 *    blindly re-sends a side-effecting command after a failure (ADR-P7-004; specs §4); re-reading to resolve a
 *    timeout is a different, later action, not a retry here.
 *  - [events] is a bounded, cancellation-safe stream of device-originated edges, distinct from requested state
 *    ([ProtocolEvent], prompt §13).
 *  - [requiredTransport] is checked against the adapter before the session is usable; an unavailable transport
 *    yields a typed refusal, and no operation is attempted on a substitute channel.
 */
interface ProtocolSession {
    /** The record this session speaks; its `transport` must match [requiredTransport]. */
    val descriptor: ProtocolDefinition

    /** The channel this session must run over, taken from [descriptor] so a caller cannot choose it. */
    val requiredTransport: TransportKind
        get() = descriptor.transport

    /** The authoritative lifecycle state. A snapshot consumer reads this; events are edges over it. */
    val state: StateFlow<ProtocolState>

    /** Device-originated updates, bounded and cancellation-safe. Never a history and never the truth of state. */
    val events: Flow<ProtocolEvent>

    /**
     * Bring the session up: attach the transport if needed and run the protocol's initialization contract.
     *
     * Moves `CREATED → INITIALIZING → READY` (or to `DEGRADED`/`FAILED`). It does not retry an unsafe
     * command on its own; a failure is returned, and re-initialization is the owner's explicit decision
     * (prompt §12's "do not automatically retry unsafe initialization commands").
     */
    suspend fun initialize(): OperationOutcome<Unit>

    /**
     * Issue one command and await its correlated reply.
     *
     * Refused with `INVALID_STATE` when the session is not [ProtocolState.READY]. The command is sent once;
     * a timeout returns `TIMEOUT` and is *not* evidence the write failed, so the caller must read state rather
     * than re-send (ADR-P7-004). Responses are parsed by the protocol's [ProtocolParser]; a malformed reply is
     * a structured failure, never a crash or a fabricated value.
     */
    suspend fun execute(command: ProtocolCommand): OperationOutcome<ProtocolResponse>

    /** Close the session and release its adapter; idempotent, and terminal for this instance. */
    suspend fun close(): OperationOutcome<Unit>
}
