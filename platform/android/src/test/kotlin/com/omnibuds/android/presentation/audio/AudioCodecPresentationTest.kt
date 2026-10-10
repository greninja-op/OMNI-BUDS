package com.omnibuds.android.presentation.audio

import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioCodecPresentationTest {

    @Test
    fun `unobservable audio state reports honest explanation without fabricating codec`() {
        val model = AudioPresentationModel.fromCoreState(AudioState.Unknown)

        assertFalse(model.isCodecObservable)
        assertFalse(model.isCodecSelectable)
        assertNull(model.activeCodecName)
        assertNotNull(model.unavailableReason)
        assertTrue(model.unavailableReason?.contains("not observable") == true)
        assertTrue(model.talkBackDescription.contains("not observable"))
    }

    @Test
    fun `explicitly observed codec is displayed accurately while remaining unselectable`() {
        val state = AudioState(
            route = ObservedValue(
                "A2DP_BLUETOOTH",
                ObservationProvenance("audio_manager", observedAtMillis = 1000L, receivedAtMillis = 1000L, sessionId = null, connectionGeneration = null, protocolVersion = null),
            ),
            codec = ObservedValue(
                "LDAC",
                ObservationProvenance("vendor_codec_probe", observedAtMillis = 1000L, receivedAtMillis = 1000L, sessionId = null, connectionGeneration = null, protocolVersion = null),
            ),
            codecObservable = true,
        )

        val model = AudioPresentationModel.fromCoreState(state)

        assertTrue(model.isCodecObservable)
        assertEquals("LDAC", model.activeCodecName)
        assertFalse(model.isCodecSelectable) // Never selectable on third-party Android
        assertNull(model.observableSampleRateHz) // Never fabricated
        assertTrue(model.talkBackDescription.contains("Active codec: LDAC"))
    }

    @Test
    fun `unavailable factory defaults produce honest model`() {
        val model = AudioPresentationModel.unavailable()

        assertFalse(model.isCodecObservable)
        assertFalse(model.isCodecSelectable)
        assertNull(model.activeCodecName)
    }
}
