package com.omnibuds.core.lab

/**
 * Request/response correlation.
 *
 * Phase 20 (OB-P20-REQ-013): pairs only on evidence; preserves ambiguity.
 * Never invents a missing response.
 */
object CorrelationEngine {

    /**
     * Correlate events in a trace.
     * @return pairs and unmatched events.
     */
    fun correlate(trace: ProtocolTrace): CorrelationResult {
        val pairs = mutableListOf<CorrelatedPair>()
        val unmatchedRequests = mutableListOf<TraceEvent>()
        val unmatchedResponses = mutableListOf<TraceEvent>()

        // Index responses by correlation ID.
        val byCorrelation = trace.events
            .filter { it.correlationId != null }
            .groupBy { it.correlationId }

        val consumed = mutableSetOf<String>()
        for (event in trace.events) {
            if (event.direction != TraceDirection.HOST_TO_DEVICE) continue
            val cid = event.correlationId
            if (cid == null) {
                unmatchedRequests.add(event)
                continue
            }
            val candidates = (byCorrelation[cid] ?: emptyList())
                .filter { it.direction == TraceDirection.DEVICE_TO_HOST }
                .filter { it.eventId !in consumed }
            when {
                candidates.isEmpty() -> unmatchedRequests.add(event)
                candidates.size == 1 -> {
                    val response = candidates.first()
                    consumed.add(response.eventId)
                    pairs.add(
                        CorrelatedPair(
                            request = event,
                            response = response,
                            confidence = CorrelationConfidence.EXPLICIT_ID,
                        ),
                    )
                }
                else -> {
                    // Ambiguous: multiple responses for one ID.
                    unmatchedRequests.add(event)
                    // Mark all as ambiguous (preserved, not paired).
                    unmatchedResponses.addAll(candidates)
                    consumed.addAll(candidates.map { it.eventId })
                }
            }
        }

        // Responses never consumed and not device-to-host with a CID are unmatched.
        for (event in trace.events) {
            if (event.direction == TraceDirection.DEVICE_TO_HOST &&
                event.eventId !in consumed &&
                event !in unmatchedResponses
            ) {
                unmatchedResponses.add(event)
            }
        }

        return CorrelationResult(pairs, unmatchedRequests, unmatchedResponses)
    }
}

/** How confident a pairing is. */
enum class CorrelationConfidence {
    /** Matched on explicit correlation/sequence ID. */
    EXPLICIT_ID,

    /** Matched on ordering/timing only — weak. */
    ORDERING_ONLY,
}

/** One request/response pair. */
data class CorrelatedPair(
    val request: TraceEvent,
    val response: TraceEvent,
    val confidence: CorrelationConfidence,
)

/** The full correlation result. */
data class CorrelationResult(
    val pairs: List<CorrelatedPair>,
    val unmatchedRequests: List<TraceEvent>,
    val unmatchedResponses: List<TraceEvent>,
)
