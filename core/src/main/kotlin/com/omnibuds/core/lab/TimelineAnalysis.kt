package com.omnibuds.core.lab

/**
 * Session timeline + differential analysis.
 *
 * Phase 20 (OB-P20-REQ-014/015): ordered domain timeline; deterministic
 * comparison that never infers semantics from byte differences.
 */
object TimelineBuilder {

    /** Build an ordered timeline from a trace. */
    fun build(trace: ProtocolTrace): List<TimelineEntry> =
        trace.events.sortedBy { it.sequence }.map { event ->
            TimelineEntry(
                sequence = event.sequence,
                relativeMillis = event.relativeMillis,
                kind = when (event.direction) {
                    TraceDirection.HOST_TO_DEVICE -> TimelineKind.HOST_MESSAGE
                    TraceDirection.DEVICE_TO_HOST -> TimelineKind.DEVICE_MESSAGE
                    TraceDirection.OBSERVATION -> TimelineKind.OBSERVATION
                    TraceDirection.UNKNOWN -> TimelineKind.UNKNOWN_EVENT
                },
                eventId = event.eventId,
                annotation = null,
            )
        }
}

/** A timeline entry. */
data class TimelineEntry(
    val sequence: Long,
    val relativeMillis: Long?,
    val kind: TimelineKind,
    val eventId: String,
    val annotation: String?,
)

/** Kinds of timeline entries. */
enum class TimelineKind {
    HOST_MESSAGE,
    DEVICE_MESSAGE,
    OBSERVATION,
    TIMEOUT,
    PARSER_FAILURE,
    UNKNOWN_EVENT,
    REDACTION_EVENT,
}

/**
 * Deterministic trace comparison.
 *
 * Phase 20: reports structural differences; never assigns meaning.
 */
object DifferentialAnalyzer {

    /** Compare two traces structurally. */
    fun compare(a: ProtocolTrace, b: ProtocolTrace): TraceDiff {
        val differences = mutableListOf<String>()

        if (a.events.size != b.events.size) {
            differences.add(
                "event count differs: ${a.events.size} vs ${b.events.size}",
            )
        }

        val count = minOf(a.events.size, b.events.size)
        for (i in 0 until count) {
            val ea = a.events[i]
            val eb = b.events[i]
            if (ea.direction != eb.direction) {
                differences.add("event $i: direction ${ea.direction} vs ${eb.direction}")
            }
            if (ea.category != eb.category) {
                differences.add("event $i: category ${ea.category} vs ${eb.category}")
            }
            val pa = ea.payload
            val pb = eb.payload
            if ((pa == null) != (pb == null)) {
                differences.add("event $i: payload presence differs")
            } else if (pa != null && pb != null) {
                if (pa.size != pb.size) {
                    differences.add("event $i: payload length ${pa.size} vs ${pb.size}")
                } else if (!pa.contentEquals(pb)) {
                    differences.add(
                        "event $i: payload bytes differ " +
                            "(no semantic meaning inferred)",
                    )
                }
            }
        }

        return TraceDiff(
            traceA = a.traceId,
            traceB = b.traceId,
            differences = differences,
            // A hypothesis is a separate, explicit record — never auto-created.
            hypotheses = emptyList(),
        )
    }
}

/** The result of comparing two traces. */
data class TraceDiff(
    val traceA: String,
    val traceB: String,
    val differences: List<String>,
    /**
     * Engineer-recorded hypotheses. Always empty from automated comparison;
     * a human adds these separately. Kept distinct from observations.
     */
    val hypotheses: List<DifferenceHypothesis>,
)

/** A human-recorded hypothesis about a difference. */
data class DifferenceHypothesis(
    val differenceIndex: Int,
    val hypothesis: String,
    /** One of: HYPOTHESIS, CONFIRMED, IMPLEMENTED, VERIFIED. */
    val status: String,
)
