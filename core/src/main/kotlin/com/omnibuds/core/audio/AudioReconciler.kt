package com.omnibuds.core.audio

/**
 * Reconciles the independent audio observation sources into one honest snapshot.
 *
 * The engine sees the world through at least three lenses that update at
 * different times: Bluetooth profile proxies (A2DP/HFP/HSP/LE Audio connection
 * state), audio-device callbacks (which devices exist and which are active),
 * and platform capabilities (what the OS can offer at all). Each lens can be
 * stale, each can be silent, and they can contradict each other — a profile can
 * claim CONNECTED while no audio device with that address exists, or a device
 * can appear while its profile still reports UNKNOWN.
 *
 * Reconciliation rules (OB-P10-REQ-016), applied in order:
 *
 * 1. **Capability gate.** A profile the platform cannot offer (LE Audio on
 *    API < 33, a profile the stack does not implement) cannot be reported as
 *    connected or active, whatever a stale callback says. Its availability is
 *    forced to [ProfileAvailability.UNAVAILABLE] and its connection state to
 *    [AudioConnectionState.UNKNOWN]: the state is unknown *because the question
 *    does not apply*, and the availability field carries the reason. The gate
 *    applies only once capabilities have actually been determined; before
 *    that, unknown capabilities gate nothing.
 * 2. **Conflict preservation.** When profile state and device state disagree
 *    (profile CONNECTED, no matching device; device present, profile UNKNOWN),
 *    the reconciled connection state becomes [AudioConnectionState.UNKNOWN]
 *    and a diagnostic records both claims. The reconciler never picks the
 *    "more plausible" source — plausibility is how fabrications start.
 * 3. **Impossible-combination repair.** An [AudioConnectionState.ACTIVE]
 *    transport with no active device is downgraded to
 *    [AudioConnectionState.CONNECTED] with a diagnostic: active-without-a-device
 *    cannot be emitted, but the connection evidence itself was real.
 * 4. **Active-transport election.** [AudioTransportSnapshot.activeTransport] is
 *    set only when exactly one transport is ACTIVE and an active device is
 *    consistent with it. Zero or several ACTIVE transports, or an ACTIVE
 *    transport with no device, leaves the field null with a diagnostic. There
 *    is no priority order (LE Audio > A2DP > HFP) — priority without platform
 *    evidence is invention (OB-P10-REQ-014).
 * 5. **Stale-callback tolerance.** A device record older than [STALE_DEVICE_AGE_MILLIS]
 *    that contradicts a fresh profile read loses: the profile read wins and the
 *    device is dropped from the snapshot with a diagnostic. Freshness is the
 *    only tiebreaker the reconciler is allowed, because it is evidence, not
 *    preference.
 *
 * The reconciler is a pure function: same inputs, same snapshot, no clock
 * reads inside (timestamps arrive as parameters). That makes every rule above
 * unit-testable with scripted contradictions and no platform.
 */
object AudioReconciler {

    /** Older than this, a device record that contradicts a fresh read is dropped. */
    const val STALE_DEVICE_AGE_MILLIS: Long = 30_000L

    /**
     * Reconciles profile states, observed devices and capabilities into a snapshot.
     *
     * @param profileStates the latest per-profile observations, by transport kind.
     * @param devices the latest audio-device observations.
     * @param capabilities what the platform can offer.
     * @param activeTransportHint the transport the platform names as active, if any.
     * @param timestampMillis now, supplied by the caller for testability.
     * @param deviceTimestampsMillis per-device observation times, by platform id.
     */
    fun reconcile(
        profileStates: Map<AudioTransportKind, AudioProfileState>,
        devices: List<ObservedAudioDevice>,
        capabilities: AudioPlatformCapabilities,
        activeTransportHint: AudioTransportKind?,
        timestampMillis: Long,
        deviceTimestampsMillis: Map<Int, Long> = emptyMap(),
    ): AudioTransportSnapshot {
        val diagnostics = mutableListOf<AudioDiagnostic>()
        fun diagnose(code: String, message: String) {
            if (diagnostics.size < AudioTransportSnapshot.MAX_DIAGNOSTICS) {
                diagnostics += AudioDiagnostic(code, message, timestampMillis)
            }
        }

        // Rule 1: capability gate. Applies only once capabilities were actually
        // determined (apiLevel != null); unknown capabilities gate nothing.
        val capabilitiesKnown = capabilities.apiLevel != null
        val gatedProfiles = profileStates.mapValues { (kind, state) ->
            val unavailable = capabilitiesKnown && when (kind) {
                AudioTransportKind.LE_AUDIO -> !capabilities.leAudioApiAvailable
                AudioTransportKind.CLASSIC_A2DP -> !capabilities.a2dpSupported
                AudioTransportKind.HFP -> !capabilities.hfpSupported
                AudioTransportKind.HSP -> !capabilities.hspSupported
                AudioTransportKind.UNKNOWN -> false
            }
            if (unavailable && state.availability != ProfileAvailability.UNAVAILABLE) {
                diagnose(
                    "CAPABILITY_GATE",
                    "$kind reported ${state.connectionState} by ${state.source}, but the " +
                        "platform cannot offer it; availability forced to UNAVAILABLE and " +
                        "connection state to UNKNOWN.",
                )
                state.copy(
                    availability = ProfileAvailability.UNAVAILABLE,
                    connectionState = AudioConnectionState.UNKNOWN,
                    audioState = null,
                )
            } else {
                state
            }
        }

        // Rule 5: drop stale device records that contradict fresh profile reads.
        val freshDevices = devices.filter { device ->
            val observedAt = deviceTimestampsMillis[device.platformDeviceId]
            val isStale = observedAt != null && timestampMillis - observedAt > STALE_DEVICE_AGE_MILLIS
            if (!isStale) return@filter true
            val profileKind = deviceTypeToProfile(device.type)
            val profileState = profileKind?.let { gatedProfiles[it] }
            val contradicts = profileState != null &&
                profileState.connectionState == AudioConnectionState.DISCONNECTED
            if (contradicts) {
                diagnose(
                    "STALE_DEVICE_DROPPED",
                    "Device ${device.platformDeviceId} (${device.type}) observed " +
                        "${timestampMillis - observedAt}ms ago contradicts a fresh " +
                        "$profileKind DISCONNECTED; dropped.",
                )
                false
            } else {
                true
            }
        }

        // Rules 2+3: per-profile reconciliation against the device list.
        val reconciledProfiles = gatedProfiles.mapValues { (kind, state) ->
            reconcileProfile(kind, state, freshDevices, ::diagnose)
        }

        // Rule 4: active-transport election.
        val activeCandidates = reconciledProfiles.filterValues {
            it.connectionState == AudioConnectionState.ACTIVE
        }.keys.toList()
        val activeTransport = when {
            activeCandidates.isEmpty() -> null
            activeCandidates.size == 1 && activeTransportHint in listOf(null, activeCandidates.first()) -> {
                val candidate = activeCandidates.first()
                val hasDevice = freshDevices.any { it.isActive && deviceTypeToProfile(it.type) == candidate }
                if (hasDevice || freshDevices.none { it.isActive }) {
                    candidate
                } else {
                    diagnose(
                        "ACTIVE_WITHOUT_DEVICE",
                        "$candidate is ACTIVE but the active device belongs to another " +
                            "transport; activeTransport left null.",
                    )
                    null
                }
            }
            else -> {
                diagnose(
                    "ACTIVE_TRANSPORT_AMBIGUOUS",
                    "ACTIVE transports $activeCandidates with platform hint $activeTransportHint; " +
                        "no election without unambiguous evidence.",
                )
                null
            }
        }

        val activeOutput = freshDevices.firstOrNull {
            it.isActive && (it.direction == AudioDirection.OUTPUT || it.direction == AudioDirection.BIDIRECTIONAL)
        }
        val activeInput = freshDevices.firstOrNull {
            it.isActive && (it.direction == AudioDirection.INPUT || it.direction == AudioDirection.BIDIRECTIONAL)
        }

        return AudioTransportSnapshot(
            timestampMillis = timestampMillis,
            devices = freshDevices,
            profileStates = reconciledProfiles,
            activeTransport = activeTransport,
            activeOutputDevice = activeOutput,
            activeInputDevice = activeInput,
            capabilities = capabilities,
            diagnostics = diagnostics,
        )
    }

    private fun reconcileProfile(
        kind: AudioTransportKind,
        state: AudioProfileState,
        devices: List<ObservedAudioDevice>,
        diagnose: (String, String) -> Unit,
    ): AudioProfileState {
        // Rule 3: ACTIVE without any active device is impossible; keep the
        // connection evidence, drop the activity claim.
        if (state.connectionState == AudioConnectionState.ACTIVE && devices.none { it.isActive }) {
            diagnose(
                "ACTIVE_WITHOUT_DEVICE",
                "$kind reported ACTIVE by ${state.source} but no device is active; " +
                    "downgraded to CONNECTED.",
            )
            return state.copy(connectionState = AudioConnectionState.CONNECTED)
        }
        // Rule 2: profile claims CONNECTED/ACTIVE but no device of that family
        // exists at all — the sources disagree, so the state becomes UNKNOWN.
        if (
            state.connectionState == AudioConnectionState.CONNECTED ||
            state.connectionState == AudioConnectionState.ACTIVE
        ) {
            val familyPresent = devices.any { deviceTypeToProfile(it.type) == kind }
            if (!familyPresent && devices.isNotEmpty()) {
                diagnose(
                    "PROFILE_DEVICE_MISMATCH",
                    "$kind reported ${state.connectionState} by ${state.source} but no " +
                        "$kind audio device is present; connection state set to UNKNOWN.",
                )
                return state.copy(connectionState = AudioConnectionState.UNKNOWN)
            }
        }
        return state
    }

    /**
     * Maps an audio-device type to the profile family it belongs to, where the
     * mapping is unambiguous. Returns null for non-Bluetooth devices and for
     * types whose profile cannot be determined (SCO serves both HFP and HSP).
     */
    fun deviceTypeToProfile(type: AudioDeviceType): AudioTransportKind? = when (type) {
        AudioDeviceType.BLUETOOTH_A2DP -> AudioTransportKind.CLASSIC_A2DP
        AudioDeviceType.BLE_HEADSET -> AudioTransportKind.LE_AUDIO
        AudioDeviceType.BLE_SPEAKER -> AudioTransportKind.LE_AUDIO
        // SCO is the shared HFP/HSP call path: attributing it to one profile
        // would be the guess rule 2 forbids.
        AudioDeviceType.BLUETOOTH_SCO -> null
        AudioDeviceType.BUILTIN_SPEAKER,
        AudioDeviceType.BUILTIN_EARPIECE,
        AudioDeviceType.WIRED_HEADSET,
        AudioDeviceType.USB_DEVICE,
        AudioDeviceType.UNKNOWN,
        -> null
    }
}
