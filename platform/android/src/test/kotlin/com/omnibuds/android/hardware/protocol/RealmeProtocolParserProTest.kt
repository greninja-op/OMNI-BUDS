package com.omnibuds.android.hardware.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealmeProtocolParserProTest {

    @Test
    fun `builds pro ANC level command with correct opcode 0x040D`() {
        val maxCmd = RealmeProtocolParser.buildProAncLevelCommand("MAX_50DB")
        val hex = HexUtils.formatHex(maxCmd)
        // Header: AA, cmd: 0x0D 0x04
        assertTrue(hex.startsWith("AA"))
        assertTrue(hex.contains("0D 04"))
        // Payload ends with 0x03 (Max)
        assertEquals(0x03.toByte(), maxCmd.last())

        val mildCmd = RealmeProtocolParser.buildProAncLevelCommand("MILD")
        assertEquals(0x01.toByte(), mildCmd.last())
    }

    @Test
    fun `builds wind noise reduction command with opcode 0x040E`() {
        val cmd = RealmeProtocolParser.buildWindNoiseReductionCommand(true)
        val hex = HexUtils.formatHex(cmd)
        assertTrue(hex.contains("0E 04"))
        assertEquals(0x01.toByte(), cmd.last())
    }

    @Test
    fun `builds transparency level command with 5-level slider and voice boost`() {
        val cmd = RealmeProtocolParser.buildTransparencyLevelCommand(level = 4, voiceBoost = true)
        val hex = HexUtils.formatHex(cmd)
        assertTrue(hex.contains("0F 04"))
        // Level is 4, voice boost flag is 1
        assertEquals(0x04.toByte(), cmd[cmd.size - 2])
        assertEquals(0x01.toByte(), cmd.last())
    }

    @Test
    fun `builds in-ear detection command with opcode 0x0406`() {
        val cmd = RealmeProtocolParser.buildInEarDetectionCommand(true)
        val hex = HexUtils.formatHex(cmd)
        assertTrue(hex.contains("06 04"))
        assertEquals(0x01.toByte(), cmd.last())
    }

    @Test
    fun `builds multipoint dual device command with opcode 0x041E`() {
        val cmd = RealmeProtocolParser.buildMultipointCommand(true)
        val hex = HexUtils.formatHex(cmd)
        assertTrue(hex.contains("1E 04"))
        assertEquals(0x01.toByte(), cmd.last())
    }

    @Test
    fun `builds conversational awareness command with opcode 0x0426`() {
        val cmd = RealmeProtocolParser.buildConversationalAwarenessCommand(true)
        val hex = HexUtils.formatHex(cmd)
        assertTrue(hex.contains("26 04"))
        assertEquals(0x01.toByte(), cmd.last())
    }

    @Test
    fun `builds pinch speed command with opcode 0x0418`() {
        val defaultSpeed = RealmeProtocolParser.buildPinchSpeedCommand("DEFAULT")
        val slowSpeed = RealmeProtocolParser.buildPinchSpeedCommand("SLOW")
        val slowestSpeed = RealmeProtocolParser.buildPinchSpeedCommand("SLOWEST")

        assertEquals(0x00.toByte(), defaultSpeed.last())
        assertEquals(0x01.toByte(), slowSpeed.last())
        assertEquals(0x02.toByte(), slowestSpeed.last())
    }

    @Test
    fun `builds hi-res codec enable command with opcode 0x041A`() {
        val enableCmd = RealmeProtocolParser.buildHiResCodecCommand(true)
        val hex = HexUtils.formatHex(enableCmd)
        assertTrue(hex.contains("1A 04"))
        assertEquals(0x01.toByte(), enableCmd.last())

        val disableCmd = RealmeProtocolParser.buildHiResCodecCommand(false)
        assertEquals(0x00.toByte(), disableCmd.last())
    }

    @Test
    fun `builds dynamic bass command supporting -5 to +5 intensity range`() {
        val bassMin = RealmeProtocolParser.buildDynamicBassCommand(true, -5)
        assertEquals(0x00.toByte(), bassMin.last()) // -5 maps to 0

        val bassNeutral = RealmeProtocolParser.buildDynamicBassCommand(true, 0)
        assertEquals(0x05.toByte(), bassNeutral.last()) // 0 maps to 5

        val bassMax = RealmeProtocolParser.buildDynamicBassCommand(true, 5)
        assertEquals(0x0A.toByte(), bassMax.last()) // +5 maps to 10
    }
}
