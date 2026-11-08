package com.omnibuds.android.hardware.protocol

import com.omnibuds.android.hardware.engine.UniversalDspController
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealmeHardwareTeardownTest {

    @Test
    fun `buildFullHardwareAncCommand for OFF builds zeroed-out mic filter hardware bypass frame`() {
        val mapping = AncByteMapping.PROFILE_A // off = 0x00, anc = 0x01, trans = 0x02
        val offFrame = RealmeProtocolParser.buildFullHardwareAncCommand("OFF", mapping)

        // Header checks
        assertEquals(0xAA.toByte(), offFrame[0]) // Magic header
        assertEquals(0x04.toByte(), offFrame[4]) // CMD_SET_ANC lo (0x0404)
        assertEquals(0x04.toByte(), offFrame[5]) // CMD_SET_ANC hi

        // Payload length = 6
        assertEquals(0x06.toByte(), offFrame[7])
        assertEquals(0x00.toByte(), offFrame[8])

        // Payload starts at index 9: [0x01, 0x01, Mode, SubLevel, VoicePassthrough, WindNoise]
        assertEquals(0x01.toByte(), offFrame[9])
        assertEquals(0x01.toByte(), offFrame[10])
        assertEquals(0x00.toByte(), offFrame[11]) // Mode = OFF (0x00)
        assertEquals(0x00.toByte(), offFrame[12]) // SubLevel/Intensity = 0x00
        assertEquals(0x00.toByte(), offFrame[13]) // VoicePassthrough = 0x00
        assertEquals(0x00.toByte(), offFrame[14]) // WindNoise = 0x00
    }

    @Test
    fun `buildFullHardwareAncCommand for ANC builds max mic active frame`() {
        val mapping = AncByteMapping.PROFILE_A
        val ancFrame = RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)

        assertEquals(0x01.toByte(), ancFrame[11]) // Mode = ANC (0x01)
        assertEquals(0x03.toByte(), ancFrame[12]) // SubLevel = Max (0x03)
        assertEquals(0x00.toByte(), ancFrame[13]) // VoicePassthrough = 0x00
        assertEquals(0x00.toByte(), ancFrame[14]) // WindNoise = 0x00
    }

    @Test
    fun `buildFullHardwareAncCommand for TRANSPARENCY builds ambient passthrough gain frame`() {
        val mapping = AncByteMapping.PROFILE_A
        val transFrame = RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)

        assertEquals(0x02.toByte(), transFrame[11]) // Mode = Transparency (0x02)
        assertEquals(0x01.toByte(), transFrame[12]) // SubLevel/Passthrough Gain = 0x01
        assertEquals(0x01.toByte(), transFrame[13]) // VoicePassthrough = 0x01
        assertEquals(0x00.toByte(), transFrame[14]) // WindNoise = 0x00
    }

    @Test
    fun `buildAncCustomOffCommand builds candidate frames for 3-variant tester`() {
        val candidate0 = RealmeProtocolParser.buildAncCustomOffCommand(0x00.toByte())
        assertEquals(0x00.toByte(), candidate0[11])
        assertEquals(0x00.toByte(), candidate0[12])

        val candidate2 = RealmeProtocolParser.buildAncCustomOffCommand(0x02.toByte())
        assertEquals(0x02.toByte(), candidate2[11])
        assertEquals(0x00.toByte(), candidate2[12])

        val candidate3 = RealmeProtocolParser.buildAncCustomOffCommand(0x03.toByte())
        assertEquals(0x03.toByte(), candidate3[11])
        assertEquals(0x00.toByte(), candidate3[12])
    }

    @Test
    fun `parseAncModeResponse does not falsely match trailing 0x00 as OFF`() {
        val mapping = AncByteMapping.PROFILE_A // off=0x00, anc=0x01, trans=0x02

        // Incoming ANC status frame where Mode=0x01 and SubLevel=0x00
        val ancFrameWithZeroSub = byteArrayOf(
            0xAA.toByte(), 0x0E, 0x00, 0x00,
            0x04, 0x84.toByte(), 0x01,
            0x06, 0x00, // payload length = 6
            0x01, 0x01, 0x01, 0x00, 0x00, 0x00 // payload: subcmd 0x01 0x01, Mode=0x01 (ANC), trailing zeros
        )

        val detected = RealmeProtocolParser.parseAncModeResponse(ancFrameWithZeroSub, mapping)
        assertEquals("ANC", detected, "Must parse primary mode byte at offset 2, NOT trailing 0x00")
    }

    @Test
    fun `parseAncModeResponse with unknown framed candidate does not fall back to prefix byte`() {
        val mapping = AncByteMapping.PROFILE_A // off=0x00, anc=0x01, trans=0x02

        // Frame where Mode=0x7F (unknown/unsupported) with [0x01, 0x01] prefix
        val frameWithUnknownMode = byteArrayOf(
            0xAA.toByte(), 0x0E, 0x00, 0x00,
            0x04, 0x84.toByte(), 0x01,
            0x06, 0x00,
            0x01, 0x01, 0x7F, 0x00, 0x00, 0x00
        )

        val detected = RealmeProtocolParser.parseAncModeResponse(frameWithUnknownMode, mapping)
        org.junit.jupiter.api.Assertions.assertNull(detected, "Unknown mode must return null, not fall back to 0x01 (ANC)")
    }

    @Test
    fun `UniversalDspController disableAmbientAndPassthrough zeroes all software passthrough`() {
        val dsp = UniversalDspController()
        dsp.setAmbientPassthrough(15, voiceFocus = true)
        assertEquals(15, dsp.ambientPassthroughLevel)
        assertTrue(dsp.isVoicePassthroughEnabled)

        dsp.disableAmbientAndPassthrough()
        assertEquals(0, dsp.ambientPassthroughLevel)
        assertFalse(dsp.isVoicePassthroughEnabled)
    }
}
