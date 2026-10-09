package com.omnibuds.core.diagnostics

/**
 * Bounded in-memory diagnostic event store.
 *
 * Phase 36: fixed capacity with oldest-first eviction. Dropped events
 * are counted with bounded counters (no unbounded logging about
 * dropped events). A corrupt record can never make the store
 * unreadable — records are validated on entry.
 */
class DiagnosticStore(
    /** Maximum retained events. */
    val capacity: Int = DEFAULT_CAPACITY,
) {
    companion object {
        const val DEFAULT_CAPACITY = 512

        /**
         * Maximum serialized size of one event's message in characters.
         * Oversized messages are truncated, never rejected outright —
         * the event's category/severity/timestamp still matter.
         */
        const val MAX_MESSAGE_CHARS = 2_048
    }

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = ArrayDeque<StoredEvent>(capacity)

    /** Number of events dropped due to capacity. Bounded: saturates. */
    var droppedCount: Long = 0L
        private set

    /** A validated, stored event. */
    data class StoredEvent(
        val event: DiagnosticEvent,
        /** Monotonic sequence number; scoped to this store. */
        val sequence: Long,
    )

    private var nextSequence = 0L

    /**
     * Record an event. Returns false when the event was invalid and
     * dropped (counts toward [invalidCount], not [droppedCount]).
     */
    @Synchronized
    fun record(event: DiagnosticEvent): Boolean {
        val sanitized = sanitize(event) ?: run {
            if (invalidCount < Long.MAX_VALUE) invalidCount++
            return false
        }
        if (events.size >= capacity) {
            events.removeFirst()
            if (droppedCount < Long.MAX_VALUE) droppedCount++
        }
        events.addLast(StoredEvent(sanitized, nextSequence++))
        return true
    }

    /** Number of invalid events rejected. Bounded: saturates. */
    var invalidCount: Long = 0L
        private set

    /** Events in insertion order. */
    @Synchronized
    fun snapshot(): List<StoredEvent> = events.toList()

    /** Events at or above [severity], newest last. */
    @Synchronized
    fun snapshot(severity: DiagnosticSeverity): List<StoredEvent> =
        events.filter { it.event.severity >= severity }

    @Synchronized
    fun clear() {
        events.clear()
        droppedCount = 0L
        invalidCount = 0L
        nextSequence = 0L
    }

    val size: Int @Synchronized get() = events.size

    /**
     * Validate and bound one event. Returns null when invalid.
     * Truncates oversized messages rather than dropping the event.
     */
    private fun sanitize(event: DiagnosticEvent): DiagnosticEvent? {
        if (event.timestampEpochMillis < 0) return null
        if (event.message.isBlank()) return null
        val message = if (event.message.length > MAX_MESSAGE_CHARS) {
            event.message.take(MAX_MESSAGE_CHARS) + "…[truncated]"
        } else {
            event.message
        }
        return event.copy(message = message)
    }
}
