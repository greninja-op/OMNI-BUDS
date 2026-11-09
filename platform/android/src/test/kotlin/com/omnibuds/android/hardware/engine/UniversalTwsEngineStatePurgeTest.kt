package com.omnibuds.android.hardware.engine

import com.omnibuds.android.hardware.capability.StrictBrandProfileDatabase
import com.omnibuds.android.hardware.capability.StrictBrandProfileResolver
import com.omnibuds.android.hardware.engine.drivers.NothingXProtocolDriver
import com.omnibuds.android.hardware.protocol.TwsBatteryReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversalTwsEngineStatePurgeTest {

    private val cmfAddress = "AA:BB:CC:DD:EE:01"
    private val realmeAddress = "74:D7:13:C4:FF:EF"

    @BeforeEach
    fun setUp() {
        UniversalTwsEngine.disconnectAll()
    }

    @Test
    fun testCmfBuds1HidesCaseBatteryWhenUndockedOrInvalid() {
        val profile = StrictBrandProfileResolver.resolveProfile("CMF Buds", cmfAddress)
        assertFalse(profile.supportsCaseBatteryWhenUndocked)

        // Simulate incoming battery report where case is undocked (null)
        val reportUndocked = TwsBatteryReport(
            left = 85,
            right = 80,
            case = null,
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            sourceProtocol = "Nothing X (0x07 C0)",
        )

        UniversalTwsEngine.handleBatteryReport(cmfAddress, reportUndocked)

        val latest = UniversalTwsEngine.latestBatteryReports.value[cmfAddress]
        assertNotNull(latest)
        assertEquals(85, latest!!.left)
        assertEquals(80, latest.right)
        assertNull(latest.case, "CMF Buds 1 must strictly have null case battery when undocked")
    }

    @Test
    fun testCmfBuds1ShowsCaseBatteryOnlyWhenHardwareExplicitlyReportsValidPercent() {
        // When docked, case reports valid 65%
        val reportDocked = TwsBatteryReport(
            left = 90,
            right = 90,
            case = 65,
            leftCharging = true,
            rightCharging = true,
            caseCharging = true,
            sourceProtocol = "Nothing X (0x07 C0)",
        )

        UniversalTwsEngine.handleBatteryReport(cmfAddress, reportDocked)

        val latestDocked = UniversalTwsEngine.latestBatteryReports.value[cmfAddress]
        assertNotNull(latestDocked)
        assertEquals(65, latestDocked!!.case)
        assertTrue(latestDocked.caseCharging)

        // Now earbuds are removed from case -> report has case = null
        val reportRemoved = TwsBatteryReport(
            left = 90,
            right = 90,
            case = null,
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            sourceProtocol = "Nothing X (0x07 C0)",
        )

        UniversalTwsEngine.handleBatteryReport(cmfAddress, reportRemoved)

        val latestRemoved = UniversalTwsEngine.latestBatteryReports.value[cmfAddress]
        assertNotNull(latestRemoved)
        // Since supportsCaseBatteryWhenUndocked is false, it MUST NOT restore old cached case battery!
        assertNull(latestRemoved!!.case, "CMF Buds 1 must NOT cache/restore case battery when undocked")
    }

    @Test
    fun testRealmeBudsT300MaintainsCaseBatteryWhenUndockedViaPogoPinCaching() {
        val profile = StrictBrandProfileResolver.resolveProfile("realme Buds T300", realmeAddress)
        assertTrue(profile.supportsCaseBatteryWhenUndocked)

        // 1. Report while docked
        val reportDocked = TwsBatteryReport(
            left = 100,
            right = 100,
            case = 75,
            leftCharging = true,
            rightCharging = true,
            caseCharging = false,
            sourceProtocol = "HeyMelody RFCOMM",
        )
        UniversalTwsEngine.handleBatteryReport(realmeAddress, reportDocked)

        // 2. Earbuds removed from case (undocked, case = null)
        val reportUndocked = TwsBatteryReport(
            left = 95,
            right = 95,
            case = null,
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            sourceProtocol = "HeyMelody RFCOMM",
        )
        UniversalTwsEngine.handleBatteryReport(realmeAddress, reportUndocked)

        val latest = UniversalTwsEngine.latestBatteryReports.value[realmeAddress]
        assertNotNull(latest)
        // Realme T300 supports case battery caching via pogo pins
        assertEquals(75, latest!!.case)
        assertFalse(latest.caseIsLive)
    }

    @Test
    fun testDeviceSwitchingPurgesPreviousDeviceTelemetry() {
        // Connect Realme T300 first
        val realmeReport = TwsBatteryReport(
            left = 80,
            right = 80,
            case = 60,
            sourceProtocol = "HeyMelody RFCOMM",
        )
        UniversalTwsEngine.handleBatteryReport(realmeAddress, realmeReport)
        assertTrue(UniversalTwsEngine.latestBatteryReports.value.containsKey(realmeAddress))
        assertEquals(80, UniversalTwsEngine.leftBattery)
        assertEquals(80, UniversalTwsEngine.rightBattery)
        assertEquals(60, UniversalTwsEngine.caseBattery)

        // Device switch: purge previous session state
        UniversalTwsEngine.purgeSessionState(realmeAddress)
        assertFalse(UniversalTwsEngine.latestBatteryReports.value.containsKey(realmeAddress), "Previous device telemetry must be purged on switch")
        assertNull(UniversalTwsEngine.activeDriver)
        assertNull(UniversalTwsEngine.activeProfile)
        assertNull(UniversalTwsEngine.activeCapabilityManifest)
        assertNull(UniversalTwsEngine.leftBattery)
        assertNull(UniversalTwsEngine.rightBattery)
        assertNull(UniversalTwsEngine.caseBattery)

        // Now connect CMF Buds
        val cmfReport = TwsBatteryReport(
            left = 100,
            right = 95,
            case = null,
            sourceProtocol = "Nothing X (0x07 C0)",
        )
        UniversalTwsEngine.handleBatteryReport(cmfAddress, cmfReport)
        assertTrue(UniversalTwsEngine.latestBatteryReports.value.containsKey(cmfAddress))
        assertFalse(UniversalTwsEngine.latestBatteryReports.value.containsKey(realmeAddress))
        assertEquals(100, UniversalTwsEngine.leftBattery)
        assertEquals(95, UniversalTwsEngine.rightBattery)
        assertNull(UniversalTwsEngine.caseBattery, "CMF Buds case battery must be null when undocked")
    }
}
