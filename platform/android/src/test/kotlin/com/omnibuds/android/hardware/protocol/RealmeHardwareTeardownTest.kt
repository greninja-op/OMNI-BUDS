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

        // Payload length = 3
        assertEquals(0x03.toByte(), offFrame[7])
        assertEquals(0x00.toByte(), offFrame[8])

        // Payload starts at index 9: [0x01, 0x01, Mode]
        assertEquals(0x01.toByte(), offFrame[9])
        assertEquals(0x01.toByte(), offFrame[10])
        assertEquals(0x00.toByte(), offFrame[11]) // Mode = OFF (0x00)
        assertEquals(12, offFrame.size)
    }

    @Test
    fun `buildFullHardwareAncCommand for ANC uses the mode byte only`() {
        val mapping = AncByteMapping.PROFILE_A
        val ancFrame = RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)

        assertEquals(0x01.toByte(), ancFrame[11]) // Mode = ANC (0x01)
        assertEquals(12, ancFrame.size)
    }

    @Test
    fun `buildFullHardwareAncCommand for TRANSPARENCY uses the mode byte only`() {
        val mapping = AncByteMapping.PROFILE_A
        val transFrame = RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)

        assertEquals(0x02.toByte(), transFrame[11]) // Mode = Transparency (0x02)
        assertEquals(12, transFrame.size)
    }

    @Test
    fun `buildAncCustomOffCommand builds candidate frames for 3-variant tester`() {
        val candidate0 = RealmeProtocolParser.buildAncCustomOffCommand(0x00.toByte())
        assertEquals(0x00.toByte(), candidate0[11])
        assertEquals(12, candidate0.size)

        val candidate2 = RealmeProtocolParser.buildAncCustomOffCommand(0x02.toByte())
        assertEquals(0x02.toByte(), candidate2[11])

        val candidate3 = RealmeProtocolParser.buildAncCustomOffCommand(0x03.toByte())
        assertEquals(0x03.toByte(), candidate3[11])
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
    fun `realme BES mapping is off 01 transparency 02 anc 04`() {
        val mapping = AncByteMapping.PROFILE_D
        assertEquals(0x01.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("OFF", mapping)[11])
        assertEquals(0x02.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)[11])
        assertEquals(0x04.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)[11])
        assertEquals(0x03.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)[7])
    }

    @Test
    fun `realme T300 mapping is off 02 transparency 01 anc 04`() {
        val mapping = AncByteMapping.PROFILE_E
        assertEquals(0x02.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("OFF", mapping)[11])
        assertEquals(0x01.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)[11])
        assertEquals(0x04.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)[11])
    }

    @Test
    fun `realme T300 measured effect table is anc 01 transparency 02 off 08 and reads back the same way`() {
        val mapping = AncByteMapping.forType(AncProfileType.PROFILE_G)
        assertEquals(0x08.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("OFF", mapping)[11])
        assertEquals(0x02.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)[11])
        assertEquals(0x01.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)[11])
        assertEquals("OFF", mapping.modeForByte(0x08))
        assertEquals("TRANSPARENCY", mapping.modeForByte(0x02))
        assertEquals("ANC", mapping.modeForByte(0x01))
        // 0x04 and 0x00 are not part of the table, so they must never decode to a mode.
        assertEquals(null, mapping.modeForByte(0x04))
        assertEquals(null, mapping.modeForByte(0x00))
    }

    @Test
    fun `realme T300 direct mapping is off 00 transparency 01 anc 04`() {
        val mapping = AncByteMapping.PROFILE_F
        assertEquals(0x00.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("OFF", mapping)[11])
        assertEquals(0x01.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("TRANSPARENCY", mapping)[11])
        assertEquals(0x04.toByte(), RealmeProtocolParser.buildFullHardwareAncCommand("ANC", mapping)[11])
    }

    @Test
    fun `a 03 01 01 mode push is a report bitmask and is not looked up in the write table`() {
        // The old decoder read the byte after `03 01 01` and looked it up in the write
        // table (0x02 = OFF under PROFILE_E). The buds report a bitmask whose bits are not
        // the write values, and on a real T300 write value 0x02 switches to Transparency.
        // The frame is therefore reported raw and never turned into a mode here.
        val mapping = AncByteMapping.PROFILE_E
        val push = byteArrayOf(
            0xAA.toByte(), 0x0B, 0x00, 0x00,
            0x04, 0x02, 0xFF.toByte(),
            0x04, 0x00,
            0x03, 0x01, 0x01, 0x02,
        )
        assertEquals(null, RealmeProtocolParser.parseAncModeResponse(push, mapping))
    }

    @Test
    fun `parseAncModeReports returns the raw mask from a push and from a read reply`() {
        val push = byteArrayOf(
            0xAA.toByte(), 0x0C, 0x00, 0x00,
            0x04, 0x02, 0xFF.toByte(),
            0x05, 0x00,
            0x03, 0x01, 0x01, 0x00, 0x01,
        )
        val pushReports = RealmeProtocolParser.parseAncModeReports(push)
        assertEquals(1, pushReports.size)
        assertEquals(0x0100, pushReports[0].mask)
        assertEquals(true, pushReports[0].viaPush)

        val read = byteArrayOf(
            0xAA.toByte(), 0x0B, 0x00, 0x00,
            0x0C, 0x81.toByte(), 0x43,
            0x04, 0x00,
            0x00, 0x01, 0x01, 0x02,
        )
        val readReports = RealmeProtocolParser.parseAncModeReports(read)
        assertEquals(1, readReports.size)
        assertEquals(0x02, readReports[0].mask)
        assertEquals(false, readReports[0].viaPush)

        val both = push + read
        assertEquals(2, RealmeProtocolParser.parseAncModeReports(both).size)
    }

    @Test
    fun `parseAncModeReports ignores battery frames and a mode set acknowledgement`() {
        val battery = byteArrayOf(
            0xAA.toByte(), 0x0F, 0x00, 0x00, 0x06, 0x81.toByte(), 0x01, 0x08, 0x00,
            0x00, 0x03, 0x01, 0x14, 0x02, 0x46, 0x03, 0x32,
        )
        val setAck = byteArrayOf(
            0xAA.toByte(), 0x08, 0x00, 0x00, 0x04, 0x84.toByte(), 0x01, 0x01, 0x00, 0x00,
        )
        assertEquals(0, RealmeProtocolParser.parseAncModeReports(battery + setAck).size)
    }

    @Test
    fun `parseAncModeResponse accepts vendor opcodes carrying a 01 01 mode payload`() {
        val mapping = AncByteMapping.PROFILE_A // off=0x00, anc=0x01, trans=0x02

        // 0x8204 status frame (OPOv1 ANC query/status opcode) with payload
        // [0x01, 0x01, 0x02]: hardware switched to Transparency.
        val statusFrame = byteArrayOf(
            0xAA.toByte(), 0x0B, 0x00, 0x00,
            0x04, 0x82.toByte(), 0x01,
            0x03, 0x00,
            0x01, 0x01, 0x02,
        )

        assertEquals(
            "TRANSPARENCY",
            RealmeProtocolParser.parseAncModeResponse(statusFrame, mapping),
        )
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
