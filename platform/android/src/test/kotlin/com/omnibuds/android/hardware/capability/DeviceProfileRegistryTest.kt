package com.omnibuds.android.hardware.capability

import com.omnibuds.android.ui.compose.model.AudioFormFactor
import com.omnibuds.android.ui.compose.model.AudioFormFactorResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceProfileRegistryTest {

    @Test
    fun realmeBudsT300_resolvesToStemInEar_withBasicAncAndZeroProMocks() {
        val profile = DeviceProfileRegistry.findProfile("realme Buds T300")
        assertNotNull(profile)
        assertEquals(AudioFormFactor.STEM_IN_EAR, profile!!.formFactor)
        assertTrue(profile.hasAnc)
        assertEquals(30, profile.ancDb)
        assertFalse(profile.hasMultipoint, "T300 must NOT report Multipoint/Dual-connection")
        assertFalse(profile.hasLdac, "T300 must NOT report LDAC Hi-Res")
        assertTrue(profile.hasSpatialAudio360)
        assertTrue(profile.hasGameMode50ms)
    }

    @Test
    fun neckbandProfiles_resolveCorrectly_withMagneticHallSwitchAndVibration() {
        val z2Anc = DeviceProfileRegistry.findProfile("OnePlus Bullets Wireless Z2 ANC")
        assertNotNull(z2Anc)
        assertEquals(AudioFormFactor.NECKBAND, z2Anc!!.formFactor)
        assertTrue(z2Anc.hasAnc)
        assertTrue(z2Anc.hasMagneticHallSwitch)
        assertTrue(z2Anc.hasCollarVibration)
        assertTrue(z2Anc.hasPhysicalInlineButtons)

        val z2Standard = DeviceProfileRegistry.findProfile("OnePlus Bullets Wireless Z2")
        assertNotNull(z2Standard)
        assertEquals(AudioFormFactor.NECKBAND, z2Standard!!.formFactor)
        assertFalse(z2Standard.hasAnc, "Standard Z2 must not claim ANC")
        assertTrue(z2Standard.hasMagneticHallSwitch)
    }

    @Test
    fun overEarHeadsets_resolveToHeadsetOverOnEar_withAmbientAndClamping() {
        val sonyXm5 = DeviceProfileRegistry.findProfile("Sony WH-1000XM5")
        assertNotNull(sonyXm5)
        assertEquals(AudioFormFactor.HEADSET_OVER_ON_EAR, sonyXm5!!.formFactor)
        assertTrue(sonyXm5.hasAnc)
        assertTrue(sonyXm5.has20LevelAmbient)
        assertTrue(sonyXm5.hasSpeakToChat)
        assertTrue(sonyXm5.hasClampingOptimizer)
        assertTrue(sonyXm5.hasSwipeTouchpad)

        val airpodsMax = DeviceProfileRegistry.findProfile("Apple AirPods Max")
        assertNotNull(airpodsMax)
        assertEquals(AudioFormFactor.HEADSET_OVER_ON_EAR, airpodsMax!!.formFactor)
        assertTrue(airpodsMax.hasDigitalCrown)

        val crusher = DeviceProfileRegistry.findProfile("Skullcandy Crusher Evo")
        assertNotNull(crusher)
        assertTrue(crusher!!.hasSensoryCrusherBass)
    }

    @Test
    fun openBackAndOpenEar_strictlyHideAnc_andEnableOpenAcousticsStudio() {
        val devaPro = DeviceProfileRegistry.findProfile("HiFiMAN Deva Pro Wireless")
        assertNotNull(devaPro)
        assertEquals(AudioFormFactor.HEADSET_OPEN_BACK, devaPro!!.formFactor)
        assertFalse(devaPro.hasAnc, "Open-Back cans must NEVER report ANC")
        assertTrue(devaPro.hasDipoleBassRestorer)
        assertTrue(devaPro.hasAntiLeakageMode)
        assertTrue(devaPro.hasBinauralCrossfeed)

        val freeClip = DeviceProfileRegistry.findProfile("Huawei FreeClip")
        assertNotNull(freeClip)
        assertEquals(AudioFormFactor.OPEN_EAR_CLIP, freeClip!!.formFactor)
        assertFalse(freeClip.hasAnc)
        assertTrue(freeClip.hasDipoleBassRestorer)

        val openRun = DeviceProfileRegistry.findProfile("Shokz OpenRun Pro")
        assertNotNull(openRun)
        assertEquals(AudioFormFactor.BONE_CONDUCTION, openRun!!.formFactor)
        assertFalse(openRun.hasAnc)
        assertTrue(openRun.hasBoneConductionClarifier)
    }

    @Test
    fun resolver_categorizesKeywordsAccurately() {
        assertEquals(AudioFormFactor.STEM_IN_EAR, AudioFormFactorResolver.resolve(null, null, "realme Buds T300"))
        assertEquals(AudioFormFactor.STEM_HALF_IN_EAR, AudioFormFactorResolver.resolve(null, null, "Apple AirPods 3"))
        assertEquals(AudioFormFactor.BUD_BEAN_IN_EAR, AudioFormFactorResolver.resolve(null, null, "Galaxy Buds2 Pro"))
        assertEquals(AudioFormFactor.BUD_OPEN_RING, AudioFormFactorResolver.resolve(null, null, "Sony LinkBuds"))
        assertEquals(AudioFormFactor.OPEN_EAR_HOOK, AudioFormFactorResolver.resolve(null, null, "Powerbeats Pro"))
        assertEquals(AudioFormFactor.OPEN_EAR_CLIP, AudioFormFactorResolver.resolve(null, null, "Bose Ultra Open"))
        assertEquals(AudioFormFactor.NECKBAND, AudioFormFactorResolver.resolve(null, null, "realme Buds Wireless 3"))
        assertEquals(AudioFormFactor.BONE_CONDUCTION, AudioFormFactorResolver.resolve(null, null, "Shokz OpenRun"))
        assertEquals(AudioFormFactor.HEADSET_OVER_ON_EAR, AudioFormFactorResolver.resolve(null, null, "Sony WH-1000XM4"))
        assertEquals(AudioFormFactor.HEADSET_OPEN_BACK, AudioFormFactorResolver.resolve(null, null, "HiFiMAN Deva Open-Back"))
    }
}
