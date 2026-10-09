package com.omnibuds.core.lab

import com.omnibuds.core.common.TransportKind

/**
 * Versioned protocol trace.
 *
 * Phase 20 (OB-P20-REQ-001): offline, sanitized protocol observations.
 * Traces are DATA — importing a trace never executes anything.
 */
data class ProtocolTrace(
    val traceId: String,
    val formatVersion: Int,
    val createdAtMillis: Long,
    val sourceType: TraceSourceType,
    val transport: TransportKind,
    val protocolId: String?,
    val protocolVersion: String?,
    val deviceModel: String?,
    val firmware: String?,
    val sessionId: String?,
    val events: List<TraceEvent>,
    val redaction: RedactionMetadata,
    val provenance: String,
    val limitations: List<String> = emptyList(),
) {
    init {
        require(traceId.isNotBlank()) { "traceId must not be blank" }
        require(formatVersion == TraceFormatVersion.CURRENT) {
            "unsupported trace format version $formatVersion"
        }
        require(events.size <= LabLimits.MAX_EVENTS) {
            "too many events: ${events.size}"
        }
    }

    companion object {
        fun create(
            traceId: String,
            sourceType: TraceSourceType,
            transport: TransportKind,
            events: List<TraceEvent>,
            provenance: String,
            nowMillis: Long,
        ): ProtocolTrace = ProtocolTrace(
            traceId = traceId,
            formatVersion = TraceFormatVersion.CURRENT,
            createdAtMillis = nowMillis,
            sourceType = sourceType,
            transport = transport,
            protocolId = null,
            protocolVersion = null,
            deviceModel = null,
            firmware = null,
            sessionId = null,
            events = events,
            redaction = RedactionMetadata.none(),
            provenance = provenance,
        )
    }
}

/** Current trace format version. */
object TraceFormatVersion {
    const val CURRENT: Int = 1
    const val MIN_SUPPORTED: Int = 1
}

/** Where a trace came from. Synthetic is never labeled as captured. */
enum class TraceSourceType {
    SYNTHETIC_FIXTURE,
    DOCUMENTED_EXAMPLE,
    SANITIZED_CAPTURE,
    IMPORTED_TRACE,
    GENERATED_TEST_CASE,
}

/** Message direction. */
enum class TraceDirection {
    HOST_TO_DEVICE,
    DEVICE_TO_HOST,
    OBSERVATION,
    UNKNOWN,
}

/** One event in a trace. */
data class TraceEvent(
    val eventId: String,
    /** Monotonic sequence within the trace. */
    val sequence: Long,
    /** Relative milliseconds from trace start, or null when unknown. */
    val relativeMillis: Long?,
    val direction: TraceDirection,
    val category: String,
    /** Payload bytes, or null when redacted/unavailable. */
    val payload: ByteArray?,
    val declaredLength: Int?,
    val correlationId: String?,
    val redacted: Boolean,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(sequence >= 0) { "sequence must be non-negative" }
        if (payload != null) {
            require(payload.size <= LabLimits.MAX_PAYLOAD_BYTES) {
                "payload too large: ${payload.size}"
            }
            if (declaredLength != null) {
                require(declaredLength == payload.size) {
                    "declared length $declaredLength != actual ${payload.size}"
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TraceEvent) return false
        return eventId == other.eventId && sequence == other.sequence &&
            relativeMillis == other.relativeMillis && direction == other.direction &&
            category == other.category &&
            ((payload == null && other.payload == null) ||
                (payload != null && other.payload != null && payload.contentEquals(other.payload))) &&
            declaredLength == other.declaredLength &&
            correlationId == other.correlationId && redacted == other.redacted
    }

    override fun hashCode(): Int {
        var result = eventId.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }
}

/** What was redacted and how. */
data class RedactionMetadata(
    val redactedFields: List<String>,
    /** True when redaction may have changed byte offsets/lengths/checksums. */
    val structureAltered: Boolean,
) {
    companion object {
        fun none(): RedactionMetadata = RedactionMetadata(emptyList(), false)
    }
}

/** Conservative resource limits for the laboratory. */
object LabLimits {
    const val MAX_TRACE_FILE_BYTES: Int = 4 * 1024 * 1024 // 4 MiB
    const val MAX_EVENTS: Int = 10_000
    const val MAX_PAYLOAD_BYTES: Int = 64 * 1024 // 64 KiB
    const val MAX_TOTAL_DECODED_BYTES: Long = 16L * 1024 * 1024 // 16 MiB
    const val MAX_MESSAGE_BYTES: Int = 4 * 1024 // 4 KiB
    const val MAX_CONCURRENT_TRACES: Int = 4
}
