package com.omnibuds.android.hardware.capability

import com.omnibuds.android.ui.compose.model.AudioFormFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrictBrandProfileDatabaseTest {

    @Test
    fun testCmfBuds1ProfileResolution() {
        val profile = StrictBrandProfileResolver.resolveProfile("CMF Buds")
        assertNotNull(profile)
        assertEquals(BrandSchema.NOTHING_X, profile.schema)
        assertEquals("Nothing X", profile.companionAppName)
        assertEquals(AudioFormFactor.STEM_IN_EAR, profile.formFactor)
        assertFalse(profile.supportsCaseBatteryWhenUndocked, "CMF Buds 1 must not support undocked case battery")
        
        // Check ANC modes
        assertEquals(listOf("High (42dB)", "Mid", "Low", "Adaptive"), profile.ancSubLevels)
        assertTrue(profile.hasNoiseControl)
        assertTrue(profile.hasAncSubLevels)
        
        // EQ
        assertEquals("Dirac Opteo", profile.eqPresets.first())
        assertTrue(profile.hasCustom3BandEq)
        assertFalse(profile.hasParametric8BandEq)
        
        // Ultra Bass
        assertTrue(profile.hasUltraBass)
        assertEquals(5, profile.ultraBassMaxLevel)
        
        // Features
        assertTrue(profile.hasInEarDetection)
        assertTrue(profile.hasLowLagMode)
        assertEquals("Low Lag Mode", profile.gameModeLabel)
        assertTrue(profile.hasDualConnection)
        assertTrue(profile.hasFindMyBuds)
        
        // Strict exclusions
        assertFalse(profile.hasSmartDial, "CMF Buds 1 must NOT have Smart Dial")
        assertFalse(profile.hasLdac, "CMF Buds 1 must NOT have LDAC")
        assertFalse(profile.hasSpatialAudio, "CMF Buds 1 must NOT have Spatial Audio")
        assertFalse(profile.hasDolbyAtmos, "CMF Buds 1 must NOT have Dolby Atmos")
        assertFalse(profile.hasVolumeEnhancer, "CMF Buds 1 must NOT have Volume Enhancer")
        assertFalse(profile.hasMindRelax, "CMF Buds 1 must NOT have Mind Relax")
        assertFalse(profile.hasGoldenSound, "CMF Buds 1 must NOT have Golden Sound")
    }

    @Test
    fun testCmfBudsPro2Profile() {
        val profile = StrictBrandProfileResolver.resolveProfile("CMF Buds Pro 2")
        assertNotNull(profile)
        assertEquals(BrandSchema.NOTHING_X, profile.schema)
        assertTrue(profile.hasSmartDial, "CMF Buds Pro 2 MUST have Smart Dial")
        assertTrue(profile.hasLdac, "CMF Buds Pro 2 MUST have LDAC")
        assertTrue(profile.hasSpatialAudio)
    }

    @Test
    fun testNothingEarProfile() {
        val profile = StrictBrandProfileResolver.resolveProfile("Nothing Ear")
        assertNotNull(profile)
        assertEquals(BrandSchema.NOTHING_X, profile.schema)
        assertTrue(profile.hasParametric8BandEq)
        assertTrue(profile.hasEarTipFitTest)
        assertTrue(profile.hasLdac)
        assertTrue(profile.gestures.any { it.displayName.contains("Pinch") })
    }

    @Test
    fun testRealmeBudsT300Profile() {
        val profile = StrictBrandProfileResolver.resolveProfile("realme Buds T300")
        assertNotNull(profile)
        assertEquals(BrandSchema.REALME_LINK, profile.schema)
        assertTrue(profile.supportsCaseBatteryWhenUndocked, "Realme T300 supports case battery caching via pogo pins")
        assertEquals("50ms Game Mode", profile.gameModeLabel)
        assertTrue(profile.hasDolbyAtmos)
        assertTrue(profile.hasVolumeEnhancer)
        assertTrue(profile.hasMindRelax)
        assertFalse(profile.hasDualConnection)
        assertFalse(profile.hasLdac)
    }

    @Test
    fun testHeyMelodyOnePlusBuds3Profile() {
        val profile = StrictBrandProfileResolver.resolveProfile("OnePlus Buds 3")
        assertNotNull(profile)
        assertEquals(BrandSchema.HEY_MELODY, profile.schema)
        assertEquals("HeyMelody", profile.companionAppName)
        assertTrue(profile.hasLdac) // LHDC/Hi-Res
        assertTrue(profile.hasDualConnection)
    }

    @Test
    fun testSamsungGalaxyBuds2ProProfile() {
        val profile = StrictBrandProfileResolver.resolveProfile("Galaxy Buds2 Pro")
        assertNotNull(profile)
        assertEquals(BrandSchema.GALAXY_WEARABLE, profile.schema)
        assertEquals("Galaxy Wearable", profile.companionAppName)
        assertEquals(AudioFormFactor.BUD_BEAN_IN_EAR, profile.formFactor)
        assertTrue(profile.hasSpatialAudio)
    }

    @Test
    fun testSonyWf1000Xm5Profile() {
        val profile = StrictBrandProfileResolver.resolveProfile("Sony WF-1000XM5")
        assertNotNull(profile)
        assertEquals(BrandSchema.SONY_SOUND_CONNECT, profile.schema)
        assertEquals("Sony Sound Connect", profile.companionAppName)
        assertTrue(profile.hasDsee)
        assertTrue(profile.hasSpeakToChat)
    }

    @Test
    fun testAirPodsProProfile() {
        val profile = StrictBrandProfileResolver.resolveProfile("AirPods Pro (2nd generation)")
        assertNotNull(profile)
        assertEquals(BrandSchema.IOS_SETTINGS, profile.schema)
        assertEquals("iOS AirPods Settings", profile.companionAppName)
        assertTrue(profile.hasConversationalAwareness)
        assertTrue(profile.hasPersonalizedVolume)
        assertTrue(profile.hasSpatialAudio)
    }
}
