package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.omnibuds.core.state.VerificationLevel

class FirmwareObservationTest {

    @Test
    fun validObservationCreation() {
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "1.5.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )
        assertEquals(FirmwareValidationState.VALID, obs.validationState)
        assertEquals("1.5.0", obs.version.rawValue)
        assertTrue(obs.isTrustworthyForMutations)
    }

    @Test
    fun missingObservationHandling() {
        val obs = FirmwareObservation.create(
            deviceId = "dev-2",
            rawVersion = null,
            source = FirmwareObservationSource.UNKNOWN,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.INFERRED,
        )
        assertEquals(FirmwareValidationState.UNKNOWN_OR_MISSING, obs.validationState)
        assertEquals(FirmwareVersion.Unknown, obs.version)
        assertFalse(obs.isTrustworthyForMutations)
    }

    @Test
    fun malformedOrLongStringHandling() {
        val longString = "A".repeat(200)
        val obs = FirmwareObservation.create(
            deviceId = "dev-3",
            rawVersion = longString,
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )
        assertEquals(FirmwareValidationState.MALFORMED, obs.validationState)
        assertFalse(obs.isTrustworthyForMutations)
    }

    @Test
    fun controlCharactersRejected() {
        val badString = "1.0.0\u0000\u0007"
        val obs = FirmwareObservation.create(
            deviceId = "dev-4",
            rawVersion = badString,
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )
        assertEquals(FirmwareValidationState.REJECTED_BY_POLICY, obs.validationState)
        assertFalse(obs.isTrustworthyForMutations)
    }

    @Test
    fun userManualEntryNotTrustworthyForMutations() {
        val obs = FirmwareObservation.create(
            deviceId = "dev-5",
            rawVersion = "2.0.0",
            source = FirmwareObservationSource.USER_MANUAL_ENTRY,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.INFERRED,
        )
        assertEquals(FirmwareValidationState.VALID, obs.validationState)
        assertFalse(obs.isTrustworthyForMutations)
    }

    @Test
    fun freshnessEvaluation() {
        val obs = FirmwareObservation.create(
            deviceId = "dev-6",
            rawVersion = "2.0.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )
        assertTrue(obs.isFresh(currentTimeMs = 2000L, maxAgeMs = 5000L))
        assertFalse(obs.isFresh(currentTimeMs = 7000L, maxAgeMs = 5000L))
    }
}
