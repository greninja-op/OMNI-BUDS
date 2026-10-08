package com.omnibuds.core.audio

import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The audio plane and the control plane are tracked independently and never
 * inferred from each other. (OB-P10-REQ-018)
 */
class AudioControlTopologyTest {

    @Test
    fun unverifiedTopologyIsTheInitialState() {
        assertFalse(AudioControlTopology.UNVERIFIED.verify())
        assertEquals(AudioTransportKind.UNKNOWN, AudioControlTopology.UNVERIFIED.audioTransport)
        assertEquals(TransportKind.UNKNOWN, AudioControlTopology.UNVERIFIED.controlTransport)
    }

    @Test
    fun verifyRequiresBothPlanesKnown() {
        assertFalse(
            AudioControlTopology(
                audioTransport = AudioTransportKind.CLASSIC_A2DP,
                controlTransport = TransportKind.UNKNOWN,
            ).verify(),
        )
        assertFalse(
            AudioControlTopology(
                audioTransport = AudioTransportKind.UNKNOWN,
                controlTransport = TransportKind.GATT,
            ).verify(),
        )
        assertTrue(
            AudioControlTopology(
                audioTransport = AudioTransportKind.CLASSIC_A2DP,
                controlTransport = TransportKind.GATT,
            ).verify(),
        )
    }

    @Test
    fun audioTransportDoesNotImplyControlTransport() {
        // The realistic split: audio over A2DP while control runs over GATT.
        // Neither plane's value constrains the other's.
        val topology = AudioControlTopology(
            audioTransport = AudioTransportKind.CLASSIC_A2DP,
            controlTransport = TransportKind.GATT,
        )
        assertTrue(topology.verify())
        assertEquals(AudioTransportKind.CLASSIC_A2DP, topology.audioTransport)
        assertEquals(TransportKind.GATT, topology.controlTransport)
    }

    @Test
    fun leAudioAsAudioTransportIsDistinctFromLeAudioAsControl() {
        // LE Audio the audio transport and LE_AUDIO the control channel are
        // different claims about different planes (master Phase 10 rule 4).
        val topology = AudioControlTopology(
            audioTransport = AudioTransportKind.LE_AUDIO,
            controlTransport = TransportKind.LE_AUDIO,
        )
        assertTrue(topology.verify())
        // The two planes are different types: an audio transport can never be
        // assigned where a control transport is expected, and vice versa. The
        // compiler enforces the separation this test exists to pin.
        val audio: AudioTransportKind = topology.audioTransport
        val control: TransportKind = topology.controlTransport
        assertEquals(AudioTransportKind.LE_AUDIO, audio)
        assertEquals(TransportKind.LE_AUDIO, control)
    }
}
