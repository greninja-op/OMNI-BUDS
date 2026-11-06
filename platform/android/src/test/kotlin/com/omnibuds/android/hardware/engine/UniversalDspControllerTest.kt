package com.omnibuds.android.hardware.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UniversalDspControllerTest {

    private val controller = UniversalDspController()

    @Test
    fun `verifies 10-band ISO standard center frequencies`() {
        assertEquals(10, UniversalDspController.TARGET_FREQUENCIES_HZ.size)
        assertEquals(31, UniversalDspController.TARGET_FREQUENCIES_HZ[0])
        assertEquals(1000, UniversalDspController.TARGET_FREQUENCIES_HZ[5])
        assertEquals(16000, UniversalDspController.TARGET_FREQUENCIES_HZ[9])
    }

    @Test
    fun `interpolates exact frequency match correctly`() {
        val gains = floatArrayOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)

        // Exact match at 31Hz
        val gainAt31 = controller.interpolateGainForFrequency(31, gains)
        assertEquals(8f, gainAt31)

        // Exact match at 125Hz
        val gainAt125 = controller.interpolateGainForFrequency(125, gains)
        assertEquals(5f, gainAt125)
    }

    @Test
    fun `interpolates intermediate frequencies smoothly`() {
        val gains = floatArrayOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)

        // Intermediate frequency between 62Hz (7dB) and 125Hz (5dB): ~93Hz
        val gainMid = controller.interpolateGainForFrequency(93, gains)
        assertTrue(gainMid in 5.0f..7.0f)
    }

    @Test
    fun `clamps out of range frequencies gracefully`() {
        val gains = floatArrayOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)

        val gainSub10Hz = controller.interpolateGainForFrequency(10, gains)
        assertEquals(8f, gainSub10Hz)

        val gainSuper20kHz = controller.interpolateGainForFrequency(22000, gains)
        assertEquals(0f, gainSuper20kHz)
    }

    @Test
    fun `preset gains have exactly 10 bands`() {
        assertEquals(10, UniversalDspController.PRESET_FLAT.size)
        assertEquals(10, UniversalDspController.PRESET_BASS_BOOST.size)
        assertEquals(10, UniversalDspController.PRESET_TREBLE_BOOST.size)
        assertEquals(10, UniversalDspController.PRESET_VOCAL.size)
        assertEquals(10, UniversalDspController.PRESET_HARMAN.size)
        assertEquals(10, UniversalDspController.PRESET_WARMTH.size)
    }
}
