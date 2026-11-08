package com.omnibuds.android.hardware.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HearingTestManagerTest {

    private val hearingManager = HearingTestManager()

    @Test
    fun `verifies 7 standard pure-tone test frequencies`() {
        assertEquals(7, HearingTestManager.TEST_FREQUENCIES.size)
        assertEquals(125, HearingTestManager.TEST_FREQUENCIES[0])
        assertEquals(1000, HearingTestManager.TEST_FREQUENCIES[3])
        assertEquals(8000, HearingTestManager.TEST_FREQUENCIES[6])
    }

    @Test
    fun `generates 10-band compensation curve from audiogram threshold deficits`() {
        // Record moderate hearing deficit at 1kHz and 4kHz
        hearingManager.recordHearingResult(EarChannel.LEFT, 1000, -18f)
        hearingManager.recordHearingResult(EarChannel.RIGHT, 1000, -20f)
        hearingManager.recordHearingResult(EarChannel.LEFT, 4000, -15f)
        hearingManager.recordHearingResult(EarChannel.RIGHT, 4000, -17f)

        val gains = hearingManager.computeCompensationGains()
        assertEquals(10, gains.size)

        // Band 5 corresponds to 1kHz; Band 7 corresponds to 4kHz
        assertTrue(gains[5] > 0f)
        assertTrue(gains[7] > 0f)
    }

    @Test
    fun `generates distinct per-ear compensation curves for asymmetric hearing response`() {
        // Severe deficit in Left ear at 2kHz, normal in Right ear
        hearingManager.recordHearingResult(EarChannel.LEFT, 2000, -10f)
        hearingManager.recordHearingResult(EarChannel.RIGHT, 2000, -36f)

        val leftGains = hearingManager.computeCompensationGains(EarChannel.LEFT)
        val rightGains = hearingManager.computeCompensationGains(EarChannel.RIGHT)

        // Band 6 is 2kHz
        assertTrue(leftGains[6] > rightGains[6])
        assertEquals(0f, rightGains[6])
    }

    @Test
    fun `applies Apple Headphone Accommodations presets accurately`() {
        hearingManager.activePreset = HeadphoneAccommodationPreset.VOCAL_RANGE
        hearingManager.activeIntensity = AccommodationIntensity.MODERATE

        val gains = hearingManager.computeCompensationGains()
        // Vocal range elevates 500Hz, 1kHz, 2kHz
        assertEquals(4.0f, gains[5]) // 1kHz boosted by moderate 4dB
        assertEquals(4.0f, gains[6]) // 2kHz boosted by moderate 4dB
    }
}
