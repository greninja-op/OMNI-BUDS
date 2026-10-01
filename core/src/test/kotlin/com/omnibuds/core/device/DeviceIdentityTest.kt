package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for [DeviceIdentity], plus the version record that sits beside it:
 * the device-identity cases required by the Phase 1 prompt (section 36 "Device
 * identity", section 49 P1-DOM-001) — known manufacturer, unknown manufacturer, model
 * missing, firmware missing, and the blank-is-unknown rule.
 *
 * `TEST-` ids are issued by `docs/phases/phase-1/test-plan.md`, which does not exist
 * yet; method names carry behaviour-plus-condition per
 * `docs/phases/phase-0/specs.md` section 1.4 until then. Tier T1: pure logic, no
 * radio, no device, nothing about hardware behaviour is claimed.
 */
class DeviceIdentityTest {

    @Test
    fun knownManufacturerIsRetainedAndCounted() {
        val identity = DeviceIdentity.of(manufacturer = "Example Audio")

        assertEquals("Example Audio", identity.manufacturer)
        assertEquals(1, identity.knownFieldCount)
        assertFalse(identity.isEntirelyUnknown)
        assertFalse(identity.isFullyKnown)
    }

    @Test
    fun unknownManufacturerStaysNullAndCountsAsNothingKnown() {
        val identity = DeviceIdentity.unknown()

        assertNull(identity.manufacturer)
        assertNull(identity.model)
        assertNull(identity.displayName)
        assertNull(identity.modelId)
        assertEquals(0, identity.knownFieldCount)
        assertTrue(identity.isEntirelyUnknown)
    }

    @Test
    fun missingModelStaysNullWhileTheFieldsThatWereReportedSurvive() {
        val identity = DeviceIdentity.of(
            manufacturer = "Example Audio",
            displayName = "Desk Headphones",
        )

        assertNull(identity.model)
        assertNull(identity.modelId)
        assertEquals("Example Audio", identity.manufacturer)
        assertEquals("Desk Headphones", identity.displayName)
        assertEquals(2, identity.knownFieldCount)
        assertFalse(identity.isFullyKnown)
    }

    @Test
    fun blankTextIsTreatedAsUnknownRatherThanAsAValue() {
        val normalisedIdentity = DeviceIdentity.of(
            manufacturer = "   ",
            model = "",
            displayName = "\t \n",
            modelId = "  ",
        )
        val reportedDirectly = DeviceIdentity(
            manufacturer = "  ",
            model = " ",
            displayName = "\t",
            modelId = "",
        )

        assertNull(normalisedIdentity.manufacturer)
        assertEquals(0, normalisedIdentity.knownFieldCount)
        assertTrue(normalisedIdentity.isEntirelyUnknown)

        // Padding supplied straight to the constructor cannot masquerade as knowledge.
        assertEquals(0, reportedDirectly.knownFieldCount)
        assertTrue(reportedDirectly.isEntirelyUnknown)
        assertFalse(reportedDirectly.isFullyKnown)
    }

    @Test
    fun reportedTextIsTrimmedButNeverInvented() {
        val identity = DeviceIdentity.of(displayName = "  Living Room Buds  ")

        assertEquals("Living Room Buds", identity.displayName)
        assertNull(identity.manufacturer)
    }

    @Test
    fun fullyKnownRequiresAllFourFieldsToBeReported() {
        val complete = DeviceIdentity.of(
            manufacturer = "Example Audio",
            model = "EB-1",
            displayName = "Example EB-1",
            modelId = "eb-1-0001",
        )
        val nearlyComplete = complete.copy(displayName = "  ")

        assertEquals(4, complete.knownFieldCount)
        assertTrue(complete.isFullyKnown)
        assertEquals(3, nearlyComplete.knownFieldCount)
        assertFalse(nearlyComplete.isFullyKnown)
    }

    @Test
    fun mergeFillsOnlyTheFieldsThatAreStillUnknown() {
        val partial = DeviceIdentity.of(manufacturer = "Example Audio")
        val observedLater = DeviceIdentity.of(model = "EB-1", displayName = "Example EB-1")

        val merged = partial.mergedWith(observedLater)

        assertEquals("Example Audio", merged.manufacturer)
        assertEquals("EB-1", merged.model)
        assertEquals("Example EB-1", merged.displayName)
        assertNull(merged.modelId)
        assertEquals(3, merged.knownFieldCount)
    }

    @Test
    fun mergeNeverRestatesAKnownFieldWithADifferentValue() {
        val established = DeviceIdentity.of(model = "EB-1")
        val conflicting = DeviceIdentity.of(model = "EB-2", manufacturer = "Example Audio")

        val merged = established.mergedWith(conflicting)

        // Two different known values are a conflict for a human or a protocol record,
        // not something the model may silently overwrite or hybridise.
        assertEquals("EB-1", merged.model)
        assertEquals("Example Audio", merged.manufacturer)
    }

    @Test
    fun mergeNeverLetsBlankTextWinAField() {
        val merged = DeviceIdentity.of(manufacturer = "Example Audio")
            .mergedWith(DeviceIdentity(manufacturer = "  ", model = " "))

        assertEquals("Example Audio", merged.manufacturer)
        assertNull(merged.model)
    }

    @Test
    fun firmwareMissingStaysMissingRatherThanBecomingVersionZero() {
        val notRead = FirmwareInfo(verification = VerificationLevel.INFERRED)

        assertNull(notRead.firmwareVersion)
        assertNull(notRead.hardwareRevision)
        assertNull(notRead.protocolVersion)
        assertEquals(0, notRead.knownVersionCount)
        assertTrue(notRead.hasNoVersionEvidence)
        assertFalse(notRead.isFullyKnown)
    }

    @Test
    fun blankVersionTextIsNotCountedAsAVersion() {
        val partlyRead = FirmwareInfo(
            firmwareVersion = "  ",
            hardwareRevision = "Rev A",
            verification = VerificationLevel.INFERRED,
        )

        // A padded string is not a version: only "Rev A" is counted.
        assertEquals(1, partlyRead.knownVersionCount)
        assertFalse(partlyRead.hasNoVersionEvidence)
        assertFalse(partlyRead.isFullyKnown)
    }
}
