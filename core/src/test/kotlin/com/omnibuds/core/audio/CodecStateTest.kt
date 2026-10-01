package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Invariants of the [CodecState] ladder (P1-DOM-003, REQ-P1-007).
 *
 * These are the claims the product must never get wrong: supported is not active,
 * and an unread codec is not an unsupported one.
 */
class CodecStateTest {

    /**
     * Covers P1-DOM-003 / AUD-STATE-001 and the master section 15 case that motivated
     * the ladder: a codec that is supported but carries no audio is one ordinary
     * record, and it must not report itself active.
     */
    @Test
    fun supportedButNotActiveIsRepresentableAndNotActive() {
        val ldac = CodecCapability(Codec.LDAC, CodecState.SUPPORTED, configurable = false)

        assertEquals(CodecState.SUPPORTED, ldac.state)
        assertFalse(ldac.isActive)
        assertFalse(ldac.isUsable)
        assertTrue(ldac.supportsAtLeast(CodecState.SUPPORTED))
        assertFalse(ldac.supportsAtLeast(CodecState.AVAILABLE))
    }

    /** A positively rejected codec can never be talked up into satisfying `ACTIVE`. */
    @Test
    fun unsupportedNeverSatisfiesActiveTarget() {
        val rejected = CodecCapability.unsupported(Codec.LDAC)

        assertFalse(rejected.supportsAtLeast(CodecState.ACTIVE))
        assertFalse(rejected.supportsAtLeast(CodecState.NEGOTIATED))
        assertFalse(rejected.supportsAtLeast(CodecState.ENABLED))
        assertFalse(rejected.supportsAtLeast(CodecState.AVAILABLE))
        assertFalse(rejected.supportsAtLeast(CodecState.SUPPORTED))
        assertFalse(rejected.isActive)
        assertFalse(rejected.isUsable)
    }

    /** "Not read" and "does not support" are different statements and stay distinct (AUD-STATE-005). */
    @Test
    fun unknownStaysDistinguishableFromUnsupported() {
        val unknown = CodecCapability.unknown(Codec.LDAC)
        val unsupported = CodecCapability.unsupported(Codec.LDAC)

        assertNotEquals(unknown, unsupported)
        assertEquals(CodecState.UNKNOWN, unknown.state)
        assertEquals(CodecState.UNSUPPORTED, unsupported.state)
        assertFalse(unknown.supportsAtLeast(CodecState.ACTIVE))
        assertFalse(unknown.supportsAtLeast(CodecState.SUPPORTED))
    }

    /** `ENABLED` is the prompt's "selected" rung and is not evidence of negotiating or carrying audio. */
    @Test
    fun enabledIsSelectionButNotNegotiatedOrActive() {
        val selected = CodecCapability(Codec.LDAC, CodecState.ENABLED, configurable = true)

        assertTrue(selected.isUsable)
        assertFalse(selected.isActive)
        assertTrue(selected.supportsAtLeast(CodecState.AVAILABLE))
        assertFalse(selected.supportsAtLeast(CodecState.NEGOTIATED))
        assertFalse(selected.supportsAtLeast(CodecState.ACTIVE))
    }

    /** Declaration order is the ladder: each rung supports the ones below it and no more. */
    @Test
    fun ladderOrderSupportsItselfAndEveryRungBelow() {
        val ladder = listOf(
            CodecState.SUPPORTED,
            CodecState.AVAILABLE,
            CodecState.ENABLED,
            CodecState.NEGOTIATED,
            CodecState.ACTIVE,
        )
        ladder.forEachIndexed { index, rung ->
            val record = CodecCapability(Codec.LDAC, rung, configurable = false)
            ladder.forEachIndexed { targetIndex, target ->
                assertEquals(
                    expected = targetIndex <= index,
                    actual = record.supportsAtLeast(target),
                    message = "$rung against target $target",
                )
            }
        }
    }

    /** A target below the ladder's evidence floor is not an evidence claim, so nothing satisfies it. */
    @Test
    fun nonPositiveTargetIsNeverSatisfied() {
        val active = CodecCapability(Codec.LDAC, CodecState.ACTIVE, configurable = false)

        assertFalse(active.supportsAtLeast(CodecState.UNKNOWN))
        assertFalse(active.supportsAtLeast(CodecState.UNSUPPORTED))
        assertTrue(active.supportsAtLeast(CodecState.ACTIVE))
    }

    /**
     * `CONFIGURABLE` left the ladder: writability is orthogonal to activity, so an
     * ACTIVE codec may be configurable or read-only (ADR-P1-005).
     */
    @Test
    fun configurableIsOrthogonalToTheLadder() {
        val activeWritable = CodecCapability(Codec.LDAC, CodecState.ACTIVE, configurable = true)
        val activeReadOnly = CodecCapability(Codec.LDAC, CodecState.ACTIVE, configurable = false)

        assertEquals(activeWritable.state, activeReadOnly.state)
        assertNotEquals(activeWritable, activeReadOnly)
        assertTrue(activeWritable.isActive)
        assertTrue(activeReadOnly.isActive)
    }
}
