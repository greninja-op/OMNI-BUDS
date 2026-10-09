package com.omnibuds.core.access

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.diagnostics.DiagnosticEvent
import com.omnibuds.core.diagnostics.DiagnosticSeverity
import com.omnibuds.core.diagnostics.OmniBudsLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 21: diagnostics tests.
 */
class DiagnosticsTest {

    private class RecordingLogger : OmniBudsLogger {
        val events = mutableListOf<DiagnosticEvent>()
        override fun isEnabled(severity: DiagnosticSeverity) = true
        override fun log(event: DiagnosticEvent) { events.add(event) }
    }

    @Test
    fun `classification events logged`() {
        val logger = RecordingLogger()
        val diagnostics = AccessDiagnostics(logger)
        diagnostics.classification("key-1", DeviceAccessState.unknown())
        assertEquals(1, logger.events.size)
        assertTrue(logger.events[0].message.contains("UNKNOWN_DEVICE"))
    }

    @Test
    fun `denied operations logged with reason`() {
        val logger = RecordingLogger()
        val diagnostics = AccessDiagnostics(logger)
        val decision = DeviceAccessPolicy.evaluate(
            DeviceAccessState.unknown(),
            OperationCategory.HARDWARE_STATE_WRITE,
            capabilityId = "anc",
        )
        diagnostics.denied("key-1", decision)
        assertEquals(1, logger.events.size)
        assertTrue(logger.events[0].message.contains("UNKNOWN_DEVICE_WRITE_DENIED"))
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, logger.events[0].error?.category)
    }

    @Test
    fun `no sensitive data in messages`() {
        val logger = RecordingLogger()
        val diagnostics = AccessDiagnostics(logger)
        diagnostics.classification("key-1", DeviceAccessState.unknown())
        diagnostics.denied("key-1", DeviceAccessPolicy.evaluate(
            DeviceAccessState.unknown(), OperationCategory.RAW_TRANSPORT_WRITE,
        ))
        val macPattern = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
        for (event in logger.events) {
            assertFalse(macPattern.containsMatchIn(event.message),
                "no address content: ${event.message}")
            assertFalse(event.message.contains("password", ignoreCase = true))
            assertFalse(event.message.contains("token", ignoreCase = true))
        }
    }

    @Test
    fun `bounded event emission`() {
        val logger = RecordingLogger()
        val diagnostics = AccessDiagnostics(logger, maxEvents = 5)
        repeat(10) { diagnostics.classification("key-$it", DeviceAccessState.unknown()) }
        assertEquals(5, logger.events.size)
    }
}
