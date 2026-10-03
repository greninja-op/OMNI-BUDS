package com.omnibuds.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [IdentityNormalizer]'s rules under prompt §7's "normalization must not destroy meaningful identity
 * distinctions" and §19's normalization cases. ADR-P5-005 pins these to trim, whitespace-collapse and
 * case-fold only; every test here is a check that the normalizer did NOT get more helpful.
 *
 * Tier T1. Values are inert fixture text.
 */
class IdentityNormalizerTest {

    @Test
    fun trimsCollapsesInternalWhitespaceAndCaseFolds() {
        val normalized = IdentityNormalizer.normalize("  Example   BUDS  ")

        assertEquals("example buds", normalized)
    }

    @Test
    fun aModelNumberSuffixSurvivesBecauseItIsTheWholeDistinction() {
        // WF-1000XM3 vs XM4 differ only by digits a helpful normalizer would strip. They must not merge.
        val a = IdentityNormalizer.normalize("WF-1000XM3")
        val b = IdentityNormalizer.normalize("WF-1000XM4")

        assertEquals("wf-1000xm3", a)
        assertEquals("wf-1000xm4", b)
        kotlin.test.assertNotEquals(a, b, "digit-stripping would weld two different models into one key")
    }

    @Test
    fun brandAndModelTokenAreNotSplitEvenWhenCaseDiffers() {
        val a = IdentityNormalizer.normalize("LinkBuds")
        val b = IdentityNormalizer.normalize("LinkBuds S")

        assertEquals("linkbuds", a)
        assertEquals("linkbuds s", b)
        // Case-folding must not turn "LinkBuds S" into "LinkBuds": the trailing token is a real model.
        kotlin.test.assertNotEquals(a, b)
    }

    @Test
    fun blankAndMissingTextNormalizeToNullNotToAnEmptyString() {
        assertNull(IdentityNormalizer.normalize(null))
        assertNull(IdentityNormalizer.normalize(""))
        assertNull(IdentityNormalizer.normalize("     "))
    }

    @Test
    fun punctuationIsKeptRatherThanStripped() {
        // Removing '-' or '+' would let two differently-named devices collide in one rule.
        assertEquals("pro-500", IdentityNormalizer.normalize("Pro-500"))
    }

    @Test
    fun theVersionedFormTagsTheValueWithTheRuleVersionThatMadeIt() {
        val versioned = IdentityNormalizer.normalizeVersioned("Example BUDS")

        assertNotNull(versioned)
        assertEquals("example buds", versioned.value)
        assertEquals(IdentityNormalizer.NORMALIZATION_VERSION, versioned.normalizationVersion)
    }

    @Test
    fun equivalentInputsAcrossRoundsNormalizeIdentically() {
        val first = IdentityNormalizer.normalize("  Exa  mple ")
        val second = IdentityNormalizer.normalize("  EXA   MPLE ")

        assertEquals(first, second, "one device re-read at different casing must produce one key")
    }
}
