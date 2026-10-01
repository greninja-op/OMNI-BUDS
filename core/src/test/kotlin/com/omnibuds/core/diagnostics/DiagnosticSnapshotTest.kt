package com.omnibuds.core.diagnostics

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins master context section 31 at the Phase 1 depth: a snapshot reports what was
 * established and infers nothing.
 *
 * The load-bearing cases are the ones that catch an over-eager reporting layer: a
 * feature nobody discovered must read as UNKNOWN (never UNSUPPORTED), the same input
 * must assemble the same snapshot, and message text must survive untouched because
 * redaction belongs to the emission boundary, not to this structure.
 */
class DiagnosticSnapshotTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")
    private val equalizer: FeatureId = FeatureId.of("audio", "equalizer")
    private val battery: FeatureId = FeatureId.of("power", "battery")

    @Test
    fun anUnrecordedFeatureHasNoEntryAndIsNotSynthesisedAsUnsupported() {
        val builder = SnapshotBuilder()
        builder.record(equalizer, CapabilityState.READ_ONLY)
        val snapshot = builder.build(capturedAtEpochMillis = 5_000L)

        assertFalse(snapshot.capabilityStates.containsKey(anc))
        assertNull(snapshot.capabilityStates[anc])
        assertEquals(CapabilityState.UNKNOWN, snapshot.stateOf(anc))
        assertNotEquals(CapabilityState.UNSUPPORTED, snapshot.stateOf(anc))
    }

    @Test
    fun aRecordedStateIsReportedExactlyAsRecorded() {
        val builder = SnapshotBuilder()
        builder.record(anc, CapabilityState.UNSUPPORTED)
        builder.record(battery, CapabilityState.UNKNOWN)
        val snapshot = builder.build(capturedAtEpochMillis = 5_000L)

        // A positively established absence stays UNSUPPORTED, and a not-yet-discovered
        // feature stays UNKNOWN. Neither is promoted, demoted or merged with the other.
        assertEquals(CapabilityState.UNSUPPORTED, snapshot.stateOf(anc))
        assertEquals(CapabilityState.UNKNOWN, snapshot.stateOf(battery))
        assertTrue(snapshot.capabilityStates.containsKey(battery))
    }

    @Test
    fun aSnapshotAssembledTwiceFromIdenticalInputIsIdentical() {
        val first = assembledBuilder().build(capturedAtEpochMillis = 9_000L)
        val second = assembledBuilder().build(capturedAtEpochMillis = 9_000L)

        assertEquals(first, second)
        assertEquals(
            listOf("audio.equalizer", "input.gesture-double-tap-left", "noise-control.anc"),
            first.capabilityStates.keys.map { feature -> feature.qualifiedName },
        )
        assertEquals(
            first.capabilityStates.keys.map { feature -> feature.qualifiedName },
            second.capabilityStates.keys.map { feature -> feature.qualifiedName },
        )
    }

    @Test
    fun anEventMessageIsPassedThroughUntouchedBecauseRedactionIsOwnedElsewhere() {
        val message = "capability read returned three of five expected characteristics"
        val builder = SnapshotBuilder()
        builder.warn(DiagnosticCategory.CAPABILITY, message, atEpochMillis = 7_000L, operationId = "op-discover-1")
        val snapshot = builder.build(capturedAtEpochMillis = 8_000L)

        val warning = snapshot.warnings.single()
        assertEquals(message, warning.message)
        assertEquals(DiagnosticSeverity.WARN, warning.severity)
        assertEquals(DiagnosticCategory.CAPABILITY, warning.category)
        assertEquals("op-discover-1", warning.operationId)
    }

    @Test
    fun aSnapshotCarriesNoDeviceIdentifierFieldAtAll() {
        // There is nothing here to fill in with an address or a device name (SEC-ID-001,
        // SEC-LOG-002): the report shape has no such property, so the only route for an
        // identifier into a report is deliberate text in a message, which the emission
        // boundary is responsible for.
        val snapshot = assembledBuilder().build(capturedAtEpochMillis = 9_000L)

        assertNull(snapshot.audioTransportSummary)
        assertTrue(snapshot.errors.isEmpty())
        assertEquals(1, snapshot.warnings.size)
        assertEquals(2, snapshot.platformNotes.size)
    }

    private fun assembledBuilder(): SnapshotBuilder {
        val builder = SnapshotBuilder()
        // Deliberately out of alphabetical order: the snapshot must not inherit the
        // order that discovery happened to produce.
        builder.record(anc, CapabilityState.SUPPORTED_PERSISTENT)
        builder.record(
            FeatureId.of("input", "gesture-double-tap-left"),
            CapabilityState.READ_ONLY,
        )
        builder.record(equalizer, CapabilityState.UNKNOWN)
        builder.warn(DiagnosticCategory.CAPABILITY, "gesture map incomplete", atEpochMillis = 6_000L)
        builder.note("adapter state read once at capture")
        builder.note("no saved device record for this session")
        return builder
    }
}
