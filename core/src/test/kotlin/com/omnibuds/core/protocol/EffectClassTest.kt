package com.omnibuds.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The read/write retry asymmetry as data (Phase 0 `specs.md` section 4, PROTO-ERR-002,
 * PROTO-ERR-003, master section 30).
 *
 * Tier T1.
 */
class EffectClassTest {

    @Test
    fun onlyReadingMayBeRepeatedAutomatically() {
        assertTrue(EffectClass.READ.permitsAutomaticRetry)
        assertFalse(EffectClass.SIDE_EFFECTING_WRITE.permitsAutomaticRetry)
        assertFalse(EffectClass.IRREVERSIBLE_WRITE.permitsAutomaticRetry)
    }

    @Test
    fun noDeclaredClassEscapesTheRule() {
        // Exhaustive over the enum's own membership, so adding a class cannot quietly
        // become retryable by default.
        val retryable = EffectClass.entries.filter { it.permitsAutomaticRetry }

        assertEquals(listOf(EffectClass.READ), retryable)
    }

    @Test
    fun bothWriteClassesAreWritesEvenThoughTheirReversibilityDiffers() {
        val writes = EffectClass.entries.filter { it != EffectClass.READ }

        assertEquals(2, writes.size)
        assertTrue(writes.contains(EffectClass.SIDE_EFFECTING_WRITE))
        assertTrue(writes.contains(EffectClass.IRREVERSIBLE_WRITE))
    }

    @Test
    fun aTimedOutWriteIsResolvedByReadingStateNotByResending() {
        // PROTO-ERR-002 worked example: SET ANC -> Timeout -> DO NOT send SET ANC again ->
        // READ CURRENT ANC -> determine actual state. The vocabulary for that resolution is
        // "the write does not permit automatic retry", which is what a caller reads.
        val setAnc = EffectClass.SIDE_EFFECTING_WRITE
        val readAnc = EffectClass.READ

        assertFalse(setAnc.permitsAutomaticRetry)
        assertTrue(readAnc.permitsAutomaticRetry)
    }
}
