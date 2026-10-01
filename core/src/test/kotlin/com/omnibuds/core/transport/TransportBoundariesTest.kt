package com.omnibuds.core.transport

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.state.atLeast
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The invariants of the Phase 2 transport boundaries, attacked rather than admired.
 *
 * Phase 2 authorises transport *boundaries* only (Phase 2 prompt section 5.6) and forbids GATT
 * service discovery, characteristic writes, RFCOMM protocol communication and any vendor protocol
 * (section 6). Nothing here opens, probes or simulates a channel: these tests check that a value
 * describing a channel cannot claim more than the evidence behind it, which is the machine-checkable
 * half of ADR-P0-001 and PROTO-RESEARCH-003.
 *
 * Tier T1 — unit, no device, no radio. Every fixture is fictional: a category is recorded because a
 * test needed a refusal, not because anything was observed.
 *
 * Traceability: names follow `docs/phases/phase-0/specs.md` section 1.4 (behavior-plus-condition) and
 * carry no `TEST-P2-<NNN>` id, because the Phase 2 test plan issues those ids separately.
 */
class TransportBoundariesTest {

    /**
     * The five Bluetooth sub-interfaces, typed as [BluetoothTransport].
     *
     * The list's declared type is the proof: it compiles only if every element is a
     * `BluetoothTransport`. That is the subtyping assertion made without reflection artifacts —
     * `kotlin-reflect` is not a dependency of `:core`, and ADR-P1-021's rule is that a dependency
     * needs a stated reason — and without a fake implementation of the contract (ADR-P1-013).
     */
    private val subInterfaces: List<KClass<out BluetoothTransport>> = listOf(
        BleTransport::class,
        ClassicTransport::class,
        GattTransport::class,
        LeAudioTransport::class,
        RfcommTransport::class,
    )

    @Test
    fun aBoundaryCannotClaimAnAvailableChannelOnInferenceOrCodeExistenceAlone() {
        // The two tiers that a discovered UUID or a written interface would reach. Neither may
        // produce "available": PROTO-VERIFY-001 forbids reporting above the evidence.
        for (weak in listOf(VerificationLevel.INFERRED, VerificationLevel.IMPLEMENTED)) {
            assertFailsWith<IllegalArgumentException>("availability claimed on $weak") {
                TransportBoundary(
                    kind = TransportKind.GATT,
                    availability = TransportAvailability.available(TransportKind.GATT),
                    supportEvidence = weak,
                    notes = null,
                )
            }
        }
    }

    @Test
    fun aBoundaryWithEvidenceBehindItsAvailabilityConstructs() {
        val boundary = TransportBoundary(
            kind = TransportKind.RFCOMM,
            availability = TransportAvailability.available(TransportKind.RFCOMM),
            supportEvidence = VerificationLevel.LAB_TESTED,
            notes = "constructed by a unit test, never observed on hardware",
        )

        assertTrue(boundary.availability.available)
        assertTrue(boundary.supportEvidence.atLeast(VerificationLevel.LAB_TESTED))
        assertEquals(TransportKind.RFCOMM, boundary.availability.kind)
        assertNull(boundary.availability.reason)
    }

    @Test
    fun oneBoundaryRowCannotDescribeTwoDifferentChannels() {
        // A row whose kind disagrees with its availability record is how one transport stands in for
        // another (PROTO-XPORT-005).
        assertFailsWith<IllegalArgumentException> {
            TransportBoundary(
                kind = TransportKind.GATT,
                availability = TransportAvailability.available(TransportKind.RFCOMM),
                supportEvidence = VerificationLevel.HARDWARE_VERIFIED,
                notes = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TransportBoundary(
                kind = TransportKind.LE_AUDIO,
                availability = TransportAvailability.unavailable(
                    TransportKind.CLASSIC_BLUETOOTH,
                    OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                ),
                supportEvidence = VerificationLevel.INFERRED,
                notes = null,
            )
        }
    }

    @Test
    fun aRefusedCandidateIsLegalAtEveryEvidenceTier() {
        // Refusal is the Phase 2 answer, and its own strength is a separate fact: an absence is not
        // proof of absence (PROTO-ERR-004).
        for (level in VerificationLevel.entries) {
            val refused = TransportBoundary.refused(
                kind = TransportKind.RFCOMM,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                supportEvidence = level,
            )
            assertFalse(refused.availability.available)
            assertTrue(refused.availability.isRefused)
            assertEquals(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, refused.availability.reason)
            assertEquals(level, refused.supportEvidence)
        }
    }

    @Test
    fun aBlankNoteIsRejectedWhileAnAbsentNoteIsARealState() {
        assertFailsWith<IllegalArgumentException> {
            TransportBoundary.refused(
                kind = TransportKind.GATT,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                notes = "   ",
            )
        }
        assertNull(
            TransportBoundary.refused(
                kind = TransportKind.GATT,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            ).notes,
        )
    }

    @Test
    fun negotiationRefusesASelectionThatWasNeverOffered() {
        val candidates = listOf(
            TransportBoundary.refused(
                kind = TransportKind.GATT,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            ),
        )

        // RFCOMM was not offered at all; selecting it would be choosing a channel for a device that
        // never presented one (PROTO-XPORT-002's "explicit open attempt required", read as: never
        // invent the attempt).
        assertFailsWith<IllegalArgumentException> {
            TransportNegotiation(candidates = candidates, selected = TransportKind.RFCOMM)
        }
    }

    @Test
    fun negotiationRefusesASelectionItAlsoRecordsAsUnavailable() {
        val refusedGatt = TransportBoundary.refused(
            kind = TransportKind.GATT,
            reason = OmniBudsErrorCategory.GATT_FAILURE,
        )

        // The row is present, so a weaker invariant would pass here. Selecting a channel the same
        // record calls unavailable is a command addressed to a channel nobody established.
        assertFailsWith<IllegalArgumentException> {
            TransportNegotiation(candidates = listOf(refusedGatt), selected = TransportKind.GATT)
        }
    }

    @Test
    fun preferringUsesTheCallersOrderAndNeverItsOwn() {
        val rfcomm = TransportBoundary.established(
            kind = TransportKind.RFCOMM,
            supportEvidence = VerificationLevel.LAB_TESTED,
        )
        val gatt = TransportBoundary.established(
            kind = TransportKind.GATT,
            supportEvidence = VerificationLevel.LAB_TESTED,
        )
        val negotiation = TransportNegotiation(candidates = listOf(rfcomm, gatt), selected = null)

        // The two orders answer differently, which is only possible because the type holds no
        // preference of its own.
        assertEquals(TransportKind.RFCOMM, negotiation.preferring(listOf(TransportKind.RFCOMM))?.kind)
        assertEquals(
            TransportKind.RFCOMM,
            negotiation.preferring(listOf(TransportKind.RFCOMM, TransportKind.GATT))?.kind,
        )
        assertEquals(
            TransportKind.GATT,
            negotiation.preferring(listOf(TransportKind.GATT, TransportKind.RFCOMM))?.kind,
        )
        assertNull(negotiation.preferring(emptyList()))
    }

    @Test
    fun preferringSkipsARefusedCandidateWithoutFallingThroughToOneThatWasNotAskedFor() {
        val candidates = listOf(
            TransportBoundary.refused(
                kind = TransportKind.GATT,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            ),
            TransportBoundary.established(
                kind = TransportKind.RFCOMM,
                supportEvidence = VerificationLevel.LAB_TESTED,
            ),
            TransportBoundary.established(
                kind = TransportKind.CLASSIC_BLUETOOTH,
                supportEvidence = VerificationLevel.LAB_TESTED,
            ),
        )
        val negotiation = TransportNegotiation(candidates = candidates, selected = null)

        // GATT is refused; asking only for GATT must not silently deliver the RFCOMM row (PROTO-XPORT-007).
        assertNull(negotiation.preferring(listOf(TransportKind.GATT)))
        // Naming the next candidate explicitly is the only way to reach it.
        assertEquals(
            TransportKind.RFCOMM,
            requireNotNull(negotiation.preferring(listOf(TransportKind.GATT, TransportKind.RFCOMM))).kind,
        )
        // LE Audio was never offered, and an unoffered kind is skipped rather than substituted.
        assertEquals(
            TransportKind.CLASSIC_BLUETOOTH,
            requireNotNull(
                negotiation.preferring(
                    listOf(TransportKind.LE_AUDIO, TransportKind.CLASSIC_BLUETOOTH),
                ),
            ).kind,
        )
    }

    @Test
    fun preferringReturnsNullWhenNothingInTheCandidateListIsAvailable() {
        val candidates = TransportKind.entries
            .filter { it != TransportKind.UNKNOWN }
            .map { kind ->
                TransportBoundary.refused(kind, OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE)
            }
        val negotiation = TransportNegotiation(candidates = candidates, selected = null)

        assertNull(negotiation.preferring(TransportKind.entries.toList()))
    }

    @Test
    fun anEmptyCandidateListAndAnAllRefusedListAreBothUndetermined() {
        assertTrue(TransportNegotiation(candidates = emptyList(), selected = null).isUndetermined)

        val allRefused = TransportNegotiation(
            candidates = listOf(
                TransportBoundary.refused(
                    kind = TransportKind.GATT,
                    reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                ),
                TransportBoundary.refused(
                    kind = TransportKind.RFCOMM,
                    reason = OmniBudsErrorCategory.RFCOMM_FAILURE,
                ),
            ),
            selected = null,
        )
        assertTrue(allRefused.isUndetermined)
    }

    @Test
    fun aNegotiationWithAUsableCandidateIsNotUndetermined() {
        val determined = TransportNegotiation(
            candidates = listOf(
                TransportBoundary.refused(
                    kind = TransportKind.GATT,
                    reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
                ),
                TransportBoundary.established(
                    kind = TransportKind.RFCOMM,
                    supportEvidence = VerificationLevel.HARDWARE_VERIFIED,
                ),
            ),
            selected = TransportKind.RFCOMM,
        )

        // The refused GATT row stays visible; determining one channel does not erase the others
        // (PROTO-XPORT-005).
        assertFalse(determined.isUndetermined)
        assertEquals(2, determined.candidates.size)
        assertTrue(determined.candidates.first { it.kind == TransportKind.GATT }.availability.isRefused)
    }

    @Test
    fun everyBluetoothSubInterfaceIsABluetoothTransport() {
        assertEquals(5, subInterfaces.size)

        val names = subInterfaces.map { it.simpleName.orEmpty() }
        assertEquals(names.toSet().size, names.size)
        assertTrue(names.containsAll(SUB_INTERFACE_NAMES))
        assertTrue(names.all { it.endsWith("Transport") })
        assertFalse(names.contains("BluetoothTransport"))
    }

    @Test
    fun everyTransportKindCanHoldABoundaryWithoutAssumingGattIsPresent() {
        val boundaries = TransportKind.entries.map { kind ->
            TransportBoundary.refused(kind, OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE)
        }

        // One row per kind, including UNKNOWN (a channel nobody established) and VENDOR_SPECIFIC
        // (a mechanism kind, with no mechanism behind it yet).
        assertEquals(TransportKind.entries.toList(), boundaries.map { it.kind })
        assertTrue(TransportNegotiation(candidates = boundaries, selected = null).isUndetermined)

        // A device that offers no GATT row at all is expressible, stays determinable, and is never
        // reported as unsupported because of the missing kind (ARCH-XPORT-003).
        val noGattAtAll = TransportNegotiation(
            candidates = listOf(
                TransportBoundary.established(TransportKind.RFCOMM, VerificationLevel.LAB_TESTED),
            ),
            selected = TransportKind.RFCOMM,
        )
        assertTrue(noGattAtAll.candidates.none { it.kind == TransportKind.GATT })
        assertEquals(
            TransportKind.RFCOMM,
            requireNotNull(noGattAtAll.preferring(listOf(TransportKind.GATT, TransportKind.RFCOMM))).kind,
        )
    }

    /**
     * The boundary stays a boundary: no Phase 2 transport file probes, opens or touches a channel.
     *
     * [BluetoothTransport.probeAvailability] is declared exactly once, in that interface, and is
     * called nowhere; the platform mechanism names that would make these interfaces an implementation
     * appear only in prose, which the scan strips (Phase 2 prompt section 6, ADR-P1-013).
     */
    @Test
    fun noPhaseTwoTransportCodeOpensOrProbesAChannel() {
        val sources = transportMainSources()
        val codeLines = sources.flatMap { file -> codeLinesOf(file).map { line -> file.name to line } }

        val probeDeclarations = codeLines.filter { (_, line) -> line.contains("probeAvailability") }
        assertEquals(1, probeDeclarations.size)
        assertTrue(
            probeDeclarations.all { (name, line) ->
                name == "BluetoothTransport.kt" && line.trim().startsWith("suspend fun")
            },
            "probeAvailability may be declared only by BluetoothTransport: $probeDeclarations",
        )
        // A call site would reach the member through a receiver, which a declaration never does.
        assertTrue(codeLines.none { (_, line) -> line.contains(".probeAvailability") })

        val mechanismTokens = listOf(
            "connectGatt", "closeGatt", "writeCharacteristic", "readCharacteristic",
            "setCharacteristicNotification", "createRfcommSocket", "listenUsingRfcomm",
            "BluetoothSocket",
        )
        val violations = codeLines
            .filter { (_, line) -> mechanismTokens.any { token -> line.contains(token) } }
            .map { (name, line) -> "$name: $line" }
        assertTrue(violations.isEmpty(), "transport holds mechanics, not boundaries:\n$violations")
    }

    private fun transportMainSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/transport")
        assertTrue(
            root.isDirectory,
            "The boundary scan is meaningless without its input; expected '$root'.",
        )
        val sources = root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "No transport sources found; the scan would be vacuous.")
        return sources
    }

    /** Comment and KDoc lines removed, so documenting a forbidden thing is not a violation. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private companion object {
        /** The five boundaries by simple name, so the assertion above has a runtime half. */
        val SUB_INTERFACE_NAMES = listOf(
            "BleTransport",
            "ClassicTransport",
            "GattTransport",
            "LeAudioTransport",
            "RfcommTransport",
        )
    }
}
