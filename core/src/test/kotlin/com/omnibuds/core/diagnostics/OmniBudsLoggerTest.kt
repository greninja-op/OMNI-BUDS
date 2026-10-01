package com.omnibuds.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the `OmniBudsLogger` seam.
 *
 * The [RecordingLogger] here is a test double and deliberately lives in test source:
 * Phase 1 execution prompt sections 26 and 27 forbid a fake sink being compiled into
 * main, because a fake that looks like an implementation is how a fake capability
 * eventually ships. A real Logcat-backed logger belongs to `:platform:android` in a
 * later phase.
 */
class OmniBudsLoggerTest {

    private fun event(severity: DiagnosticSeverity, message: String): DiagnosticEvent = DiagnosticEvent(
        timestampEpochMillis = 1_000L,
        severity = severity,
        category = DiagnosticCategory.TRANSPORT,
        message = message,
        operationId = "op-read-1",
        error = null,
    )

    @Test
    fun theSeamIsImplementableWithNoPlatformTypeAndNoFrameworkDependency() {
        val logger = RecordingLogger(allowed = setOf(DiagnosticSeverity.INFO, DiagnosticSeverity.WARN))

        logger.log(event(DiagnosticSeverity.INFO, "control session opened"))
        logger.log(event(DiagnosticSeverity.WARN, "capability read was partial"))

        assertEquals(2, logger.recorded.size)
        assertTrue(logger.isEnabled(DiagnosticSeverity.INFO))
    }

    @Test
    fun aSinkCanRefuseTheOptInLevelsThroughIsEnabledAlone() {
        val logger = RecordingLogger(allowed = setOf(DiagnosticSeverity.INFO))

        assertFalse(logger.isEnabled(DiagnosticSeverity.TRACE))
        assertFalse(logger.isEnabled(DiagnosticSeverity.PACKET))

        // The two levels that name a privacy cost are the two that carry the gate.
        assertTrue(DiagnosticSeverity.TRACE.requiresOptIn)
        assertTrue(DiagnosticSeverity.PACKET.requiresOptIn)
    }

    @Test
    fun theSeamCarriesAnEventUnchangedRatherThanReshapingIt() {
        val logger = RecordingLogger(allowed = DiagnosticSeverity.entries.toSet())
        val original = event(DiagnosticSeverity.ERROR, "write refused by device")

        logger.log(original)

        assertEquals(original, logger.recorded.single())
        assertEquals("write refused by device", logger.recorded.single().message)
    }

    /** Test-only sink: keeps events in memory, persists nothing anywhere. */
    private class RecordingLogger(private val allowed: Set<DiagnosticSeverity>) : OmniBudsLogger {
        val recorded: MutableList<DiagnosticEvent> = mutableListOf()

        override fun isEnabled(severity: DiagnosticSeverity): Boolean = severity in allowed

        override fun log(event: DiagnosticEvent) {
            recorded += event
        }
    }
}
