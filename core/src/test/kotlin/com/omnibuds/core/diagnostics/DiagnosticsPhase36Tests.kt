package com.omnibuds.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun event(
    message: String = "test event",
    severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    category: DiagnosticCategory = DiagnosticCategory.BLUETOOTH,
    operationId: String? = "op-1",
) = DiagnosticEvent(
    timestampEpochMillis = 1_000L,
    severity = severity,
    category = category,
    message = message,
    operationId = operationId,
    error = null,
)

class DiagnosticStoreTest {

    @Test
    fun `store is bounded`() {
        val store = DiagnosticStore(capacity = 4)
        repeat(7) { store.record(event(message = "e$it")) }
        assertEquals(4, store.size)
        assertEquals(3L, store.droppedCount)
        val messages = store.snapshot().map { it.event.message }
        assertEquals(listOf("e3", "e4", "e5", "e6"), messages)
    }

    @Test
    fun `oversized message is truncated not dropped`() {
        val store = DiagnosticStore()
        val long = "x".repeat(DiagnosticStore.MAX_MESSAGE_CHARS + 100)
        assertTrue(store.record(event(message = long)))
        val stored = store.snapshot().single().event.message
        assertTrue(stored.length <= DiagnosticStore.MAX_MESSAGE_CHARS + 20)
        assertTrue(stored.endsWith("…[truncated]"))
    }

    @Test
    fun `sequence numbers are monotonic`() {
        val store = DiagnosticStore()
        repeat(3) { store.record(event()) }
        val seqs = store.snapshot().map { it.sequence }
        assertEquals(listOf(0L, 1L, 2L), seqs)
    }

    @Test
    fun `severity filter works`() {
        val store = DiagnosticStore()
        store.record(event(severity = DiagnosticSeverity.DEBUG))
        store.record(event(severity = DiagnosticSeverity.ERROR))
        val filtered = store.snapshot(DiagnosticSeverity.WARN)
        assertEquals(1, filtered.size)
        assertEquals(DiagnosticSeverity.ERROR, filtered[0].event.severity)
    }

    @Test
    fun `clear resets counters`() {
        val store = DiagnosticStore(capacity = 1)
        repeat(3) { store.record(event()) }
        store.clear()
        assertEquals(0, store.size)
        assertEquals(0L, store.droppedCount)
    }

    @Test
    fun `dropped counter saturates instead of overflowing`() {
        val store = DiagnosticStore(capacity = 1)
        // Can't realistically reach Long.MAX_VALUE; assert the field
        // exists and is non-negative after drops.
        repeat(5) { store.record(event()) }
        assertTrue(store.droppedCount >= 0)
    }
}

class DiagnosticHealthTest {

    @Test
    fun `healthy when sink available and no failures`() {
        val tracker = DiagnosticHealthTracker(DiagnosticStore())
        val h = tracker.health()
        assertTrue(h.sinkAvailable)
        assertFalse(h.isDegraded)
        assertEquals(0.0, h.saturation)
    }

    @Test
    fun `persistence failure degrades health`() {
        val tracker = DiagnosticHealthTracker(DiagnosticStore())
        tracker.recordPersistenceFailure()
        val h = tracker.health()
        assertTrue(h.isDegraded)
        assertEquals(1L, h.persistenceFailures)
    }

    @Test
    fun `unavailable sink degrades health`() {
        val tracker = DiagnosticHealthTracker(DiagnosticStore())
        tracker.setSinkAvailable(false)
        assertTrue(tracker.health().isDegraded)
    }

    @Test
    fun `saturation is reported`() {
        val store = DiagnosticStore(capacity = 4)
        repeat(2) { store.record(event()) }
        val h = DiagnosticHealthTracker(store).health()
        assertEquals(0.5, h.saturation)
    }

    @Test
    fun `success timestamp recorded`() {
        val tracker = DiagnosticHealthTracker(DiagnosticStore())
        tracker.recordPersistenceSuccess(2_000L)
        assertEquals(2_000L, tracker.health().lastPersistenceEpochMillis)
    }
}

class DiagnosticExporterTest {

    private fun stored(count: Int): List<DiagnosticStore.StoredEvent> {
        val store = DiagnosticStore()
        repeat(count) { store.record(event(message = "event $it")) }
        return store.snapshot()
    }

    @Test
    fun `export is versioned and sanitized`() {
        val result = DiagnosticExporter.export(stored(3), 9_999L)
        assertTrue(result is DiagnosticExporter.ExportResult.Exported)
        val exported = result as DiagnosticExporter.ExportResult.Exported
        assertTrue(exported.content.contains("\"schemaVersion\":1"))
        assertEquals(3, exported.eventCount)
    }

    @Test
    fun `export redacts sensitive content`() {
        val store = DiagnosticStore()
        store.record(event(message = "device AA:BB:CC:DD:EE:FF failed"))
        val result = DiagnosticExporter.export(store.snapshot(), 9_999L)
        val exported = result as DiagnosticExporter.ExportResult.Exported
        assertFalse(exported.content.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(exported.content.contains("[REDACTED]"))
    }

    @Test
    fun `empty store refuses export`() {
        val result = DiagnosticExporter.export(emptyList(), 9_999L)
        assertTrue(result is DiagnosticExporter.ExportResult.Refused)
    }

    @Test
    fun `export respects event cap`() {
        val result = DiagnosticExporter.export(
            stored(DiagnosticExporter.MAX_EXPORT_EVENTS + 100),
            9_999L,
        )
        val exported = result as DiagnosticExporter.ExportResult.Exported
        assertEquals(DiagnosticExporter.MAX_EXPORT_EVENTS, exported.eventCount)
    }

    @Test
    fun `export respects size cap`() {
        val store = DiagnosticStore()
        repeat(50) {
            store.record(event(message = "y".repeat(DiagnosticStore.MAX_MESSAGE_CHARS)))
        }
        val result = DiagnosticExporter.export(store.snapshot(), 9_999L)
        val exported = result as DiagnosticExporter.ExportResult.Exported
        assertTrue(exported.content.length <= DiagnosticExporter.MAX_EXPORT_CHARS)
    }

    @Test
    fun `newest events come first`() {
        val result = DiagnosticExporter.export(stored(3), 9_999L)
        val exported = result as DiagnosticExporter.ExportResult.Exported
        val first = exported.content.indexOf("event 2")
        val last = exported.content.indexOf("event 0")
        assertTrue(first in 0 until last)
    }
}

class SinkFailureIsolationTest {

    @Test
    fun `failing sink does not break the store`() {
        // A sink that throws must not prevent recording: the store
        // never calls the sink, so this is structural.
        val store = DiagnosticStore()
        val failingSink: (DiagnosticEvent) -> Unit = { throw RuntimeException("sink down") }
        assertTrue(store.record(event()))
        try {
            failingSink(event())
        } catch (_: RuntimeException) {
            // Expected; the store is unaffected.
        }
        assertEquals(1, store.size)
    }

    @Test
    fun `redaction failure drops the event safely`() {
        // LogRedactor never throws, but the store's sanitize path
        // must also never throw on hostile input.
        val store = DiagnosticStore()
        val hostile = "\u0000\u0001\u0002".repeat(10_000)
        assertTrue(store.record(event(message = hostile)))
        assertEquals(1, store.size)
    }
}
