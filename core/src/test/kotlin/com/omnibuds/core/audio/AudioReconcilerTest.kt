package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reconciler's five rules, each proven with a scripted contradiction.
 * (OB-P10-REQ-016)
 */
class AudioReconcilerTest {

    private val capabilities = AudioPlatformCapabilities(
        leAudioApiAvailable = true,
        apiLevel = 34,
        a2dpSupported = true,
        hfpSupported = true,
        hspSupported = true,
        audioDeviceCallbackSupported = true,
    )

    private fun profileState(
        kind: AudioTransportKind,
        connection: AudioConnectionState,
        availability: ProfileAvailability = ProfileAvailability.AVAILABLE,
    ) = AudioProfileState(
        profile = kind,
        availability = availability,
        connectionState = connection,
        audioState = null,
        deviceAddress = null,
        source = "test",
    )

    private fun device(
        id: Int,
        type: AudioDeviceType,
        active: Boolean = false,
    ) = ObservedAudioDevice(
        platformDeviceId = id,
        type = type,
        productName = "Test Device",
        bluetoothAddress = null,
        direction = AudioDirection.OUTPUT,
        isActive = active,
        source = "test",
    )

    @Test
    fun rule1_capabilityGateForcesLeAudioUnavailableBelowApi33() {
        val oldCapabilities = capabilities.copy(leAudioApiAvailable = false, apiLevel = 30)
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.LE_AUDIO to profileState(
                    AudioTransportKind.LE_AUDIO,
                    AudioConnectionState.CONNECTED,
                ),
            ),
            devices = emptyList(),
            capabilities = oldCapabilities,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        val le = snapshot.profileStates[AudioTransportKind.LE_AUDIO]!!
        assertEquals(ProfileAvailability.UNAVAILABLE, le.availability)
        assertEquals(AudioConnectionState.UNKNOWN, le.connectionState)
        assertTrue(snapshot.diagnostics.any { it.code == "CAPABILITY_GATE" })
    }

    @Test
    fun rule1_gateDoesNotFireWhenCapabilitiesAreUnknown() {
        // UNKNOWN capabilities (apiLevel == null) must not gate anything: the
        // platform simply has not been asked yet.
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.CONNECTED,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BLUETOOTH_A2DP)),
            capabilities = AudioPlatformCapabilities.UNKNOWN,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        val a2dp = snapshot.profileStates[AudioTransportKind.CLASSIC_A2DP]!!
        assertEquals(ProfileAvailability.AVAILABLE, a2dp.availability)
        assertEquals(AudioConnectionState.CONNECTED, a2dp.connectionState)
        assertFalse(snapshot.diagnostics.any { it.code == "CAPABILITY_GATE" })
    }

    @Test
    fun rule2_conflictingSourcesPreserveUnknownAndRecordDiagnostic() {
        // Profile claims CONNECTED, but no A2DP device exists at all while
        // other devices do: the sources disagree, so the state becomes UNKNOWN.
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.CONNECTED,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BUILTIN_SPEAKER, active = true)),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        val a2dp = snapshot.profileStates[AudioTransportKind.CLASSIC_A2DP]!!
        assertEquals(AudioConnectionState.UNKNOWN, a2dp.connectionState)
        assertTrue(snapshot.diagnostics.any { it.code == "PROFILE_DEVICE_MISMATCH" })
    }

    @Test
    fun rule3_activeWithoutDeviceDowngradesToConnected() {
        // ACTIVE with no active device is impossible: keep the connection
        // evidence, drop the activity claim, say why.
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.ACTIVE,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BLUETOOTH_A2DP)),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        val a2dp = snapshot.profileStates[AudioTransportKind.CLASSIC_A2DP]!!
        assertEquals(AudioConnectionState.CONNECTED, a2dp.connectionState)
        assertTrue(snapshot.diagnostics.any { it.code == "ACTIVE_WITHOUT_DEVICE" })
    }

    @Test
    fun rule4_singleActiveTransportWithConsistentDeviceIsElected() {
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.ACTIVE,
                ),
                AudioTransportKind.HFP to profileState(
                    AudioTransportKind.HFP,
                    AudioConnectionState.CONNECTED,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BLUETOOTH_A2DP, active = true)),
            capabilities = capabilities,
            activeTransportHint = AudioTransportKind.CLASSIC_A2DP,
            timestampMillis = 1_000L,
        )
        assertEquals(AudioTransportKind.CLASSIC_A2DP, snapshot.activeTransport)
        assertEquals(1, snapshot.activeOutputDevice?.platformDeviceId)
    }

    @Test
    fun rule4_ambiguousActiveTransportsElectNothing() {
        // Two ACTIVE transports and no tiebreaker: null, with a diagnostic.
        // There is no priority order — inventing one would be fabrication.
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.ACTIVE,
                ),
                AudioTransportKind.LE_AUDIO to profileState(
                    AudioTransportKind.LE_AUDIO,
                    AudioConnectionState.ACTIVE,
                ),
            ),
            devices = listOf(
                device(1, AudioDeviceType.BLUETOOTH_A2DP, active = true),
                device(2, AudioDeviceType.BLE_HEADSET, active = true),
            ),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        assertNull(snapshot.activeTransport)
        assertTrue(snapshot.diagnostics.any { it.code == "ACTIVE_TRANSPORT_AMBIGUOUS" })
    }

    @Test
    fun rule5_staleDeviceContradictingFreshProfileIsDropped() {
        val now = 100_000L
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.DISCONNECTED,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BLUETOOTH_A2DP)),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = now,
            deviceTimestampsMillis = mapOf(1 to (now - AudioReconciler.STALE_DEVICE_AGE_MILLIS - 1)),
        )
        assertTrue(snapshot.devices.none { it.platformDeviceId == 1 })
        assertTrue(snapshot.diagnostics.any { it.code == "STALE_DEVICE_DROPPED" })
    }

    @Test
    fun rule5_freshDeviceIsKeptEvenWhenProfileDisagrees() {
        // Freshness is the only allowed tiebreaker: a fresh device that
        // contradicts the profile triggers rule 2 (UNKNOWN), not rule 5.
        val now = 100_000L
        val snapshot = AudioReconciler.reconcile(
            profileStates = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profileState(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.DISCONNECTED,
                ),
            ),
            devices = listOf(device(1, AudioDeviceType.BLUETOOTH_A2DP)),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = now,
            deviceTimestampsMillis = mapOf(1 to now),
        )
        assertEquals(1, snapshot.devices.size)
    }

    @Test
    fun scoDeviceIsNotAttributedToHfpOrHsp() {
        // BLUETOOTH_SCO serves both HFP and HSP; attributing it to one would
        // be the guess rule 2 forbids.
        assertNull(AudioReconciler.deviceTypeToProfile(AudioDeviceType.BLUETOOTH_SCO))
    }

    @Test
    fun diagnosticsAreBounded() {
        // Many contradictions at once must not grow the snapshot unboundedly.
        val profiles = AudioTransportKind.entries
            .filter { it != AudioTransportKind.UNKNOWN }
            .associateWith { kind ->
                profileState(kind, AudioConnectionState.ACTIVE)
            }
        val snapshot = AudioReconciler.reconcile(
            profileStates = profiles,
            devices = listOf(device(1, AudioDeviceType.BUILTIN_SPEAKER, active = true)),
            capabilities = capabilities,
            activeTransportHint = null,
            timestampMillis = 1_000L,
        )
        assertTrue(snapshot.diagnostics.size <= AudioTransportSnapshot.MAX_DIAGNOSTICS)
    }
}
