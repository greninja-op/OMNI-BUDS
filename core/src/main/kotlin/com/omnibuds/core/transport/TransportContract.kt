package com.omnibuds.core.transport

import com.omnibuds.core.common.TransportKind

import com.omnibuds.core.common.OperationOutcome

/**
 * What a control channel must be able to do, stated as a contract and nothing else.
 *
 * This type exists so that one higher-level protocol abstraction can drive a GATT
 * device, an RFCOMM device and a vendor-specific device without any layer above the
 * platform implementation knowing which it is (ADR-P0-003, master section 8). It is
 * deliberately silent about *how*: no characteristic handle, no socket parameter, no
 * callback shape, no UUID, because PROTO-ABST-006 forbids expressing protocol
 * semantics in transport primitives, and OQ-PROTO-01 defers the concrete transport
 * contract to the phase that owns it.
 *
 * **Nothing in `:core` may implement this interface.** Opening a transport is the
 * platform layer's job: the platform module attaches a channel, and it is the only
 * code allowed to touch a Bluetooth API (ADR-P0-003, ADR-P0-008, Phase 1 prompt
 * sections 2, 32 and 51 — Bluetooth connection, GATT, RFCOMM, sockets and vendor
 * packet transmission are forbidden here). A Phase 1 implementation of this interface
 * would necessarily be a fake that claims to work, which is what prompt section 53
 * prohibits. Test doubles belong in test infrastructure, never in main source.
 *
 * Consequences of treating this as a pure contract:
 *  - [open] and [close] report what the platform said; they never imply that a device
 *    is controllable, and a session may legitimately hold zero open channels while
 *    still being readable for other purposes (PROTO-XPORT-004).
 *  - [exchange] is the only way bytes move. It is `suspend`, so the blocking client
 *    API stays confined inside the implementation and cancellation propagates
 *    (specs.md section 5.2, 5.3).
 *  - A channel that is missing or refuses to open yields a `Failure` carrying
 *    `OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE` for *this* [kind]. Falling through
 *    to a different transport is prohibited (PROTO-XPORT-007); a caller that wants
 *    another channel must ask for it explicitly.
 *  - Writes to one channel are serialised by the implementation, because response
 *    correlation is impossible otherwise (specs.md section 5.9).
 */
interface TransportContract {

    /** Which kind of channel this is; a value, never inferred from a vendor or model name. */
    val kind: TransportKind

    /**
     * Whether the channel is currently attached and able to carry a request.
     *
     * A snapshot of the platform's report, not a promise that the next [exchange]
     * succeeds: a device can leave at any moment, which is why [exchange] still has
     * to be able to fail with `DEVICE_DISCONNECTED`.
     */
    val isOpen: Boolean

    /**
     * Attach the channel.
     *
     * Success means the platform reported the channel open; it says nothing about
     * which protocol speaks over it or what the device can do.
     */
    suspend fun open(): OperationOutcome<Unit>

    /**
     * Detach the channel and release the platform handle.
     *
     * Must be safe to call when already closed, and must not be treated as a failure
     * path that retries: a half-torn-down channel is reported, not re-closed in a loop.
     */
    suspend fun close(): OperationOutcome<Unit>

    /**
     * Send one request and await its response.
     *
     * [timeoutMillis] is the caller's wait bound for this single exchange; a timeout is
     * a `TIMEOUT` failure and never a silent null or a fabricated response
     * (specs.md section 5.4). When the request carries its own
     * [TransportRequest.timeoutMillis], the implementation must not wait longer than
     * the smaller of the two bounds — an operation may be given less time than it
     * asked for, never more than its definition permits.
     *
     * This method does not retry. Retrying is a decision about effects, and the only
     * place that decision is recorded is [com.omnibuds.core.protocol.EffectClass] on a
     * command definition (specs.md section 4, rule 3): a transport that re-sent a
     * timed-out write on its own would compound an effect nobody observed.
     */
    suspend fun exchange(
        request: TransportRequest,
        timeoutMillis: Long,
    ): OperationOutcome<TransportResponse>
}
