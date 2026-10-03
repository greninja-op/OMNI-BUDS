package com.omnibuds.core.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Phase 5 was allowed to reach, checked as data.
 *
 * `PhaseTwo/Three/FourScopeTest` establish the shape. Phase 5's answer mirrors Phase 4's: a
 * fingerprinting and identification engine needs no new reach into the phone, so nothing here
 * authorises anything - and the specific thing it must NOT authorise is the scan it used to be
 * tagged against. The enum once carried `DEVICE_DISCOVERY_SCAN authorizedInPhase = 5`, which would
 * have asserted that the phase whose matcher never opens a scanner (ADR-P5-007 keeps MANUFACTURER_DATA
 * at `UNAVAILABLE` for exactly that reason) had authorised one; the tag moved to 6 and the first
 * assertion here is the guard that it does not creep back.
 */
class PhaseFiveScopeTest {

    @Test
    fun nothingNewIsAuthorisedAtPhaseFive() {
        val atFour = BluetoothOperation.entries.filter { operation -> operation.isAuthorizedIn(PHASE_FOUR) }.toSet()
        val atFive = BluetoothOperation.entries.filter { operation -> operation.isAuthorizedIn(PHASE_FIVE) }.toSet()

        assertEquals(atFour, atFive, "Phase 5 authorised a Bluetooth operation, and its engine reads no radio")
        assertFalse(
            BluetoothOperation.DEVICE_DISCOVERY_SCAN.isAuthorizedIn(PHASE_FIVE),
            "the scan tag crept back to Phase 5; advertisement data is a deferred-device concern",
        )
    }

    @Test
    fun theIdentificationSourcesReachNoControlTransportOrPersistenceSeam() {
        // Identification is a pure computation over already-observed values. The types it must never
        // name: the observation control seams, a transport it would open, the saved-device store, and
        // the protocol layer whose selection prompt section 5 keeps ahead of this phase.
        val forbidden = listOf(
            "ConnectedDeviceSource",
            "ConnectedDeviceObserver",
            "BluetoothOperation",
            "com.omnibuds.core.transport",
            "com.omnibuds.core.persistence",
            "com.omnibuds.core.protocol",
            "connectGatt",
            "startDiscovery",
            "BluetoothAdapter",
        )
        val violations = phaseFiveIdentitySources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> forbidden.any { token -> line.contains(token) } }
                .map { line -> "${file.name}: ${line.take(90)}" }
        }

        assertEquals(emptyList(), violations, "Phase 5 identification reached a seam it was not authorised to touch")
    }

    @Test
    fun identitySignalTypesDeclareNoAddressBearingMember() {
        // SEC-ID-003: identity comes from evidence, never from a MAC. This scans only Phase 5's own
        // identity sources, and only for a member that could *hold* one (a `val ...Address...` or an
        // address kind) - not for the word, which DeviceFingerprint uses to fold the separator out.
        val patterns = listOf("Address", "address")
        val offenders = phaseFiveIdentitySources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> line.contains("val ") && patterns.any { token -> line.contains(token) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "an address-bearing member appeared in the identity types")
    }

    @Test
    fun phaseFiveAddedNoNewSourceArea() {
        val areas = identificationMainSources()
            .map { file -> file.invariantSeparatorsPath.substringAfter("omnibuds/core/").substringBefore("/") }
            .distinct()

        assertEquals(listOf("device"), areas, "Phase 5 wrote identification code outside the device area (ADR-P5-001)")
    }

    /** Comment lines and KDoc stripped, so documenting a forbidden thing is not doing it. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private fun identificationMainSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/device")
        assertTrue(root.isDirectory, "the device area moved or vanished: ${root.invariantSeparatorsPath}")
        val files = root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "no device sources found; the check would be vacuous")
        return files
    }

    /** The six files Phase 5 added, by name, so the scan reads exactly what this phase wrote. */
    private fun phaseFiveIdentitySources(): List<File> {
        val names = setOf(
            "IdentitySignal.kt",
            "IdentityNormalizer.kt",
            "IdentificationConfidence.kt",
            "DeviceIdentityRegistry.kt",
            "IdentificationResult.kt",
            "IdentityEngine.kt",
        )
        val found = identificationMainSources().filter { file -> file.name in names }
        assertEquals(
            names.size,
            found.size,
            "a Phase 5 identity source is missing: ${names - found.map { file -> file.name }.toSet()}",
        )
        return found
    }

    companion object {
        private const val PHASE_FOUR = 4
        private const val PHASE_FIVE = 5
    }
}
