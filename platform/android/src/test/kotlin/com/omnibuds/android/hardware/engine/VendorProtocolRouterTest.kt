package com.omnibuds.android.hardware.engine

import com.omnibuds.android.hardware.engine.drivers.GenericBleProberDriver
import com.omnibuds.android.hardware.engine.drivers.NothingEarbudsDriver
import com.omnibuds.android.hardware.engine.drivers.OnePlusHeyMelodyDriver
import com.omnibuds.android.hardware.engine.drivers.SamsungGalaxyBudsDriver
import com.omnibuds.android.hardware.engine.drivers.XiaomiAirohaRaceDriver
import com.omnibuds.android.hardware.protocol.HexUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VendorProtocolRouterTest {

    @Test
    fun `OnePlusHeyMelodyDriver matches realme Buds T300 and builds valid framing`() {
        val driver = OnePlusHeyMelodyDriver()
        assertTrue(driver.matches("realme Buds T300", "74:D7:13:C4:FF:EF"))
        assertTrue(driver.matches("OnePlus Buds Pro 2", "E4:12:1D:01:02:03"))

        val ancFrame = driver.buildAncCommand("ANC")
        assertEquals(0xAA.toByte(), ancFrame[0]) // Magic AA header
        assertEquals(0x04.toByte(), ancFrame[4]) // Opcode 0x0404 lo
        assertEquals(0x04.toByte(), ancFrame[5]) // Opcode 0x0404 hi
        // Mode value 0x02 for ANC
        assertEquals(0x02.toByte(), ancFrame[ancFrame.size - 1])

        val ancOnFrame = driver.buildAncCommand("ANC_ON")
        assertArrayEquals(ancFrame, ancOnFrame)

        val transFrame = driver.buildAncCommand("TRANSPARENCY")
        assertEquals(0x04.toByte(), transFrame[transFrame.size - 1])

        val offFrame = driver.buildAncCommand("OFF")
        assertEquals(0x01.toByte(), offFrame[offFrame.size - 1])
    }

    @Test
    fun `XiaomiAirohaRaceDriver matches Redmi Buds and builds RACE framing`() {
        val driver = XiaomiAirohaRaceDriver()
        assertTrue(driver.matches("Redmi Buds 5 Pro", "78:02:F8:11:22:33"))
        assertTrue(driver.matches("EarFun Air Pro 3", "00:1E:C0:44:55:66"))

        val ancFrame = driver.buildAncCommand("ANC")
        assertEquals(0x05.toByte(), ancFrame[0]) // RACE magic 0x05
        assertEquals(0x5A.toByte(), ancFrame[1]) // RACE magic 0x5A
        // Opcode 0x0D0A (LE: 0x0A, 0x0D)
        assertEquals(0x0A.toByte(), ancFrame[4])
        assertEquals(0x0D.toByte(), ancFrame[5])
        // Mode 0x01 for ANC
        assertEquals(0x01.toByte(), ancFrame[6])

        val ancOnFrame = driver.buildAncCommand("ANC_ON")
        assertArrayEquals(ancFrame, ancOnFrame)

        val transFrame = driver.buildAncCommand("TRANSPARENCY")
        assertEquals(0x02.toByte(), transFrame[6])
    }

    @Test
    fun `NothingEarbudsDriver matches CMF Buds and builds 55 60 framing`() {
        val driver = NothingEarbudsDriver()
        assertTrue(driver.matches("CMF Buds", "11:22:33:44:55:66"))
        assertTrue(driver.matches("Nothing Ear (2)", "22:33:44:55:66:77"))

        val ancFrame = driver.buildAncCommand("ANC")
        assertEquals(0x55.toByte(), ancFrame[0])
        assertEquals(0x60.toByte(), ancFrame[1])
        assertEquals(0x01.toByte(), ancFrame[2])
        assertEquals(0x0F.toByte(), ancFrame[3]) // CMD_NOISE_CONTROL
        assertEquals(0xF0.toByte(), ancFrame[4]) // SUB_NOISE_CONTROL
        assertEquals(0x01.toByte(), ancFrame[6]) // Mode ANC

        val ancOnFrame = driver.buildAncCommand("ANC_ON")
        assertArrayEquals(ancFrame, ancOnFrame)

        val transFrame = driver.buildAncCommand("TRANSPARENCY")
        assertEquals(0x02.toByte(), transFrame[6])
    }

    @Test
    fun `SamsungGalaxyBudsDriver matches Galaxy Buds and calculates CRC16`() {
        val driver = SamsungGalaxyBudsDriver()
        assertTrue(driver.matches("Galaxy Buds2 Pro", "24:4B:03:00:11:22"))

        val ancFrame = driver.buildAncCommand("ANC")
        assertEquals(0xFD.toByte(), ancFrame[0]) // Start byte
        assertEquals(0x78.toByte(), ancFrame[3]) // MSG_SET_NOISE_CONTROL
        assertEquals(0x01.toByte(), ancFrame[4]) // ANC Mode
        assertEquals(0xDD.toByte(), ancFrame[ancFrame.size - 1]) // End byte

        val ancOnFrame = driver.buildAncCommand("ANC_ON")
        assertArrayEquals(ancFrame, ancOnFrame)

        // Verify CRC16 computation
        val crc = driver.calculateCrc16(byteArrayOf(0x78, 0x01))
        assertTrue(crc != 0)
    }

    @Test
    fun `GenericBleProberDriver matches unknown earphones and generates learn probe frames`() {
        val driver = GenericBleProberDriver()
        assertTrue(driver.matches("Unknown BT Headset", "AA:BB:CC:DD:EE:FF"))

        val frame = driver.buildAncCommand("ANC")
        assertTrue(frame.isNotEmpty())

        val ancOnFrame = driver.buildAncCommand("ANC_ON")
        assertArrayEquals(frame, ancOnFrame)

        val probeFrames = driver.generateLearnProbeFrames()
        assertTrue(probeFrames.size >= 4)
        // Includes HeyMelody query
        assertEquals(0xAA.toByte(), probeFrames[0][0])
        // Includes Airoha RACE query
        assertEquals(0x05.toByte(), probeFrames[1][0])
        assertEquals(0x5A.toByte(), probeFrames[1][1])
        // Includes Nothing query
        assertEquals(0x55.toByte(), probeFrames[2][0])
        assertEquals(0x60.toByte(), probeFrames[2][1])
    }

    @Test
    fun `SamsungGalaxyBudsDriver parses extended status battery reports`() {
        val driver = SamsungGalaxyBudsDriver()
        // FD [len=5, 0] [msg_id=0x61] [L=90, R=85, Case=100, chg=1] [crc_lo, crc_hi] DD
        val body = byteArrayOf(0x61, 90, 85, 100, 0x01)
        val crc = driver.calculateCrc16(body)
        val packet = byteArrayOf(
            0xFD.toByte(),
            5, 0,
            0x61, 90, 85, 100, 0x01,
            (crc and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            0xDD.toByte(),
        )

        val report = driver.parseBatteryReport(packet)
        assertNotNull(report)
        assertEquals(90, report?.left)
        assertEquals(85, report?.right)
        assertEquals(100, report?.case)
        assertTrue(report?.leftCharging == true)
    }
}
