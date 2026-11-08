package com.omnibuds.android.hardware.audio

import com.omnibuds.android.hardware.engine.BinauralCrossfeedMode
import com.omnibuds.android.hardware.engine.NonTwsHardwareController
import com.omnibuds.android.hardware.engine.UniversalDspController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NonTwsDspTest {

    private lateinit var dsp: UniversalDspController
    private lateinit var nonTws: NonTwsHardwareController

    @BeforeEach
    fun setup() {
        dsp = UniversalDspController()
        nonTws = NonTwsHardwareController()
    }

    @Test
    fun openAirBassRestorer_appliesHarmonicMissingFundamentalBoost() {
        dsp.setEqualizerPreset("NATURE_BALANCE")

        dsp.setOpenAirBassRestorer(5)
        assertEquals(5, dsp.openAirBassRestorerLevel)

        // Reset to 0 turns off
        dsp.setOpenAirBassRestorer(0)
        assertEquals(0, dsp.openAirBassRestorerLevel)
    }

    @Test
    fun antiLeakagePrivateMode_limitsHighFrequencies() {
        assertFalse(dsp.isAntiLeakageModeEnabled)
        dsp.setAntiLeakageMode(true)
        assertTrue(dsp.isAntiLeakageModeEnabled)

        dsp.setAntiLeakageMode(false)
        assertFalse(dsp.isAntiLeakageModeEnabled)
    }

    @Test
    fun binauralCrossfeed_cyclesThroughPresets() {
        assertEquals(BinauralCrossfeedMode.OFF, dsp.binauralCrossfeedMode)

        dsp.setBinauralCrossfeed(BinauralCrossfeedMode.LOW)
        assertEquals(BinauralCrossfeedMode.LOW, dsp.binauralCrossfeedMode)

        dsp.setBinauralCrossfeed(BinauralCrossfeedMode.MEDIUM)
        assertEquals(BinauralCrossfeedMode.MEDIUM, dsp.binauralCrossfeedMode)

        dsp.setBinauralCrossfeed(BinauralCrossfeedMode.REFERENCE)
        assertEquals(BinauralCrossfeedMode.REFERENCE, dsp.binauralCrossfeedMode)
    }

    @Test
    fun boneConductionClarifier_attenuatesVibration_andBoostsVoice() {
        assertFalse(dsp.isBoneConductionClarifierEnabled)
        dsp.setBoneConductionClarifier(true)
        assertTrue(dsp.isBoneConductionClarifierEnabled)
    }

    @Test
    fun sensoryCrusherBass_scalesIntensityWithinBounds() {
        assertEquals(0, dsp.sensoryCrusherBassLevel)

        dsp.setSensoryCrusherBass(7)
        assertEquals(7, dsp.sensoryCrusherBassLevel)

        dsp.setSensoryCrusherBass(15) // clamps to 10
        assertEquals(10, dsp.sensoryCrusherBassLevel)

        dsp.setSensoryCrusherBass(-2) // clamps to 0
        assertEquals(0, dsp.sensoryCrusherBassLevel)
    }

    @Test
    fun ambientPassthrough_setsLevelAndVoiceFocus() {
        dsp.setAmbientPassthrough(18, voiceFocus = true)
        assertEquals(18, dsp.ambientPassthroughLevel)
        assertTrue(dsp.isVoicePassthroughEnabled)

        dsp.setAmbientPassthrough(5, voiceFocus = false)
        assertEquals(5, dsp.ambientPassthroughLevel)
        assertFalse(dsp.isVoicePassthroughEnabled)
    }

    @Test
    fun magneticHallSwitch_triggersPlayOnSeparate_andPauseOnClasp() {
        var playCalled = false
        var pauseCalled = false

        nonTws.onMediaPlayRequest = { playCalled = true }
        nonTws.onMediaPauseRequest = { pauseCalled = true }

        nonTws.isAutoPlayOnSeparateEnabled = true
        nonTws.isInstantPauseOnClaspEnabled = true

        nonTws.onMagneticSeparated()
        assertTrue(playCalled, "onMagneticSeparated must trigger play within 400ms")

        nonTws.onMagneticClasped()
        assertTrue(pauseCalled, "onMagneticClasped must trigger instant pause")
    }
}
