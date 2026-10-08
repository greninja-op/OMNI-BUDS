package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OB-P12-REQ-001, OB-P12-REQ-003, OB-P12-REQ-004: the five independent
 * control-capability dimensions.
 */
class CodecControlCapabilityTest {

    @Test
    fun `LDAC supported but not selectable or configurable is valid`() {
        val cap = CodecControlCapability(
            codec = Codec.LDAC,
            observable = true,
            supported = true,
            selectable = false,
            configurable = false,
            verifiable = true,
            evidence = testEvidence(),
        )
        assertTrue(cap.supported)
        assertFalse(cap.selectable)
        assertFalse(cap.configurable)
        assertFalse(cap.controllable)
    }

    @Test
    fun `fully controllable codec is representable`() {
        val cap = fullControlCapability(Codec.LDAC)
        assertTrue(cap.controllable)
    }

    @Test
    fun `unknown capability is all false`() {
        val cap = CodecControlCapability.unknown(Codec.AAC, testEvidence())
        assertFalse(cap.observable)
        assertFalse(cap.supported)
        assertFalse(cap.selectable)
        assertFalse(cap.configurable)
        assertFalse(cap.verifiable)
        assertFalse(cap.controllable)
    }

    @Test
    fun `dimensions are independent of each other`() {
        // verifiable without selectable: we can observe, but not change.
        val cap = CodecControlCapability(
            codec = Codec.AAC,
            observable = true,
            supported = true,
            selectable = false,
            configurable = false,
            verifiable = true,
            evidence = testEvidence(),
        )
        assertTrue(cap.verifiable)
        assertFalse(cap.controllable)
    }
}
