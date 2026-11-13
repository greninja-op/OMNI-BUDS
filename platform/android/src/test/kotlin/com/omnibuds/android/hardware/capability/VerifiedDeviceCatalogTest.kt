package com.omnibuds.android.hardware.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VerifiedDeviceCatalogTest {

    @Test
    fun `CMF Buds resolves to split case-open contract`() {
        val profile = VerifiedDeviceCatalog.resolve("CMF Buds")
        assertNotNull(profile)
        assertEquals(BatteryBudsDisplay.SPLIT, profile!!.batteryContract.budsDisplay)
        assertEquals(BatteryCaseDisplay.SEPARATE, profile.batteryContract.caseDisplay)
        assertEquals(BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN, profile.batteryContract.caseVisibility)
        assertEquals(BatteryCaseWhileBudsOut.HIDDEN, profile.batteryContract.caseWhileBudsOut)
    }

    @Test
    fun `CMF Buds Pro resolves separately from CMF Buds`() {
        val profile = VerifiedDeviceCatalog.resolve("CMF Buds Pro")
        assertNotNull(profile)
        assertEquals("cmf-buds-pro", profile!!.slug)
    }

    @Test
    fun `realme Buds T300 shows the case only while a bud is docked`() {
        val profile = VerifiedDeviceCatalog.resolve("realme Buds T300")
        assertNotNull(profile)
        assertEquals(BatteryBudsDisplay.SPLIT, profile!!.batteryContract.budsDisplay)
        assertEquals(BatteryCaseDisplay.SEPARATE, profile.batteryContract.caseDisplay)
        assertEquals(BatteryCaseVisibility.ONLY_WHEN_DOCKED, profile.batteryContract.caseVisibility)
        assertEquals(BatteryCaseWhileBudsOut.HIDDEN, profile.batteryContract.caseWhileBudsOut)
    }

    @Test
    fun `AirPods Pro 2 uses a combined bud reading`() {
        val profile = VerifiedDeviceCatalog.resolve("AirPods Pro 2 (USB-C)")
        assertNotNull(profile)
        assertEquals(BatteryBudsDisplay.COMBINED, profile!!.batteryContract.budsDisplay)
        assertEquals(BatteryCaseDisplay.SEPARATE, profile.batteryContract.caseDisplay)
    }

    @Test
    fun `Galaxy Buds3 Pro uses split buds and last-known case`() {
        val profile = VerifiedDeviceCatalog.resolve("Galaxy Buds3 Pro")
        assertNotNull(profile)
        assertEquals(BatteryBudsDisplay.SPLIT, profile!!.batteryContract.budsDisplay)
        assertEquals(BatteryCaseVisibility.ALWAYS, profile.batteryContract.caseVisibility)
        assertEquals(BatteryCaseWhileBudsOut.LAST_KNOWN, profile.batteryContract.caseWhileBudsOut)
    }

    @Test
    fun `alias resolves Sennheiser MTW4`() {
        val profile = VerifiedDeviceCatalog.resolve("MTW4")
        assertNotNull(profile)
        assertEquals(BatteryBudsDisplay.COMBINED, profile!!.batteryContract.budsDisplay)
    }

    @Test
    fun `expanded catalog contains mainstream models beyond the pilot`() {
        assertTrue(VerifiedDeviceCatalog.all().size >= 100)
    }

    @Test
    fun `generated records map features and keep driver guardrail`() {
        val all = VerifiedDeviceCatalog.all()
        assertTrue(all.any { it.featureIds.isNotEmpty() && it.capabilityIds.isNotEmpty() })
        assertTrue(all.any { it.featureIds.isNotEmpty() && it.writableCapabilityIds.isEmpty() })
        assertTrue(all.any { it.writableCapabilityIds.isNotEmpty() })
    }

    @Test
    fun `unknown device stays unknown`() {
        assertNull(VerifiedDeviceCatalog.resolve("Acme SoundPods X9"))
    }

    @Test
    fun `JBL Live Beam 3 exposes ANC EQ and spatial`() {
        val profile = VerifiedDeviceCatalog.resolve("Live Beam 3")
        assertNotNull(profile)
        assertTrue(profile!!.capabilityIds.containsAll(setOf("anc_mode", "eq_preset", "spatial_audio")))
    }

    @Test
    fun `official-app features without a driver are not writable`() {
        val profile = VerifiedDeviceCatalog.resolve("Live Beam 3")
        assertNotNull(profile)
        assertTrue(profile!!.writableCapabilityIds.isEmpty())
    }

    @Test
    fun `control guardrail drops ids without a dispatch path`() {
        assertEquals(
            setOf("anc_mode", "eq_preset"),
            VerifiedControlCatalog.actionable(setOf("anc_mode", "eq_preset", "not-a-real-control")),
        )
    }

    @Test
    fun `WH-1000XM5 has no case`() {
        val profile = VerifiedDeviceCatalog.resolve("WH-1000XM5")
        assertNotNull(profile)
        assertEquals(BatteryCaseDisplay.HIDDEN, profile!!.batteryContract.caseDisplay)
    }

    @Test
    fun `Galaxy Buds3 resolves separately from Buds3 Pro`() {
        val buds3 = VerifiedDeviceCatalog.resolve("Galaxy Buds3")
        val pro = VerifiedDeviceCatalog.resolve("Galaxy Buds3 Pro")
        assertNotNull(buds3)
        assertNotNull(pro)
        assertEquals("galaxy-buds3", buds3!!.slug)
        assertEquals("galaxy-buds3-pro", pro!!.slug)
    }

    @Test
    fun `CMF Buds has a verified writable path`() {
        val profile = VerifiedDeviceCatalog.resolve("CMF Buds")
        assertNotNull(profile)
        assertTrue(profile!!.writableCapabilityIds.contains("anc_mode"))
    }

    @Test
    fun `AirPods Pro 2 does not claim a user EQ preset control`() {
        val profile = VerifiedDeviceCatalog.resolve("AirPods Pro 2")
        assertNotNull(profile)
        assertFalse(profile!!.capabilityIds.contains("eq_preset"))
    }
}
