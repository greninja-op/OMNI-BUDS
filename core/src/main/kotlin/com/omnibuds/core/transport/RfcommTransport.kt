package com.omnibuds.core.transport

import com.omnibuds.core.common.OperationOutcome

/**
 * The serial port profile over classic Bluetooth: RFCOMM, a streamed bidirectional socket.
 *
 * This is the boundary that carries the argument for the whole transport abstraction. Master
 * section 8 records that a device "may use BLE for advertisements/bookkeeping" and "RFCOMM for
 * control", and `protocol-governance.md` section 2 (PROTO-XPORT-002) states the rule directly: for
 * devices where configuration is classic-only, RFCOMM is the control path and opening it requires
 * "an explicit open attempt" — never an inferred one. A vendor that publishes a GATT service for
 * battery reporting and puts its ANC and EQ commands on an SPP socket is a normal device, not an
 * edge case (ADR-P0-003). Keeping RFCOMM as its own boundary is what allows a session to hold GATT
 * and RFCOMM simultaneously for different purposes (PROTO-XPORT-005) without either being mistaken
 * for the other.
 *
 * The boundary pins [com.omnibuds.core.common.TransportKind.RFCOMM] as its [kind] and inherits
 * [BluetoothTransport.probeAvailability]. Failure on this channel is its own category,
 * [com.omnibuds.core.common.OmniBudsErrorCategory.RFCOMM_FAILURE], distinct from
 * [com.omnibuds.core.common.OmniBudsErrorCategory.GATT_FAILURE], because a socket-level failure and
 * an attribute-level failure imply different retry behaviour (`protocol-governance.md` section 13:
 * both are "reads only, capped" but name different layers).
 *
 * **What would be fabricated to add a member now:** an RFCOMM channel is a socket with a channel
 * number, a service UUID to connect against, and an input/output stream. Naming any of those would
 * be a socket parameter, which OQ-PROTO-01 explicitly defers to Phase 6; it would also presume the
 * service record whose discovery Phase 2 prompt section 6 forbids, and it would put an unproven
 * UUID in code, which PROTO-NOMAGIC-002 forbids. Framing is not transport's either: RFCOMM delivers
 * a byte stream with no message boundaries, so inventing a "send one command" member here would
 * assert a framing the protocol layer has not defined (PROTO-ABST-006).
 *
 * Implementing phase: Phase 6 (`BluetoothOperation.TRANSPORT_RFCOMM_OPEN`, authorizedInPhase = 6).
 * Before then every honest statement about this channel is a candidate record —
 * [TransportAvailability] or [TransportBoundary] — not an open socket.
 */
interface RfcommTransport : BluetoothTransport {

    /** Pinned: this boundary answers for exactly one transport kind, never another. */
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.RFCOMM

    /**
     * The service record or channel this socket will connect to, supplied by a caller who resolved it from
     * evidence — never a value this layer guesses (PROTO-NOMAGIC-002). [BluetoothTransport]'s inherited
     * `open()` connects to exactly this endpoint.
     */
    val endpoint: RfcommEndpoint

    /**
     * Read the bytes currently delivered by the stream.
     *
     * An RFCOMM socket carries no message boundaries, so one read may return a partial message, an exact
     * one, or several — and this type deliberately does not pretend otherwise. Turning a byte run into a
     * message is framing, which belongs to the protocol layer (PROTO-ABST-006), so the transport neither
     * buffers to a delimiter nor splits on one. An empty array is "nothing available this call", distinct
     * from a [com.omnibuds.core.common.OmniBudsErrorCategory] failure, and a closed channel refuses rather
     * than blocking forever.
     */
    suspend fun read(): OperationOutcome<ByteArray>

    /**
     * Write bytes to the stream as given, with no framing and no implicit retry.
     *
     * Concurrent writes are serialised by the implementation (prompt §10's "prevent concurrent writes from
     * corrupting future framing"): the ordering that a later protocol will depend on is established here
     * at the mechanism level, even though the bytes themselves are opaque.
     */
    suspend fun write(bytes: ByteArray): OperationOutcome<Unit>
}
