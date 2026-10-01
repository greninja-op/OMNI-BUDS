package com.omnibuds.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for [BatteryState]: the battery cases required by the Phase 1
 * prompt (section 15, section 36 "Battery", section 49 P1-DOM-004) and master
 * section 23 — unknown is `null`, never `0%`, and a flat battery is a reading.
 *
 * Tier T1: pure value logic, no device, no claim that any reading was real.
 */
class BatteryStateTest {

    @Test
    fun anUnknownLevelIsNullAndNeverZero() {
        val unknown = BatteryState.unknown()

        assertNull(unknown.leftLevel)
        assertNull(unknown.rightLevel)
        assertNull(unknown.caseLevel)
        assertEquals(0, unknown.knownFieldCount)
        assertTrue(unknown.isEntirelyUnknown)
        assertFalse(unknown.hasAnyKnownLevel)
    }

    @Test
    fun aReadingOfExactlyZeroIsPreservedAsZeroAndNotTreatedAsMissing() {
        val flat = BatteryState(leftLevel = 0, rightLevel = 0)

        assertEquals(0, flat.leftLevel)
        assertEquals(0, flat.rightLevel)
        assertTrue(flat.hasAnyKnownLevel)
        assertEquals(2, flat.knownFieldCount)
        assertFalse(flat.isEntirelyUnknown)
    }

    @Test
    fun aCaseReadingAloneIsStillSomethingKnown() {
        val caseOnly = BatteryState(caseLevel = 55)

        assertTrue(caseOnly.hasAnyKnownLevel)
        assertNull(caseOnly.leftLevel)
        assertNull(caseOnly.rightLevel)
    }

    @Test
    fun notReportedChargingIsNotReadAsNotCharging() {
        val reportedNotCharging = BatteryState(leftLevel = 40, leftCharging = false)
        val chargingNotMentioned = BatteryState(leftLevel = 40)

        assertEquals(false, reportedNotCharging.leftCharging)
        assertNull(chargingNotMentioned.leftCharging)
        assertNull(chargingNotMentioned.caseCharging)
        assertNull(chargingNotMentioned.rightCharging)
        assertTrue(chargingNotMentioned.hasAnyKnownLevel)
    }

    @Test
    fun asymmetryIsOnlyClaimedWhenBothSidesAreKnown() {
        val asymmetric = BatteryState(leftLevel = 80, rightLevel = 20)
        val symmetric = BatteryState(leftLevel = 80, rightLevel = 80)
        val oneSideMissing = BatteryState(leftLevel = 80, rightLevel = null)
        val bothMissing = BatteryState(caseLevel = 80)

        assertTrue(asymmetric.isAsymmetric)
        assertFalse(symmetric.isAsymmetric)
        assertFalse(oneSideMissing.isAsymmetric)
        assertFalse(bothMissing.isAsymmetric)
    }

    @Test
    fun asymmetryHoldsInEitherDirectionAndAcrossZero() {
        assertTrue(BatteryState(leftLevel = 0, rightLevel = 1).isAsymmetric)
        assertTrue(BatteryState(leftLevel = 1, rightLevel = 0).isAsymmetric)
        assertFalse(BatteryState(leftLevel = 0, rightLevel = 0).isAsymmetric)
    }

    @Test
    fun aLevelOutsideTheReportedRangeIsRejected() {
        val tooHigh = assertFailsWith<IllegalArgumentException> {
            BatteryState(leftLevel = 101)
        }
        val tooLow = assertFailsWith<IllegalArgumentException> {
            BatteryState(rightLevel = -1)
        }
        val caseTooHigh = assertFailsWith<IllegalArgumentException> {
            BatteryState(caseLevel = 1000)
        }

        assertTrue("leftLevel" in requireMessage(tooHigh))
        assertTrue("rightLevel" in requireMessage(tooLow))
        assertTrue("caseLevel" in requireMessage(caseTooHigh))
    }

    @Test
    fun theReportedRangeEndsAreAcceptedAsRealReadings() {
        val extremes = BatteryState(leftLevel = 0, rightLevel = 100, caseLevel = 100)

        assertEquals(0, extremes.leftLevel)
        assertEquals(100, extremes.rightLevel)
        assertEquals(100, extremes.caseLevel)
        assertTrue(extremes.isAsymmetric)
        assertFalse(BatteryState(leftLevel = 0, rightLevel = 0).isAsymmetric)
        assertTrue(BatteryState(leftLevel = 0, rightLevel = 100).isAsymmetric)
    }

    @Test
    fun anUnknownLevelIsAcceptedNextToAKnownOne() {
        val partial = BatteryState(leftLevel = 61, rightLevel = null, caseCharging = true)

        assertEquals(61, partial.leftLevel)
        assertEquals(true, partial.caseCharging)
        assertNull(partial.rightCharging)
        assertEquals(2, partial.knownFieldCount)
    }

    private fun requireMessage(rejected: IllegalArgumentException): String = rejected.message ?: ""
}
