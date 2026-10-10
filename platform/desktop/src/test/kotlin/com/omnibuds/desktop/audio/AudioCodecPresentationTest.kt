package com.omnibuds.desktop.audio

import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.desktop.presentation.audio.AudioPresentationModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioCodecPresentationTest {

    @Test
    fun unobservableCodecReportsHonestOperatingSystemLimitation() {
        val state = AudioState(
            route = null,
            codec = null,
            codecObservable = false,
        )

        val model = AudioPresentationModel.fromCoreState(state)

        assertFalse(model.isCodecObservable)
        assertFalse(model.hasObservedCodec)
        assertNull(model.activeCodecName)
        assertNotNull(model.unavailableReason)
        assertTrue(model.unavailableReason!!.contains("not observable"))

        // Guaranteed: no invented bit depth or sample rate
        assertNull(model.observableBitDepth)
        assertNull(model.observableSampleRateHz)
    }

    @Test
    fun observedActiveCodecIsReportedWithoutAssumingUnsupportedCodecs() {
        val provenance = ObservationProvenance(
            sourceId = "desktop-audio-subsystem",
            observedAtMillis = System.currentTimeMillis(),
            receivedAtMillis = System.currentTimeMillis(),
            sessionId = "sess-1",
            connectionGeneration = 1L,
            protocolVersion = null,
        )

        val state = AudioState(
            route = ObservedValue("A2DP (Bluetooth)", provenance),
            codec = ObservedValue("AAC", provenance),
            codecObservable = true,
        )

        val model = AudioPresentationModel.fromCoreState(state)

        assertTrue(model.isCodecObservable)
        assertTrue(model.hasObservedCodec)
        assertEquals("AAC", model.activeCodecName)
        assertEquals("A2DP (Bluetooth)", model.audioTransport)
        assertNull(model.unavailableReason)
    }

    @Test
    fun observableWithoutObservedValueExplainsCodecPending() {
        val state = AudioState(
            route = null,
            codec = null,
            codecObservable = true,
        )

        val model = AudioPresentationModel.fromCoreState(state)

        assertTrue(model.isCodecObservable)
        assertFalse(model.hasObservedCodec)
        assertNull(model.activeCodecName)
        assertNotNull(model.unavailableReason)
        assertTrue(model.unavailableReason!!.contains("not been reported"))
    }
}
