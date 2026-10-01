package com.omnibuds.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins Phase 1 execution prompt section 41: DEBUG/INFO/WARN/ERROR are the minimal
 * logging foundation, and TRACE plus PACKET "must be opt-in and privacy-aware".
 *
 * The gate lives in the type so that a sink cannot discover, by reading the enum, that
 * a verbose level looked harmless.
 */
class DiagnosticSeverityTest {

    @Test
    fun traceAndPacketRequireAnOptIn() {
        assertTrue(DiagnosticSeverity.TRACE.requiresOptIn, "TRACE is reserved for opt-in use")
        assertTrue(DiagnosticSeverity.PACKET.requiresOptIn, "PACKET is reserved for opt-in use")
    }

    @Test
    fun infoWarnErrorAndDebugDoNotRequireAnOptIn() {
        val foundationLevels = setOf(
            DiagnosticSeverity.DEBUG,
            DiagnosticSeverity.INFO,
            DiagnosticSeverity.WARN,
            DiagnosticSeverity.ERROR,
        )

        for (severity in foundationLevels) {
            assertFalse(severity.requiresOptIn, "$severity belongs to the always-available foundation set")
        }
    }

    @Test
    fun onlyTheTwoReservedLevelsAreOptInGated() {
        val gated = DiagnosticSeverity.entries.filter { it.requiresOptIn }.toSet()

        assertEquals(setOf(DiagnosticSeverity.TRACE, DiagnosticSeverity.PACKET), gated)
    }

    @Test
    fun theSeverityVocabularyIsExactlyTheSixDocumentedLevels() {
        assertEquals(6, DiagnosticSeverity.entries.size)
        assertEquals(
            setOf(
                DiagnosticSeverity.TRACE,
                DiagnosticSeverity.DEBUG,
                DiagnosticSeverity.INFO,
                DiagnosticSeverity.WARN,
                DiagnosticSeverity.ERROR,
                DiagnosticSeverity.PACKET,
            ),
            DiagnosticSeverity.entries.toSet(),
        )
    }

    @Test
    fun packetLevelIsNamedButNoCaptureIsImplementedInThisPhase() {
        // The level exists so that a later phase can gate it; recording that the name is
        // present without any capture machinery keeps section 51 (no premature
        // implementation) honest about what Phase 1 actually ships.
        assertEquals("PACKET", DiagnosticSeverity.PACKET.name)
        assertTrue(DiagnosticSeverity.PACKET.requiresOptIn)
    }
}
