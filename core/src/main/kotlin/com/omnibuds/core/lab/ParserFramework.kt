package com.omnibuds.core.lab

/**
 * Parser framework contracts.
 *
 * Phase 20 (OB-P20-REQ-008/009/010): reusable framing + typed outcomes.
 * Parsers analyze data; they never transmit.
 */
interface LabParser {
    /** Stable parser identifier, e.g. `lab.framing.length-prefixed`. */
    val parserId: String

    /** Protocol this parser understands, or null for generic framing. */
    val protocolId: String?

    /** Minimum input bytes to attempt parsing. */
    val minInputBytes: Int

    /** Maximum input bytes accepted. */
    val maxInputBytes: Int

    /** Parse one message from the head of [input]. */
    fun parse(input: ByteArray): ParseOutcome
}

/** Typed parser outcomes. */
sealed interface ParseOutcome {
    /** A complete message was parsed. */
    data class Parsed(
        val message: StructuredMessage,
        /** Bytes consumed; remainder may hold more messages. */
        val bytesConsumed: Int,
    ) : ParseOutcome

    /** Need more input; not an error. */
    data class Incomplete(val bytesNeeded: Int?) : ParseOutcome

    /** Input doesn't match this parser's format. */
    data object UnsupportedFormat : ParseOutcome

    /** Structurally invalid. */
    data class Malformed(val reason: String) : ParseOutcome

    /** Protocol version not supported. */
    data class UnsupportedVersion(val version: String?) : ParseOutcome

    /** Input exceeds resource limits. */
    data object LimitExceeded : ParseOutcome
}

/** A parsed message with typed fields. */
data class StructuredMessage(
    val messageType: String,
    /** Null when the type is unknown — meaning stays unknown. */
    val semanticMeaning: String?,
    val direction: TraceDirection,
    val headerFields: Map<String, String>,
    val payloadFields: Map<String, String>,
    /** Fields whose meaning is not established. */
    val unknownFields: Map<String, String>,
    val totalLength: Int,
    val sequenceNumber: Long?,
    val checksumValid: Boolean?,
    val sourceEventId: String?,
) {
    init {
        require(messageType.isNotBlank()) { "messageType must not be blank" }
        require(totalLength >= 0) { "totalLength must be non-negative" }
    }
}

/**
 * Length-prefixed framing parser.
 *
 * Format: [2-byte big-endian length][payload]. A generic framing example —
 * not a vendor protocol.
 */
class LengthPrefixedParser : LabParser {
    override val parserId: String = "lab.framing.length-prefixed"
    override val protocolId: String? = null
    override val minInputBytes: Int = 2
    override val maxInputBytes: Int = LabLimits.MAX_MESSAGE_BYTES + 2

    override fun parse(input: ByteArray): ParseOutcome {
        if (input.size > maxInputBytes) return ParseOutcome.LimitExceeded
        if (input.size < 2) return ParseOutcome.Incomplete(2 - input.size)
        // Big-endian 16-bit length. (255 = 0xFF byte mask; standard idiom,
        // not a protocol literal.)
        val length = ((input[0].toInt() and 255) shl 8) or (input[1].toInt() and 255)
        if (length > LabLimits.MAX_MESSAGE_BYTES) {
            return ParseOutcome.Malformed("declared length $length exceeds limit")
        }
        if (input.size < 2 + length) {
            return ParseOutcome.Incomplete(2 + length - input.size)
        }
        val payload = input.copyOfRange(2, 2 + length)
        return ParseOutcome.Parsed(
            message = StructuredMessage(
                messageType = "length-prefixed",
                semanticMeaning = null, // Unknown — framing only, no semantics.
                direction = TraceDirection.UNKNOWN,
                headerFields = mapOf("length" to length.toString()),
                payloadFields = mapOf("payloadHex" to payload.toHex()),
                unknownFields = mapOf("payloadHex" to payload.toHex()),
                totalLength = 2 + length,
                sequenceNumber = null,
                checksumValid = null,
                sourceEventId = null,
            ),
            bytesConsumed = 2 + length,
        )
    }
}

/** Bounded hex rendering for inspection. */
fun ByteArray.toHex(maxBytes: Int = 64): String {
    val shown = take(maxBytes).joinToString("") {
        "%02x".format(it)
    }
    return if (size > maxBytes) "$shown…(+${size - maxBytes} bytes)" else shown
}
