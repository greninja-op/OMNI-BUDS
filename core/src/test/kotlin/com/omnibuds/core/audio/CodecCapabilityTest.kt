package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The worked example the whole audio model exists for (P1-DOM-003, master section 15,
 * audio-governance section 6 worked examples A and B): the user selected LDAC, the
 * stack is running AAC.
 */
class CodecCapabilityTest {

    /** Two records, two truths: LDAC ENABLED, AAC ACTIVE — and only AAC claims to carry audio. */
    @Test
    fun ldacEnabledWhileAacActiveKeepsBothFactsSeparate() {
        val ldac = CodecCapability(Codec.LDAC, CodecState.ENABLED, configurable = true)
        val aac = CodecCapability(Codec.AAC, CodecState.ACTIVE, configurable = false)

        assertEquals(CodecState.ENABLED, ldac.state)
        assertEquals(CodecState.ACTIVE, aac.state)

        assertTrue(aac.isActive)
        assertFalse(ldac.isActive)

        // "LDAC enabled (preferred), not negotiated — not carrying audio. Active codec: AAC."
        assertTrue(ldac.supportsAtLeast(CodecState.ENABLED))
        assertFalse(ldac.supportsAtLeast(CodecState.NEGOTIATED))
        assertFalse(ldac.supportsAtLeast(CodecState.ACTIVE))
        assertEquals(Codec.AAC, listOf(ldac, aac).single { it.isActive }.codec)
    }

    /** A codec that is merely supported is not in use, however much the registry contains it (AUD-REG-006). */
    @Test
    fun ldacSupportedWhileAacActiveRendersAsSupportedNotInUse() {
        val ldac = CodecCapability(Codec.LDAC, CodecState.SUPPORTED, configurable = false)
        val aac = CodecCapability(Codec.AAC, CodecState.ACTIVE, configurable = false)

        assertFalse(ldac.isActive)
        assertFalse(ldac.isUsable)
        assertTrue(aac.isActive)
    }

    /** No record reports itself active unless its own state is the ACTIVE rung. */
    @Test
    fun isActiveFollowsOwnStateOnly() {
        CodecState.entries.forEach { state ->
            val record = CodecCapability(Codec.LDAC, state, configurable = false)
            assertEquals(state == CodecState.ACTIVE, record.isActive, "isActive for state $state")
        }
    }

    /**
     * The six-boolean shape of the Phase 1 prompt section 17 allowed nonsense such as
     * `active = true, supported = false`. With one ladder value, activity and
     * non-support cannot both be claimed, so the contradiction is unrepresentable
     * rather than merely discouraged (ADR-P1-005).
     */
    @Test
    fun contradictoryActiveAndUnsupportedIsUnrepresentable() {
        val unsupported = CodecCapability.unsupported(Codec.LDAC)

        // The boolean shape allowed `active = true, supported = false`; the ladder does not.
        assertFalse(unsupported.isActive)
        assertFalse(unsupported.supportsAtLeast(CodecState.SUPPORTED))
        CodecState.entries.forEach { state ->
            val record = CodecCapability(Codec.LDAC, state, configurable = false)
            assertFalse(
                record.isActive && state == CodecState.UNSUPPORTED,
                "state $state would claim activity while denying support",
            )
        }
    }

    /** `isUsable` covers AVAILABLE..ACTIVE and excludes SUPPORTED, UNSUPPORTED and UNKNOWN. */
    @Test
    fun usableIsAnythingTheSessionCouldStillCarry() {
        val usable = listOf(
            CodecState.AVAILABLE,
            CodecState.ENABLED,
            CodecState.NEGOTIATED,
            CodecState.ACTIVE,
        )
        usable.forEach { state ->
            assertTrue(
                CodecCapability(Codec.LDAC, state, configurable = false).isUsable,
                "$state should be usable",
            )
        }
        listOf(CodecState.UNKNOWN, CodecState.UNSUPPORTED, CodecState.SUPPORTED).forEach { state ->
            assertFalse(
                CodecCapability(Codec.LDAC, state, configurable = false).isUsable,
                "$state should not be usable",
            )
        }
    }
}
