package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Behaviour tests for [DeviceFingerprint] and its [DeviceFingerprint.identityKey]:
 * the fingerprint foundations of the Phase 1 prompt (sections 7 and 10, section 49
 * P1-DOM-001/P1-DOM-005 evidence rules) and the identifier-minimisation rule of
 * `docs/phases/phase-0/security-governance.md` SEC-ID-001/003/004.
 *
 * Tier T1. The UUID and device-class values below are inert fixture text: they assert
 * nothing about any real product and claim no meaning for any of them.
 */
class DeviceFingerprintTest {

    @Test
    fun nothingObservedProducesTheExplicitlyRecognisableNothingKnownKey() {
        val fingerprint = DeviceFingerprint.empty()

        assertTrue(fingerprint.isEntirelyUnobserved)
        assertEquals(DeviceFingerprint.NOTHING_KNOWN_KEY, fingerprint.identityKey())
        assertTrue(fingerprint.identityKey().contains("nothing-known"))
    }

    @Test
    fun sparseEvidenceKeyIsDistinguishableFromNothingKnown() {
        val sparse = DeviceFingerprint(deviceClass = REPORTED_DEVICE_CLASS)

        assertFalse(sparse.isEntirelyUnobserved)
        assertNotEquals(DeviceFingerprint.NOTHING_KNOWN_KEY, sparse.identityKey())
    }

    @Test
    fun theSameEvidenceDiscoveredInAnotherOrderGivesTheSameKey() {
        val forwards = DeviceFingerprint(
            manufacturerData = listOf(entry(3000, "0109"), entry(4000, "ABCD")),
            serviceUuids = setOf(FIRST_SERVICE, SECOND_SERVICE),
            characteristicUuids = mapOf(
                FIRST_SERVICE to listOf(FIRST_CHARACTERISTIC, SECOND_CHARACTERISTIC),
                SECOND_SERVICE to listOf(THIRD_CHARACTERISTIC),
            ),
            deviceClass = REPORTED_DEVICE_CLASS,
            transportCandidates = setOf(TransportKind.GATT, TransportKind.RFCOMM),
            protocolCandidates = listOf("candidate-alpha", "candidate-beta"),
        )
        val backwards = DeviceFingerprint(
            manufacturerData = listOf(entry(4000, "ABCD"), entry(3000, "0109")),
            serviceUuids = setOf(SECOND_SERVICE, FIRST_SERVICE),
            characteristicUuids = mapOf(
                SECOND_SERVICE to listOf(THIRD_CHARACTERISTIC),
                FIRST_SERVICE to listOf(SECOND_CHARACTERISTIC, FIRST_CHARACTERISTIC),
            ),
            deviceClass = REPORTED_DEVICE_CLASS,
            transportCandidates = setOf(TransportKind.RFCOMM, TransportKind.GATT),
            protocolCandidates = listOf("candidate-beta", "candidate-alpha"),
        )

        assertEquals(forwards.identityKey(), backwards.identityKey())
    }

    @Test
    fun aDifferentServiceSetGivesADifferentKey() {
        val first = DeviceFingerprint(serviceUuids = setOf(FIRST_SERVICE))
        val second = DeviceFingerprint(serviceUuids = setOf(SECOND_SERVICE))
        val both = DeviceFingerprint(serviceUuids = setOf(FIRST_SERVICE, SECOND_SERVICE))

        assertNotEquals(first.identityKey(), second.identityKey())
        assertNotEquals(first.identityKey(), both.identityKey())
        assertNotEquals(second.identityKey(), both.identityKey())
    }

    @Test
    fun theSameServiceSpelledInAnotherCaseIsNotANewDevice() {
        val upper = DeviceFingerprint(serviceUuids = setOf(FIRST_SERVICE.uppercase()))
        val lower = DeviceFingerprint(serviceUuids = setOf(FIRST_SERVICE.lowercase()))

        assertEquals(upper.identityKey(), lower.identityKey())
    }

    @Test
    fun firmwareEvidenceIsExcludedFromTheIdentityKey() {
        // A firmware update changes the build, not the device, so folding versions in
        // would make an updated device look newly discovered.
        val before = DeviceFingerprint(
            serviceUuids = setOf(FIRST_SERVICE),
            firmware = FirmwareInfo(firmwareVersion = "1.0.0", verification = VerificationLevel.INFERRED),
        )
        val after = before.copy(
            firmware = FirmwareInfo(firmwareVersion = "9.9.9", verification = VerificationLevel.INFERRED),
        )

        assertNotEquals(after.firmware, before.firmware)
        assertEquals(before.identityKey(), after.identityKey())
    }

    @Test
    fun addressTextCannotSurviveIntoTheIdentityKey() {
        val fingerprint = DeviceFingerprint(
            serviceUuids = setOf("11:22:33:44:55:66"),
            manufacturerData = listOf(entry(companyId = null, dataHex = "AA:BB:CC:DD:EE:FF")),
        )

        val key = fingerprint.identityKey()

        // No canonical address separator exists anywhere in a key, while the evidence
        // itself is folded rather than dropped.
        assertFalse(key.contains(':'))
        assertTrue(key.contains("11-22-33-44-55-66"))
        assertFalse(key.contains("11:22:33:44:55:66"))
    }

    @Test
    fun anUnknownTransportCandidateAssertsNoKnowledgeAndAddsNoEvidence() {
        val unknownOnly = DeviceFingerprint(transportCandidates = setOf(TransportKind.UNKNOWN))
        val viaGatt = DeviceFingerprint(transportCandidates = setOf(TransportKind.GATT))
        val viaGattAndUnknown = DeviceFingerprint(
            transportCandidates = setOf(TransportKind.GATT, TransportKind.UNKNOWN),
        )

        assertTrue(unknownOnly.isEntirelyUnobserved)
        assertEquals(DeviceFingerprint.NOTHING_KNOWN_KEY, unknownOnly.identityKey())
        assertNotEquals(DeviceFingerprint.NOTHING_KNOWN_KEY, viaGatt.identityKey())
        assertEquals(viaGatt.identityKey(), viaGattAndUnknown.identityKey())
    }

    @Test
    fun aPlaceholderManufacturerRecordIsNotEvidence() {
        val fingerprint = DeviceFingerprint(
            manufacturerData = listOf(entry(companyId = null, dataHex = "   "), entry(companyId = null, dataHex = null)),
        )

        assertTrue(fingerprint.isEntirelyUnobserved)
        assertEquals(DeviceFingerprint.NOTHING_KNOWN_KEY, fingerprint.identityKey())
    }

    @Test
    fun anEmptyCollectionMeansNothingSeenRatherThanNothingPresent() {
        val nothingSeen = DeviceFingerprint(serviceUuids = emptySet())
        val oneServiceSeen = DeviceFingerprint(serviceUuids = setOf(FIRST_SERVICE))

        // The type cannot distinguish "the device has no services" from "we did not
        // look", which is exactly why absence is never recorded as UNSUPPORTED here.
        assertTrue(nothingSeen.isEntirelyUnobserved)
        assertFalse(oneServiceSeen.isEntirelyUnobserved)
        assertNotEquals(nothingSeen.identityKey(), oneServiceSeen.identityKey())
    }

    private fun entry(companyId: Int?, dataHex: String?): ManufacturerDataEntry =
        ManufacturerDataEntry(companyId = companyId, dataHex = dataHex)

    private companion object {
        // Inert fixture text, patterned on how a platform spells a 16-bit assigned
        // number; no claim is made that any of these identify a real service.
        const val FIRST_SERVICE = "0000-A001-0000-1000-8000-00A0B0C1D2E3"
        const val SECOND_SERVICE = "0000-A002-0000-1000-8000-00A0B0C1D2E3"
        const val FIRST_CHARACTERISTIC = "0000-A003-0000-1000-8000-00A0B0C1D2E3"
        const val SECOND_CHARACTERISTIC = "0000-A004-0000-1000-8000-00A0B0C1D2E3"
        const val THIRD_CHARACTERISTIC = "0000-A005-0000-1000-8000-00A0B0C1D2E3"
        const val REPORTED_DEVICE_CLASS = 7936
    }
}
