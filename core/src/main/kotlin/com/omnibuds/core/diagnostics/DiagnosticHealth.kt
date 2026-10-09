package com.omnibuds.core.diagnostics

/**
 * Self-observability for the diagnostic subsystem.
 *
 * Phase 36: reports actual degradation. Never reports healthy when
 * persistence is failing. Never recursively generates events about
 * its own health — health is a passive snapshot, not an event source.
 */
data class DiagnosticHealth(
    /** True when at least one retaining sink is accepting events. */
    val sinkAvailable: Boolean,
    /** Fraction of store capacity in use, [0, 1]. */
    val saturation: Double,
    /** Events dropped due to capacity. */
    val droppedEvents: Long,
    /** Invalid events rejected. */
    val invalidEvents: Long,
    /** Persistence failures observed. */
    val persistenceFailures: Long,
    /** Last successful persistence epoch millis, or null. */
    val lastPersistenceEpochMillis: Long?,
) {
    init {
        require(saturation in 0.0..1.0) { "saturation must be in [0,1]" }
        require(droppedEvents >= 0) { "droppedEvents must be >= 0" }
        require(invalidEvents >= 0) { "invalidEvents must be >= 0" }
        require(persistenceFailures >= 0) { "persistenceFailures must be >= 0" }
    }

    /** True when diagnostics are degraded but the app is fine. */
    val isDegraded: Boolean
        get() = !sinkAvailable || persistenceFailures > 0 || saturation >= 1.0
}

/**
 * Tracks diagnostic health from a store and sink observations.
 */
class DiagnosticHealthTracker(
    private val store: DiagnosticStore,
) {
    private var persistenceFailures = 0L
    private var lastPersistenceEpochMillis: Long? = null
    private var sinkAvailable = true

    @Synchronized
    fun recordPersistenceSuccess(epochMillis: Long) {
        lastPersistenceEpochMillis = epochMillis
    }

    @Synchronized
    fun recordPersistenceFailure() {
        if (persistenceFailures < Long.MAX_VALUE) persistenceFailures++
    }

    @Synchronized
    fun setSinkAvailable(available: Boolean) {
        sinkAvailable = available
    }

    @Synchronized
    fun health(): DiagnosticHealth = DiagnosticHealth(
        sinkAvailable = sinkAvailable,
        saturation = store.size.toDouble() / store.capacity,
        droppedEvents = store.droppedCount,
        invalidEvents = store.invalidCount,
        persistenceFailures = persistenceFailures,
        lastPersistenceEpochMillis = lastPersistenceEpochMillis,
    )
}
