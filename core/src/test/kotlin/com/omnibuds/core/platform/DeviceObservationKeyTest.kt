package com.omnibuds.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The join criterion of ADR-P3-010, tested as the two things it is there to prevent.
 *
 * The first is a leak: an app holding the connect-class permission receives real device addresses, so
 * a key that printed itself would put a rotating handle on a person's belongings into whatever log the
 * framework happens to write, which is the duty SEC-LOG-002 and ADR-P2-015 already paid for. The second
 * is a weld: the same absence of a key arriving twice must not become one device, because prompt
 * section 12 forbids merging two devices and a value-equality join would do exactly that on the quiet.
 *
 * Nothing here asserts what an *address* means as identity over time - the research records that as
 * UNVERIFIED (section 5.4, item U-2), and this repository does not test a platform promise it has not
 * collected.
 */
class DeviceObservationKeyTest {

    @Test
    fun aKeyNeverPrintsTheAddressItWraps() {
        // Asserted against the shape of an address rather than against one test constant, because the
        // redaction has to hold for every address and not merely for the one this file happens to use.
        val pattern = Regex("([0-9A-F]{2}[:.-]){5}[0-9A-F]{2}")
        val reported = listOf(
            "00:11:22:AA:BB:CC",
            "AA:BB:CC:11:22:33",
            "04:5E:7C:9B:2A:F1",
        )
        for (address in reported) {
            val key = DeviceObservationKey.ofReportedAddress(address)
            assertFalse(pattern.containsMatchIn(key.toString()), "the key's own text leaked an address")
            assertTrue(key.toString().contains("length"), "redaction still has to say something useful")
        }
    }

    @Test
    fun anObservationPrintsItsRedactedKeyRatherThanAPartOfADevice() {
        // The record is what actually reaches a diagnostic string, so the duty propagates through it and
        // not only through the key typed on its own.
        val observation = DeviceObservation.reported(
            key = DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC"),
            link = DeviceConnectionState.CONNECTED,
            bond = DeviceBondState.BONDED,
            availability = DeviceAvailability.AVAILABLE,
        )

        assertFalse(Regex("([0-9A-F]{2}[:.-]){5}[0-9A-F]{2}").containsMatchIn(observation.toString()))
        assertTrue(observation.toString().contains("link-address"))
    }

    @Test
    fun twoReportsOfOneAddressJoinAndTwoAddressesNeverDo() {
        val first = DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC")
        val same = DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC")
        val other = DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CD")

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertTrue(first.joinableWith(same))
        assertFalse(first.joinableWith(other), "two devices are two devices")
        assertNotEquals(first, other)
    }

    @Test
    fun twoConventionsForOneAddressStillJoin() {
        // Two mechanisms can hand back the same address in different case or with stray space, and a
        // join that misses that produces a device appearing twice, which is the failure mode the design
        // accepts on a rotating address and must not invent for a stable one.
        val reported = DeviceObservationKey.ofReportedAddress("00:11:22:aa:bb:cc")
        val alsoReported = DeviceObservationKey.ofReportedAddress("  00:11:22:AA:BB:CC  ")

        assertTrue(reported.joinableWith(alsoReported))
    }

    @Test
    fun anEmptyOrBlankAddressIsNoAddressAtAll() {
        // The falsy platform return ADR-P2-012 warns about: "" is what a refused read looks like, and
        // making it a key would give every device the platform was silent about one shared identity.
        for (nothing in listOf("", "   ", "\t\n ", null)) {
            val key = DeviceObservationKey.ofReportedAddress(nothing)

            assertEquals(DeviceObservationKey.NotReported, key, "blank text is absence, not an address")
            assertFalse(key.canIdentifyAcrossObservations)
        }
    }

    @Test
    fun twoReportsWithNoKeyStayTwoDevices() {
        // The case prompt section 12 forbids, arriving through equality rather than through a name: a
        // data object equals itself, so a join on equality alone would weld them together. The
        // predicate is what a join has to consult first, and this is the test that stops anyone from
        // "simplifying" it away.
        val first = DeviceObservationKey.ofReportedAddress(null)
        val second = DeviceObservationKey.ofReportedAddress("   ")

        assertEquals(first, second, "absence is a value, so it compares equal to itself")
        assertFalse(first.joinableWith(second), "equal is not the same as attributable")
        assertTrue(first.isUnattributable())
        assertTrue(second.isUnattributable())
    }

    @Test
    fun anUnattributedKeyJoinsWithNothingIncludingAnAddress() {
        val nothing = DeviceObservationKey.NotReported
        val something = DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC")

        assertFalse(nothing.joinableWith(something))
        assertFalse(something.joinableWith(nothing))
        assertFalse(something.isUnattributable())
        assertTrue(nothing.isUnattributable())
    }

    @Test
    fun aKeyCanBeUsedAsAMapKeyWithoutAnyoneReadingTheAddress() {
        // The engine's fold is a keyed lookup, so hashing has to work; and the only readable facts about
        // a key are its kind and its length, which is the whole of what a log may carry.
        val keys = listOf("00:11:22:AA:BB:CC", "00:11:22:AA:BB:CD")
            .map { address -> DeviceObservationKey.ofReportedAddress(address) }
        val joined = keys.associateWith { it.toString() }

        assertEquals(2, joined.size, "two addresses must not collapse into one map entry")
        assertTrue(joined.keys.all { it.canIdentifyAcrossObservations })
    }

    @Test
    fun theRedactedTextStatesTheKindAndTheLengthAndNothingElse() {
        val address = "00:11:22:AA:BB:CC"
        val key = DeviceObservationKey.ofReportedAddress(address)

        assertEquals(
            "DeviceObservationKey.AddressBacked(kind=link-address, length=${address.length})",
            key.toString(),
        )
        assertEquals("DeviceObservationKey.NotReported", DeviceObservationKey.NotReported.toString())
    }
}
