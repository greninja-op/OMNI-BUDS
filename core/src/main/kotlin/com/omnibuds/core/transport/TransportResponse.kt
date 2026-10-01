package com.omnibuds.core.transport

/**
 * One opaque response a [TransportContract] came back with, bound to the command it
 * answers.
 *
 * The transport's whole job is to report what arrived. It does not parse: turning these
 * bytes into a domain value is [com.omnibuds.core.protocol.ProtocolParser]'s
 * responsibility, and a response that contradicts the definition is a
 * `OmniBudsErrorCategory.PROTOCOL_MISMATCH` raised there rather than a defaulted value
 * (PROTO-ERR-001, specs.md section 3 rule 5).
 *
 * [payload] is null when the channel reported nothing back. That is different from an
 * empty array, which means "a zero-length body arrived", and the difference is kept
 * because collapsing them would let a missing answer be parsed as if it were a real
 * one. Unknown stays unknown (specs.md section 2.2, ADR-P0-016).
 *
 * [acknowledged] is the narrowest claim this type can make: the channel accepted or
 * denied delivery. It is rung 2 of the persistence ladder at best — "command accepted"
 * — and establishes neither that the value was applied, nor that it is readable, nor
 * that it survives a reconnect (PROTO-PERSIST-001, master section 24).
 *
 * As with [TransportRequest], the array field needs hand-written equality: the default
 * data-class implementation compares references, which makes two byte-identical
 * responses unequal and lets a mutated array retroactively "match" an old one.
 */
data class TransportResponse(
    /** Symbolic identity of the command this response belongs to. */
    val commandId: String,

    /** Response body as delivered, or null when nothing was delivered. */
    val payload: ByteArray?,

    /**
     * Whether the channel acknowledged the exchange.
     *
     * Delivery-level only; see the class documentation for what it does not mean.
     */
    val acknowledged: Boolean,
) {

    init {
        require(commandId.isNotBlank()) {
            "commandId correlates this response to an operation; a blank one cannot be correlated"
        }
    }

    /** Whether a body arrived at all, as opposed to an arrived-but-empty body. */
    val hasPayload: Boolean
        get() = payload != null

    /** How many body bytes arrived; zero both for an absent body and for an empty one. */
    val payloadByteCount: Int
        get() = payload?.size ?: 0

    override fun equals(other: Any?): Boolean {
        if (other !is TransportResponse) return false
        return commandId == other.commandId &&
            acknowledged == other.acknowledged &&
            payloadsMatch(other.payload)
    }

    override fun hashCode(): Int {
        var result = commandId.hashCode()
        result = 31 * result + acknowledged.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }

    /** Null and an empty array are distinct states, so they must not compare equal. */
    private fun payloadsMatch(other: ByteArray?): Boolean {
        val local = payload
        return when {
            local === null -> other === null
            other === null -> false
            else -> local.contentEquals(other)
        }
    }

    /** Reports the correlation key and the body length, never the body bytes (SEC-LOG-004). */
    override fun toString(): String =
        "TransportResponse(commandId=$commandId, payloadBytes=$payloadByteCount, " +
            "payloadReported=${payload != null}, acknowledged=$acknowledged)"
}
