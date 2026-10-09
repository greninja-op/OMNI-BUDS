package com.omnibuds.core.protocol.version

/**
 * Version-aware message codec contract.
 *
 * Enforces explicit wire framing and serialization differences across protocol revisions
 * without guessing packet fields or silently tolerating malformed frames.
 */
interface VersionedMessageCodec<TCommand, TResponse> {

    /** The protocol version this codec handles. */
    val protocolVersion: ProtocolVersion

    /**
     * Encode a command into raw wire bytes according to this version's wire contract.
     */
    fun encodeCommand(command: TCommand): ByteArray

    /**
     * Decode a response from raw wire bytes according to this version's wire contract.
     * Returns null if bytes are malformed, truncated, or incompatible.
     */
    fun decodeResponse(payload: ByteArray): TResponse?
}

/**
 * Wire codec outcome representing deterministic parse results.
 */
sealed interface CodecResult<out T> {
    data class Success<T>(val value: T) : CodecResult<T>
    data class MalformedData(val reason: String) : CodecResult<Nothing>
    data class IncompatibleVersion(val observedVersion: Int, val supportedVersion: Int) : CodecResult<Nothing>
    data class TruncatedFrame(val expectedLength: Int, val actualLength: Int) : CodecResult<Nothing>
}
