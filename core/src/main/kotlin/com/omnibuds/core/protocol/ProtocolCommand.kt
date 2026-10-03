package com.omnibuds.core.protocol

/**
 * A protocol operation in flight: the definition being invoked, the bytes to send, and the identity used to
 * match the reply.
 *
 * This is the runtime counterpart to the static [CommandDefinition]. The definition says an operation
 * *exists* and how it behaves; this says "issue *this* one now, and correlate its answer to this token".
 * [payload] is opaque at this layer — turning fields into bytes is a [ProtocolEncoder]'s job and the reverse
 * a [ProtocolParser]'s, so a command never carries a decoded domain value that would smuggle protocol
 * meaning into a transport-shaped call (PROTO-ABST-006).
 *
 * [correlationId] is required and non-blank: prompt §11 makes response correlation deterministic, and a reply
 * that cannot be matched to the request that caused it is unattributable evidence. It is supplied by the
 * caller (or derived from a per-session counter) rather than minted from wall-clock or randomness inside
 * `:core`, so a test drives it and two identical commands with different tokens stay distinct.
 *
 * [MAX_PAYLOAD_BYTES] is prompt §11's bound and prompt §15's "validate payload lengths": an over-large
 * payload is refused at construction rather than sent and blamed on the device.
 */
data class ProtocolCommand(
    /** The [CommandDefinition.id] being invoked, unique within the session's protocol. */
    val commandId: String,

    /** The encoded body; empty is legal and means "this command takes no payload". */
    val payload: ByteArray,

    /** The token the reply must carry to be attributable to this call. */
    val correlationId: String,

    /**
     * This call's wait bound in milliseconds, or null to inherit the session's bound.
     *
     * The effective timeout is the smaller of this and the definition's — an operation may be given less
     * time than it declared, never more (mirrors Phase 6's `exchange` bound rule).
     */
    val timeoutMillis: Long?,
) {
    init {
        require(commandId.isNotBlank()) { "commandId names the operation; a blank one cannot be correlated" }
        require(correlationId.isNotBlank()) {
            "a command with no correlation id produces an unattributable reply (prompt section 11)"
        }
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "payload of ${payload.size} bytes exceeds the $MAX_PAYLOAD_BYTES protocol bound"
        }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be unreported (null) or positive, was $timeoutMillis"
        }
    }

    // Array equality by hand, the same reason TransportRequest does it: a data class compares array
    // references, which would make two byte-identical commands unequal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolCommand) return false
        return commandId == other.commandId &&
            correlationId == other.correlationId &&
            timeoutMillis == other.timeoutMillis &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = commandId.hashCode()
        result = 31 * result + correlationId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (timeoutMillis?.hashCode() ?: 0)
        return result
    }

    /** Reports the correlation token and the length, never the bytes (SEC-LOG-004). */
    override fun toString(): String =
        "ProtocolCommand(commandId=$commandId, correlationId=$correlationId, " +
            "payloadBytes=${payload.size}, timeoutMillis=$timeoutMillis)"

    companion object {
        /** Bound on one command's encoded body, so no caller sends an unbounded payload. */
        const val MAX_PAYLOAD_BYTES: Int = 512
    }
}

/**
 * A protocol reply: the correlation token, the delivered bytes, and how strongly they may be read as an
 * effect.
 *
 * [payload] is null when the device answered with nothing, which is distinct from an empty array — the same
 * null-versus-empty discipline [com.omnibuds.core.transport.TransportResponse] keeps, so a missing answer is
 * never parsed as a real one (ADR-P0-016). [acknowledged] is delivery-level only: it is prompt's closing
 * "a successful command request is not proof the device applied the change", so a session that wants the
 * applied value must *read it back*, not assume it from an ack.
 *
 * A malformed reply is not a `ProtocolResponse` at all — it becomes a structured failure from the parser,
 * never a half-populated response object (prompt §11/§15's "malformed responses must not crash the engine").
 */
data class ProtocolResponse(
    val commandId: String,
    val correlationId: String,
    val payload: ByteArray?,
    val acknowledged: Boolean,
) {
    init {
        require(commandId.isNotBlank()) { "a reply must say which command it answers" }
        require(correlationId.isNotBlank()) { "a reply with no correlation token cannot be matched to a call" }
    }

    val hasPayload: Boolean get() = payload != null
    val payloadByteCount: Int get() = payload?.size ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolResponse) return false
        return commandId == other.commandId &&
            correlationId == other.correlationId &&
            acknowledged == other.acknowledged &&
            payload.contentEqualsNullable(other.payload)
    }

    override fun hashCode(): Int {
        var result = commandId.hashCode()
        result = 31 * result + correlationId.hashCode()
        result = 31 * result + acknowledged.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ProtocolResponse(commandId=$commandId, correlationId=$correlationId, " +
            "payloadBytes=$payloadByteCount, payloadReported=${payload != null}, acknowledged=$acknowledged)"

    /** null and an empty array are distinct states and must not compare equal. */
    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
        this === null -> other === null
        other === null -> false
        else -> this.contentEquals(other)
    }
}
