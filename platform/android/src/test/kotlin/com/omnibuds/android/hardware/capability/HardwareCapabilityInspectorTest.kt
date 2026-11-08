package com.omnibuds.android.hardware.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HardwareCapabilityInspectorTest {

    @Test
    fun `mid-range realme Buds T300 manifests exact official feature set`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "74:D7:13:C4:FF:EF",
            deviceName = "realme Buds T300",
        )

        assertEquals(DeviceTier.MID_RANGE, manifest.tier)
        assertEquals(AncTier.BASIC_30DB, manifest.ancTier)
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.TOUCH_SENSOR))
        assertFalse(manifest.supportedSensors.contains(PhysicalInputSensor.FORCE_PINCH_SENSOR))
        assertFalse(manifest.supportedSensors.contains(PhysicalInputSensor.STEM_SLIDE_VOLUME))

        // Official T300 features enabled
        assertTrue(manifest.hasSpatialAudio360)
        assertTrue(manifest.hasDolbyAtmos)
        assertTrue(manifest.hasDynamicBass)
        assertTrue(manifest.hasVolumeEnhancer)
        assertTrue(manifest.hasGameMode50ms)
        assertTrue(manifest.hasFindMyBuds)
        assertTrue(manifest.hasMindRelax)
        assertTrue(manifest.hasGoldenSoundCalibration)

        // Pro features not present on T300 hardware
        assertFalse(manifest.hasPersonalizedAnc)
        assertFalse(manifest.hasWindNoiseReduction)
        assertFalse(manifest.hasStemSlideVolume)
        assertFalse(manifest.hasMultipoint)
    }

    @Test
    fun `flagship realme Buds Air 6 Pro unlocks Pro tier features`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "94:DB:56:11:22:33",
            deviceName = "realme Buds Air 6 Pro",
        )

        assertEquals(DeviceTier.PRO_FLAGSHIP, manifest.tier)
        assertEquals(AncTier.PRO_MULTI_LEVEL_50DB, manifest.ancTier)
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.STEM_SLIDE_VOLUME))
        assertTrue(manifest.hasPersonalizedAnc)
        assertTrue(manifest.hasWindNoiseReduction)
        assertTrue(manifest.hasAmbientSlider)
        assertTrue(manifest.hasVocalEnhancement)
        assertTrue(manifest.hasConversationalAwareness)
        assertTrue(manifest.hasInEarDetection)
        assertTrue(manifest.hasMultipoint)
        assertTrue(manifest.hasHiResCodecSwitcher)
        assertTrue(manifest.hasFitTest)
    }

    @Test
    fun `Apple AirPods Pro 2 unlocks pinch sensor and stem slide volume`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "F0:D2:F1:AA:BB:CC",
            deviceName = "AirPods Pro (2nd generation)",
        )

        assertEquals(DeviceTier.PRO_FLAGSHIP, manifest.tier)
        assertTrue(manifest.isAppleAirPods)
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.FORCE_PINCH_SENSOR))
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.STEM_SLIDE_VOLUME))
        assertTrue(manifest.hasPinchSpeedControl)
        assertTrue(manifest.hasConversationalAwareness)
    }

    @Test
    fun `Samsung Galaxy Buds2 Pro detects edge tap accelerometer sensor`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "2C:FD:B3:01:02:03",
            deviceName = "Galaxy Buds2 Pro",
        )

        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.EDGE_TAP_ACCELEROMETER))
        assertTrue(manifest.hasEdgeTapVolume)
    }

    @Test
    fun `CMF Buds Pro 2 detects Case Smart Dial`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "BC:9A:C4:44:55:66",
            deviceName = "CMF Buds Pro 2",
        )

        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.CASE_SMART_DIAL))
        assertTrue(manifest.hasCaseSmartDial)
    }

    @Test
    fun `Nothing Ear 2024 flagship categorizes as Pro tier and pinch sensor`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "38:B4:49:12:34:56",
            deviceName = "Nothing Ear",
        )

        assertEquals(DeviceTier.PRO_FLAGSHIP, manifest.tier)
        assertEquals(AncTier.PRO_MULTI_LEVEL_50DB, manifest.ancTier)
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.FORCE_PINCH_SENSOR))
    }

    @Test
    fun `AirPods Pro 3 detects stem slide volume and pinch speed`() {
        val manifest = HardwareCapabilityInspector.inspect(
            device = null,
            address = "F0:D2:F1:11:22:33",
            deviceName = "AirPods Pro 3",
        )

        assertEquals(DeviceTier.PRO_FLAGSHIP, manifest.tier)
        assertTrue(manifest.isAppleAirPods)
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.FORCE_PINCH_SENSOR))
        assertTrue(manifest.supportedSensors.contains(PhysicalInputSensor.STEM_SLIDE_VOLUME))
    }
}
