package com.omnibuds.core.lab

/**
 * Trace validation: structured errors, never silent repair.
 *
 * Phase 20 (OB-P20-REQ-004): malformed traces are rejected or quarantined
 * with field paths and diagnostics.
 */
object TraceValidator {

    /** Validate a trace; returns errors (empty = valid). */
    fun validate(trace: ProtocolTrace): List<TraceValidationError> {
        val errors = mutableListOf<TraceValidationError>()

        // Duplicate event IDs.
        val ids = trace.events.map { it.eventId }
        ids.groupingBy { it }.eachCount()
            .filter { it.value > 1 }
            .forEach { (id, _) ->
                errors.add(TraceValidationError("events", "duplicate event id: $id"))
            }

        // Sequence ordering.
        trace.events.zipWithNext { a, b ->
            if (b.sequence <= a.sequence) {
                errors.add(
                    TraceValidationError(
                        "events[${b.eventId}]",
                        "sequence ${b.sequence} not after ${a.sequence}",
                    ),
                )
            }
        }

        // Timestamp ordering (when both present).
        trace.events.zipWithNext { a, b ->
            val ta = a.relativeMillis
            val tb = b.relativeMillis
            if (ta != null && tb != null && tb < ta) {
                errors.add(
                    TraceValidationError(
                        "events[${b.eventId}]",
                        "timestamp $tb before $ta",
                    ),
                )
            }
        }

        // Redaction consistency.
        if (trace.redaction.structureAltered && trace.redaction.redactedFields.isEmpty()) {
            errors.add(
                TraceValidationError(
                    "redaction",
                    "structureAltered but no redacted fields listed",
                ),
            )
        }

        return errors
    }

    /** True when the trace is safe to analyze. */
    fun isValid(trace: ProtocolTrace): Boolean = validate(trace).isEmpty()
}

/** One validation failure, with a field path. */
data class TraceValidationError(
    val fieldPath: String,
    val message: String,
)
