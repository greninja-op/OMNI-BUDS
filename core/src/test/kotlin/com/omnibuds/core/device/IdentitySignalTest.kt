package com.omnibuds.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour of [IdentitySignal] as the boundary where platform text stops being trusted input:
 * prompt §6's six quality distinctions, §15's length and format validation, and §19's signal cases
 * (missing, empty, whitespace, malformed).
 *
 * Tier T1. Values below are inert fixture text asserting nothing about any real product.
 */
class IdentitySignalTest {

    @Test
    fun aReportedValueIsObservedAndKeepsItsText() {
        val signal = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "Example Buds",
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        assertEquals(SignalQuality.OBSERVED, signal.quality)
        assertEquals("Example Buds", signal.rawValue)
        assertTrue(signal.isUsable)
    }

    @Test
    fun blankAndMissingTextBecomeUnknownNotAFabricatedValue() {
        for (raw in listOf(null, "", "   ")) {
            val signal = IdentitySignal.observed(
                kind = IdentitySignalKind.USER_ALIAS,
                value = raw,
                source = SignalSource.PLATFORM,
                reliability = SignalReliability.USER_CHOSEN_TEXT,
            )
            assertEquals(SignalQuality.UNKNOWN, signal.quality, "blank alias for [$raw]")
            assertNull(signal.rawValue)
            assertFalse(signal.isUsable)
        }
    }

    @Test
    fun surroundingWhitespaceIsTrimmedButInnerTextIsPreserved() {
        val signal = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "  Example Buds  ",
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        assertEquals("Example Buds", signal.rawValue)
    }

    @Test
    fun oversizedVendorTextIsRejectedAndItsPayloadDiscarded() {
        val tooLong = "x".repeat(IdentitySignal.MAX_VALUE_LENGTH + 1)
        val signal = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = tooLong,
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        assertEquals(SignalQuality.INVALID, signal.quality)
        assertNull(signal.rawValue, "an invalid value must not keep the untrusted text")
        assertFalse(signal.isUsable)
    }

    @Test
    fun unavailableAndUnknownAreDifferentSentencesAboutDifferentThings() {
        val unavailable = IdentitySignal.unavailable(IdentitySignalKind.MANUFACTURER_DATA)
        val unknown = IdentitySignal.unknown(IdentitySignalKind.SERVICE_UUID)

        assertEquals(SignalQuality.UNAVAILABLE, unavailable.quality)
        assertEquals(SignalQuality.UNKNOWN, unknown.quality)
        assertFalse(unavailable.isUsable)
        assertFalse(unknown.isUsable)
    }

    @Test
    fun derivedTextCarriesTheNormalizerSourceAndIsUsable() {
        val derived = IdentitySignal.derived(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "example buds",
            reliability = SignalReliability.PLATFORM_ATTRIBUTE,
            atEpochMillis = null,
        )

        assertEquals(SignalQuality.DERIVED, derived.quality)
        assertEquals(SignalSource.NORMALIZED, derived.source)
        assertTrue(derived.isUsable)
    }

    @Test
    fun aRejectedValueNeverSurvivesIntoReliability() {
        val signal = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "x".repeat(IdentitySignal.MAX_VALUE_LENGTH + 1),
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        assertEquals(SignalReliability.NONE, signal.reliability, "an unusable signal carries no weight")
    }

    @Test
    fun aControlCharacterAnywhereInTheValueIsRejected() {
        val signal = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "Example" + BELL + "Buds",
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        assertEquals(SignalQuality.INVALID, signal.quality)
        assertNull(signal.rawValue)
    }

    private companion object {
        private const val BELL = "\u0007"
    }
}
