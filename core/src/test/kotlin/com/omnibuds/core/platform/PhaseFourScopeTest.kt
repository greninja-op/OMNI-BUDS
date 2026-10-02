package com.omnibuds.core.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Phase 4 was allowed to reach, checked as data rather than remembered as prose.
 *
 * `PhaseTwoScopeTest` and `PhaseThreeScopeTest` establish the shape: a phase pins its own authorised
 * operation set, and the phase before it keeps its guard intact. Phase 4's answer is a shorter list
 * than Phase 3's, and that is the finding - a session engine needs no new reach into the phone, so
 * nothing here authorises anything. The second half matters more than the first: Phase 4's engine
 * consumes the *output* of observation and must not be able to touch its *control*. `refresh()`
 * has no owner (RISK-058), and the tempting fix is to hand the session layer the observer. This file
 * is the reason that temptation has to be argued rather than typed (ADR-P4-011).
 */
class PhaseFourScopeTest {

    @Test
    fun nothingNewIsAuthorisedAtPhaseFour() {
        val atThree = BluetoothOperation.entries.filter { operation -> operation.isAuthorizedIn(PHASE_THREE) }.toSet()
        val atFour = BluetoothOperation.entries.filter { operation -> operation.isAuthorizedIn(PHASE_FOUR) }.toSet()

        assertEquals(atThree, atFour, "Phase 4 authorised a Bluetooth operation, and no requirement asked for one")
    }

    @Test
    fun theSessionLayerReachesNoControlSeam() {
        // The engine reads ConnectedDeviceSnapshot - a value - and nothing that could make the phone
        // do something. These are the control types in the platform area, by name.
        val forbidden = listOf(
            "ConnectedDeviceSource",
            "ConnectedDeviceObserver",
            "AdapterStateSource",
            "BluetoothPlatform",
            "BluetoothOperation",
            "PlatformRegistration",
            "ConnectedDeviceEventChannel",
            "PermissionContext",
        )
        val violations = sessionMainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> forbidden.any { name -> line.contains("com.omnibuds.core.platform.$name") } }
                .map { line -> "${file.name}: reaches ${line.take(80)}" }
        }

        assertEquals(emptyList(), violations, "the session layer reached an observation control seam")
    }

    @Test
    fun theSessionLayerStillImportsNoPersistenceContract() {
        // Prompt section 17 and ADR-P4-010: a session is not a saved device. The layer map forbids the
        // import structurally (session and persistence are both layer 3); this pins that the rule was
        // not quietly widened to let one convenience call through.
        val violations = sessionMainSources().flatMap { file ->
            file.readLines()
                .filter { line -> line.startsWith("import com.omnibuds.core.persistence.") }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), violations, "a session reached the saved-device store")
    }

    @Test
    fun theSessionLayerNamesNothingThatImpliesAControlSession() {
        // A textual guard, deliberately blunt: the words that would make a transport, a protocol or a
        // capability claim legible in this area. `CONTROL_SESSION` is a ConnectionState member and is
        // named in a KDoc explaining why it is unreachable, so the scan reads code lines only.
        val forbidden = listOf("TRANSPORT_RFCOMM_OPEN", "TRANSPORT_GATT_OPEN", "connectGatt", "createRfcommSocket")
        val violations = sessionMainSources().flatMap { file ->
            file.readLines()
                .map { line -> line.trim() }
                .filter { line -> line.isNotEmpty() && !line.startsWith("*") && !line.startsWith("//") }
                .filter { line -> forbidden.any { token -> line.contains(token) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), violations, "a control-protocol name appeared in the session layer")
    }

    @Test
    fun phaseFourAddedNoNewSourceArea() {
        val areas = sessionMainSources()
            .map { file -> file.invariantSeparatorsPath.substringAfter("omnibuds/core/").substringBefore("/") }
            .distinct()

        assertEquals(listOf("session"), areas, "Phase 4 wrote session code outside the session area")
    }

    @Test
    fun thePhaseThatFollowsIsStillNotAuthorisedToReachAnythingThisOneInvented() {
        // Phase 5 is fingerprinting and identification. It may read what Phase 4 built; it may not
        // reach the phone further than Phase 4 could, and nothing here quietly raised a scan tag.
        assertFalse(BluetoothOperation.DEVICE_DISCOVERY_SCAN.isAuthorizedIn(PHASE_FOUR))
        assertFalse(BluetoothOperation.TRANSPORT_GATT_OPEN.isAuthorizedIn(PHASE_FOUR))
        assertFalse(BluetoothOperation.LE_AUDIO_SESSION_INSPECTION.isAuthorizedIn(PHASE_FOUR))
        assertTrue(BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION.isAuthorizedIn(PHASE_FOUR))
    }

    /** Comment lines and KDoc stripped, so documenting a forbidden thing is not a violation. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private fun sessionMainSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/session")
        assertTrue(root.isDirectory, "the session area moved or vanished: ${root.invariantSeparatorsPath}")
        val files = root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
        // A scan that finds nothing passes by accident, which is the failure mode every guard in this
        // project refuses.
        assertTrue(files.isNotEmpty(), "no session sources found; the check would be vacuous")
        return files
    }

    companion object {
        private const val PHASE_THREE = 3
        private const val PHASE_FOUR = 4
    }
}
