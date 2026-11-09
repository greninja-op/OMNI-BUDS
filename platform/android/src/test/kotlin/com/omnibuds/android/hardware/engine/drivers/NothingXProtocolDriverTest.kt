package com.omnibuds.android.hardware.engine.drivers

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NothingXProtocolDriverTest {

    private val driver = NothingXProtocolDriver()

    @Test
    fun testBatteryQueryCommandFraming() {
        val cmd = driver.buildBatteryQueryCommand()
        assertEquals(0x55.toByte(), cmd[0])
        assertEquals(0x60.toByte(), cmd[1])
        assertEquals(0x01.toByte(), cmd[2]) // Protocol version
        assertEquals(0x07.toByte(), cmd[3]) // Cmd ID Low
        assertEquals(0xC0.toByte(), cmd[4]) // Cmd ID High
        assertEquals(0x00.toByte(), cmd[5]) // Payload length = 0
        // Total length = 6 header + 2 CRC = 8 bytes
        assertEquals(8, cmd.size)
    }

    @Test
    fun testAncCommandBuilding() {
        val cmdHigh = driver.buildAncCommand("High")
        assertEquals(0x55.toByte(), cmdHigh[0])
        assertEquals(0x60.toByte(), cmdHigh[1])
        assertEquals(0x0F.toByte(), cmdHigh[3])
        assertEquals(0xF0.toByte(), cmdHigh[4])
        // Sub-mode for High is 0x01, sub-level 0x03
        assertEquals(0x01.toByte(), cmdHigh[6])
        assertEquals(0x03.toByte(), cmdHigh[7])

        val cmdTransparency = driver.buildAncCommand("Transparency")
        assertEquals(0x02.toByte(), cmdTransparency[6])

        val cmdOff = driver.buildAncCommand("Off")
        assertEquals(0x00.toByte(), cmdOff[6])
    }

    @Test
    fun testUltraBassCommandBuilding() {
        val cmdLevel3 = driver.buildUltraBassCommand(true, 3)
        assertEquals(0x55.toByte(), cmdLevel3[0])
        assertEquals(0x60.toByte(), cmdLevel3[1])
        assertEquals(0x19.toByte(), cmdLevel3[3]) // CMD_ULTRA_BASS
        assertEquals(0xA0.toByte(), cmdLevel3[4]) // SUB_ULTRA_BASS
        assertEquals(0x01.toByte(), cmdLevel3[6]) // Enabled = true
        assertEquals(0x03.toByte(), cmdLevel3[7]) // Level = 3

        val cmdDisabled = driver.buildUltraBassCommand(false, 1)
        assertEquals(0x00.toByte(), cmdDisabled[6]) // Enabled = false
    }

    @Test
    fun testEqualizerCommandBuilding() {
        val diracCmd = driver.buildEqualizerCommand("Dirac Opteo")
        assertEquals(0x55.toByte(), diracCmd[0])
        assertEquals(0x0D.toByte(), diracCmd[3]) // CMD_EQ
        assertEquals(0xE0.toByte(), diracCmd[4]) // SUB_EQ_PRESET
        assertEquals(0x00.toByte(), diracCmd[6]) // 0x00 = Dirac Opteo

        val bassCmd = driver.buildEqualizerCommand("More Bass")
        assertEquals(0x02.toByte(), bassCmd[6])
    }

    @Test
    fun testCustom3BandEqCommandBuilding() {
        val eqCmd = driver.buildCustom3BandEqCommand(bass = 2, mid = -1, treble = 4)
        assertEquals(0x0D.toByte(), eqCmd[3]) // CMD_EQ
        assertEquals(0xE1.toByte(), eqCmd[4]) // SUB_EQ_3BAND
        assertEquals(3.toByte(), eqCmd[5]) // 3 bytes payload
        assertEquals(2.toByte(), eqCmd[6])
        assertEquals((-1).toByte(), eqCmd[7])
        assertEquals(4.toByte(), eqCmd[8])
    }

    @Test
    fun testParseBatteryReportSplitEarbudsWithoutCase() {
        // Frame: Header (55 60 01) Cmd (07 C0) Len (03) Payload (Left=80%, Right=75%, Case=0xFF/unknown) CRC16
        // Left: 80% (0x50), not charging
        // Right: 75% | 0x80 = 0xCB (75% charging)
        // Case: 0xFF (-1, undocked / not reporting)
        val payload = byteArrayOf(0x50, 0xCB.toByte(), 0xFF.toByte())
        val frame = buildFrame(0x07, 0xC0, payload)

        val report = driver.parseBatteryReport(frame)
        assertNotNull(report)
        assertEquals(80, report!!.left)
        assertFalse(report.leftCharging)
        assertEquals(75, report.right)
        assertTrue(report.rightCharging)
        assertNull(report.case, "Undocked/0xFF case byte must result in null case battery")
        assertFalse(report.caseCharging)
    }

    @Test
    fun testParseBatteryReportSplitEarbudsWithValidCase() {
        // Left: 90% (0x5A)
        // Right: 90% (0x5A)
        // Case: 65% charging (0x41 | 0x80 = 0xC1)
        val payload = byteArrayOf(0x5A, 0x5A, 0xC1.toByte())
        val frame = buildFrame(0x07, 0xC0, payload)

        val report = driver.parseBatteryReport(frame)
        assertNotNull(report)
        assertEquals(90, report!!.left)
        assertEquals(90, report.right)
        assertEquals(65, report.case)
        assertTrue(report.caseCharging)
    }

    @Test
    fun testParseBatteryReportHardwareResponseOpcodeAndOfflineBuds() {
        // Opcode 0x87 (hardware response to query 0x07)
        // Left: 0xFF (offline/unavailable)
        // Right: 85% charging (0x55 | 0x80 = 0xD5)
        // Case: 0x00 (undocked/offline)
        val payload = byteArrayOf(0xFF.toByte(), 0xD5.toByte(), 0x00)
        val frame = buildFrame(0x87, 0xC0, payload)

        val report = driver.parseBatteryReport(frame)
        assertNotNull(report)
        assertNull(report!!.left, "0xFF must be parsed as null Left battery")
        assertFalse(report.leftCharging, "Offline bud must not be marked as charging")
        assertEquals(85, report.right)
        assertTrue(report.rightCharging)
        assertNull(report.case, "0x00 case byte must be parsed as null Case battery")
        assertFalse(report.caseCharging)
    }

    private fun buildFrame(cmdLow: Int, cmdHigh: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(6 + payload.size + 2)
        frame[0] = 0x55.toByte()
        frame[1] = 0x60.toByte()
        frame[2] = 0x01.toByte()
        frame[3] = cmdLow.toByte()
        frame[4] = cmdHigh.toByte()
        frame[5] = payload.size.toByte()
        System.arraycopy(payload, 0, frame, 6, payload.size)
        // Add dummy CRC
        frame[frame.size - 2] = 0x00
        frame[frame.size - 1] = 0x00
        return frame
    }
}
