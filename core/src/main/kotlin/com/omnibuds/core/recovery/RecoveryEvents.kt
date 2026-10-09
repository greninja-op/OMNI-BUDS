package com.omnibuds.core.recovery

/**
 * Bounded, privacy-conscious recovery diagnostic events.
 *
 * Phase 34: no raw Bluetooth payloads, no secrets, no user media.
 * The sink is bounded — diagnostics must never become a failure source.
 */
enum class RecoveryEventType {
    FAILURE_CLASSIFIED,
    RECOVERY_POLICY_SELECTED,
    RETRY_SCHEDULED,
    RETRY_STARTED,
    RETRY_CANCELLED,
    RECOVERY_BUDGET_EXHAUSTED,
    SESSION_RECREATED,
    RECONCILIATION_STARTED,
    RECONCILIATION_COMPLETED,
    OPERATION_MARKED_UNKNOWN,
    RECOVERY_SUCCEEDED,
    RECOVERY_FAILED,
}

/** A single recovery diagnostic event. */
data class RecoveryEvent(
    val eventId: String,
    val correlationId: String,
    val type: RecoveryEventType,
    val deviceId: String?,
    val sessionId: String?,
    val operationId: String?,
    val attemptNumber: Int?,
    val elapsedMillis: Long?,
    val decision: RecoveryDecision?,
)

/**
 * Bounded in-memory event sink.
 *
 * Phase 34: fixed capacity with oldest-first eviction. A full sink
 * drops the oldest event — it never blocks recovery.
 */
class RecoveryEventSink(private val capacity: Int = 256) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = ArrayDeque<RecoveryEvent>(capacity)

    @Synchronized
    fun record(event: RecoveryEvent) {
        if (events.size >= capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<RecoveryEvent> = events.toList()

    @Synchronized
    fun clear() = events.clear()

    val size: Int @Synchronized get() = events.size
}
