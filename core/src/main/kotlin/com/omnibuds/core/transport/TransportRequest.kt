package com.omnibuds.core.transport

/**
 * One opaque request handed to a [TransportContract]: a command identity plus the bytes
 * that command maps to.
 *
 * The payload is opaque *here* on purpose. Framing, opcode values and field layout
 * belong to the protocol layer's [com.omnibuds.core.protocol.ProtocolEncoder] and are
 * carried in a [com.omnibuds.core.protocol.CommandDefinition]; the transport must not
 * interpret them, which is what keeps one protocol abstraction working over GATT,
 * RFCOMM or a vendor channel (PROTO-ABST-006, ADR-P0-003). [commandId] is the symbolic
 * name of the operation, so a log or an error can say which command was involved
 * without anybody reconstructing it from bytes (master section 52, PROTO-NOMAGIC-002).
 *
 * **Array equality is implemented by hand, and that is the point.** A data class with
 * an array property compares references by default, so two byte-identical requests
 * would be unequal while two aliases of one mutated array would be "equal" — a defect
 * that makes response correlation, caching and test assertions silently wrong.
 * [equals] and [hashCode] therefore use `contentEquals`/`contentHashCode`.
 *
 * [payload] is not defensively copied: the sender retains ownership of the array and
 * must not mutate it after construction. Copying it here would imply the transport may
 * queue a request and send the bytes that exist whenever it wakes up, which would let a
 * later mutation reach the wire.
 */
data class TransportRequest(
    /** Symbolic command identity, e.g. `example-vendor.read-battery-status`; never an opcode. */
    val commandId: String,

    /** The encoded body. Empty is legal and means "this command takes no payload". */
    val payload: ByteArray,

    /**
     * The command's own wait bound in milliseconds, or null when the definition states
     * none. An unset bound is unknown, not unlimited and not zero
     * (specs.md section 2.2), and the caller's bound in
     * [TransportContract.exchange] still applies.
     */
    val timeoutMillis: Long?,
) {

    init {
        require(commandId.isNotBlank()) {
            "commandId identifies the operation in diagnostics; a blank one is meaningless, " +
                "and null is not an option here"
        }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be unreported (null) or positive, was $timeoutMillis"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransportRequest) return false
        return commandId == other.commandId &&
            timeoutMillis == other.timeoutMillis &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = commandId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (timeoutMillis?.hashCode() ?: 0)
        return result
    }

    /**
     * Reports the command and the payload *length*, never the payload bytes.
     *
     * The generated `toString` would print an array reference, which is both useless
     * and misleading next to a content-based `equals`; printing the bytes themselves
     * would put packet content into any log line that interpolates this value, which
     * SEC-LOG-004 forbids for release builds.
     */
    override fun toString(): String =
        "TransportRequest(commandId=$commandId, payloadBytes=${payload.size}, " +
            "timeoutMillis=$timeoutMillis)"
}
