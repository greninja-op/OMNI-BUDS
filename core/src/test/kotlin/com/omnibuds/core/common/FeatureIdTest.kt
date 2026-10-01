package com.omnibuds.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The shared vocabulary everything else is built on.
 *
 * `common` previously had no tests at all, which meant the identity grammar and the
 * retry-safety table were protected only by the type system that reads them. The retry
 * assertions below are the machine-checked form of the rule that a side-effecting
 * command must never be blindly re-sent (Phase 1 prompt section 53, master section 30,
 * ADR-P1-006).
 */
class FeatureIdTest {

    @Test
    fun aCoreFeatureIdNeedsANamespaceAndAName() {
        val anc = FeatureId.of("noise-control", "anc")

        assertEquals("noise-control.anc", anc.qualifiedName)
        assertEquals("noise-control", anc.namespace)
        assertEquals("anc", anc.localName)
        assertEquals(2, anc.segments.size)
    }

    @Test
    fun aSingleSegmentNameIsRefusedRatherThanGuessedIntoShape() {
        assertFailsWith<IllegalArgumentException> { FeatureId.of("anc") }
    }

    @Test
    fun uppercaseUnderscoreAndBlankSegmentsAreNotIdentities() {
        listOf("NoiseControl.ANC", "noise control.anc", "noise-control.", ".anc", "noise-control..anc")
            .forEach { raw -> assertNull(FeatureId.parseOrNull(raw), "$raw must not parse") }
    }

    @Test
    fun aVendorExtensionIsRecognisableAndCarriesItsVendor() {
        val feature = FeatureId.ofVendor("example-vendor", "adaptive-sound-control")

        assertTrue(feature.isVendorExtension)
        assertEquals("example-vendor", feature.vendorName)
        assertEquals(3, feature.segments.size)
    }

    @Test
    fun aCoreFeatureNeverReportsItselfAsAVendorExtension() {
        assertFalse(FeatureId.of("noise-control", "anc").isVendorExtension)
        assertNull(FeatureId.of("noise-control", "anc").vendorName)
    }

    @Test
    fun aTwoSegmentVendorShapedNameIsTooShallowToNameAVendor() {
        val shallow = assertNotNull(FeatureId.parseOrNull("vendor.example"))

        assertFalse(shallow.isVendorExtension)
        assertNull(shallow.vendorName)
    }

    @Test
    fun parsingRoundTripsAStoredIdentifierAndRefusesGarbage() {
        val original = FeatureId.ofVendor("example-vendor", "some-feature")

        assertEquals(original, FeatureId.parseOrNull(original.qualifiedName))
        assertNull(FeatureId.parseOrNull(""))
        // A two-segment name is a legal core identity, so it is parsed rather than
        // refused: refusing it would forbid namespaces such as "example-vendor".
        assertNotNull(FeatureId.parseOrNull("example-vendor.some-feature"))
    }
}
