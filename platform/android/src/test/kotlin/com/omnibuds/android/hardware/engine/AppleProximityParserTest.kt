package com.omnibuds.android.hardware.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppleProximityParserTest {

    @Test
    fun `parses standard Apple AirPods proximity continuity beacon`() {
        // Standard 27-byte Continuity frame starting with 0x07, 0x19
        val frame = ByteArray(27)
        frame[0] = 0x07
        frame[1] = 0x19

        // At payload offset 4: Right=9 (90%), Left=8 (80%) -> 0x98
        // At payload offset 5: Charging flags=0b0001 (Left charging), Case=7 (70%) -> 0x17
        frame[2 + 4] = 0x98.toByte()
        frame[2 + 5] = 0x17.toByte()

        val report = AppleProximityParser.parse(frame)
        assertNotNull(report)
        assertEquals(80, report?.left)
        assertEquals(90, report?.right)
        assertEquals(70, report?.case)
        assertTrue(report?.leftCharging == true)
        assertFalse(report?.rightCharging == true)
        assertFalse(report?.caseCharging == true)
    }

    @Test
    fun `parses 25-byte header-stripped continuity beacon`() {
        // 25-byte payload without 0x07 0x19 prefix
        val payload = ByteArray(25)
        payload[4] = 0x75.toByte() // Right=7 (70%), Left=5 (50%)
        payload[5] = 0x28.toByte() // Case=8 (80%), Right charging (bit 1)

        val report = AppleProximityParser.parse(payload)
        assertNotNull(report)
        assertEquals(50, report?.left)
        assertEquals(70, report?.right)
        assertEquals(80, report?.case)
        assertFalse(report?.leftCharging == true)
        assertTrue(report?.rightCharging == true)
        assertFalse(report?.caseCharging == true)
    }

    @Test
    fun `parses clone direct percentage representation`() {
        val payload = byteArrayOf(0x55, 0x50, 0xE4.toByte())
        val report = AppleProximityParser.parse(payload)

        assertNotNull(report)
        assertEquals(85, report?.left)
        assertEquals(80, report?.right)
        assertEquals(100, report?.case)
        assertFalse(report?.leftCharging == true)
        assertFalse(report?.rightCharging == true)
        assertTrue(report?.caseCharging == true)
    }

    @Test
    fun `rejects non-continuity Apple beacons like AirDrop or Find My`() {
        // AirDrop beacon (Type 0x05)
        val airDropData = byteArrayOf(0x05, 0x12, 0x00, 0x04, 0x05, 0x06, 0x07, 0x08)
        assertNull(AppleProximityParser.parse(airDropData))

        // Find My beacon (Type 0x12)
        val findMyData = byteArrayOf(0x12, 0x19, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        assertNull(AppleProximityParser.parse(findMyData))
    }

    @Test
    fun `returns null for invalid or short payload`() {
        assertNull(AppleProximityParser.parse(null))
        assertNull(AppleProximityParser.parse(byteArrayOf(0x07, 0x01)))
    }
}
