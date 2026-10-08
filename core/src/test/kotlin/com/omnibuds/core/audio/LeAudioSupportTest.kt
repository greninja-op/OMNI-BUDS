package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * LE Audio observability is a pure function of the API level — no framework
 * access, no guessing. (OB-P10-REQ-019)
 */
class LeAudioSupportTest {

    @Test
    fun api33AndAboveIsSupported() {
        assertEquals(LeAudioSupport.SUPPORTED, leAudioSupport(33))
        assertEquals(LeAudioSupport.SUPPORTED, leAudioSupport(34))
        assertEquals(LeAudioSupport.SUPPORTED, leAudioSupport(35))
    }

    @Test
    fun belowApi33IsTooOld() {
        // The project's minSdk is 26: every phone below 33 must get the honest
        // "too old" answer, never an inferred LE Audio state.
        assertEquals(LeAudioSupport.API_TOO_OLD, leAudioSupport(26))
        assertEquals(LeAudioSupport.API_TOO_OLD, leAudioSupport(30))
        assertEquals(LeAudioSupport.API_TOO_OLD, leAudioSupport(32))
    }

    @Test
    fun undeterminedApiLevelIsUnknownNotSupported() {
        // Unknown is not "probably new enough". An undetermined API level
        // must never license loading BluetoothLeAudio.
        assertEquals(LeAudioSupport.UNKNOWN, leAudioSupport(null))
    }

    @Test
    fun minApiLevelConstantIs33() {
        assertEquals(33, LE_AUDIO_MIN_API_LEVEL)
    }

    @Test
    fun supportedIsNotDeviceCapability() {
        // The verdict describes the OS, not any headset. This test pins the
        // function's signature: it takes only an API level, so no device fact
        // can leak into the answer.
        val verdict = leAudioSupport(35)
        assertTrue(verdict == LeAudioSupport.SUPPORTED)
        assertFalse(verdict == LeAudioSupport.API_TOO_OLD)
    }
}
