package com.omnibuds.core.diagnostics

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The builder is the only way a Phase 1 snapshot gets filled, so these cases pin its
 * two disciplines: failures stay structured rather than flattening into warning prose,
 * and recording a fact twice updates it instead of duplicating or inventing it.
 */
class SnapshotBuilderTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")
    private val transparency: FeatureId = FeatureId.of("noise-control", "transparency")
    private val battery: FeatureId = FeatureId.of("power", "battery")

    private fun readFailure(operationId: String): OmniBudsError = OmniBudsError(
        category = OmniBudsErrorCategory.READ_FAILED,
        operationId = operationId,
        detail = "characteristic returned no value",
    )

    @Test
    fun errorsAndWarningsLandInSeparateLists() {
        val builder = SnapshotBuilder()
        builder.fail(readFailure("op-read-battery-1"))
        builder.warn(DiagnosticCategory.PERSISTENCE, "cached capability map was rebuilt", atEpochMillis = 2_000L)
        val snapshot = builder.build(capturedAtEpochMillis = 3_000L)

        assertEquals(1, snapshot.errors.size)
        assertEquals(1, snapshot.warnings.size)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, snapshot.errors.single().category)
        assertEquals("op-read-battery-1", snapshot.errors.single().operationId)
        assertEquals(DiagnosticSeverity.WARN, snapshot.warnings.single().severity)
        assertNull(snapshot.warnings.single().error)
    }

    @Test
    fun recordingTheSameFeatureTwiceKeepsTheLatestStateWithoutDuplicatingTheEntry() {
        val builder = SnapshotBuilder()
        builder.record(anc, CapabilityState.UNKNOWN)
        builder.record(anc, CapabilityState.SUPPORTED_VOLATILE)
        builder.record(anc, CapabilityState.SUPPORTED_PERSISTENT)
        val snapshot = builder.build(capturedAtEpochMillis = 4_000L)

        assertEquals(1, snapshot.capabilityStates.size)
        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, snapshot.stateOf(anc))
    }

    @Test
    fun aWeakerLaterReadingReplacesAnEarlierOptimisticOne() {
        val builder = SnapshotBuilder()
        builder.record(battery, CapabilityState.PERSISTENCE_VERIFIED)
        builder.record(battery, CapabilityState.UNKNOWN)
        val snapshot = builder.build(capturedAtEpochMillis = 4_000L)

        // Support moves down on failed discovery; the builder does not keep the nicer
        // earlier answer just because it was recorded first.
        assertEquals(CapabilityState.UNKNOWN, snapshot.stateOf(battery))
    }

    @Test
    fun anUnusedBuilderProducesASnapshotThatClaimsNothing() {
        val snapshot = SnapshotBuilder().build(capturedAtEpochMillis = 0L)

        assertTrue(snapshot.capabilityStates.isEmpty())
        assertTrue(snapshot.errors.isEmpty())
        assertTrue(snapshot.warnings.isEmpty())
        assertTrue(snapshot.platformNotes.isEmpty())
        assertNull(snapshot.audioTransportSummary)
        assertEquals(CapabilityState.UNKNOWN, snapshot.stateOf(anc))
    }

    @Test
    fun capabilityStatesAreOrderedByFeatureIdentityRatherThanByDiscoveryOrder() {
        val builder = SnapshotBuilder()
        builder.record(transparency, CapabilityState.READ_ONLY)
        builder.record(anc, CapabilityState.SUPPORTED_PERSISTENT)
        val snapshot = builder.build(capturedAtEpochMillis = 5_000L)

        assertEquals(
            listOf("noise-control.anc", "noise-control.transparency"),
            snapshot.capabilityStates.keys.map { feature -> feature.qualifiedName },
        )
    }

    @Test
    fun warningsAndNotesKeepTheOrderTheyHappenedIn() {
        val builder = SnapshotBuilder()
        builder.warn(DiagnosticCategory.TRANSPORT, "first degraded read", atEpochMillis = 100L, operationId = "op-1")
        builder.warn(DiagnosticCategory.PROTOCOL, "second degraded read", atEpochMillis = 200L, operationId = "op-2")
        builder.note("adapter enabled during capture")
        builder.note("session not saved")
        val snapshot = builder.build(capturedAtEpochMillis = 300L)

        assertEquals(
            listOf("first degraded read", "second degraded read"),
            snapshot.warnings.map { event -> event.message },
        )
        assertEquals(listOf("adapter enabled during capture", "session not saved"), snapshot.platformNotes)
    }

    @Test
    fun aBuiltSnapshotIsDetachedFromTheBuilderThatProducedIt() {
        val builder = SnapshotBuilder()
        builder.record(anc, CapabilityState.SUPPORTED_VOLATILE)
        val built = builder.build(capturedAtEpochMillis = 1_000L)

        builder.record(anc, CapabilityState.UNSUPPORTED)
        builder.fail(readFailure("op-write-anc-1"))
        builder.warn(DiagnosticCategory.CAPABILITY, "write path refused", atEpochMillis = 1_500L)

        assertEquals(0, built.errors.size)
        assertEquals(0, built.warnings.size)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, built.stateOf(anc))

        val later = builder.build(capturedAtEpochMillis = 2_000L)
        assertEquals(1, later.errors.size)
        assertEquals(1, later.warnings.size)
        assertEquals(CapabilityState.UNSUPPORTED, later.stateOf(anc))
    }

    @Test
    fun audioSummaryAndPlatformNotesArriveExactlyAsTheCallerWroteThem() {
        val builder = SnapshotBuilder()
        builder.describeAudioTransport("A2DP active, LDAC negotiated")
        builder.note("route reported by platform, not inferred")
        val snapshot = builder.build(capturedAtEpochMillis = 6_000L)

        assertEquals("A2DP active, LDAC negotiated", snapshot.audioTransportSummary)
        assertEquals(1, snapshot.platformNotes.size)
        assertTrue(snapshot.platformNotes.single().startsWith("route reported"))

        builder.describeAudioTransport(null)
        assertNull(builder.build(capturedAtEpochMillis = 7_000L).audioTransportSummary)
    }

    @Test
    fun aNegativeCaptureTimestampIsRejectedRatherThanSilentlyAccepted() {
        assertFailsWith<IllegalArgumentException> {
            SnapshotBuilder().build(capturedAtEpochMillis = -1L)
        }
    }
}
