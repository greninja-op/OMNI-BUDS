package com.omnibuds.android.bluetooth.audio

import android.bluetooth.BluetoothProfile

/**
 * One audio device as raw primitives, extracted from the framework's
 * `AudioDeviceInfo` by the handle implementation.
 *
 * The mapping layer translates this — never the framework object — so the
 * translation stays unit-testable on a JVM with no audio hardware. The
 * extraction itself (calling `getType()`, `isSink()` and friends) is the
 * thin untestable edge, owned by the system handle implementation.
 */
data class RawAudioDevice(
    /** `AudioDeviceInfo.getId()`: platform-scoped, not a persistent identity. */
    val id: Int,
    /** `AudioDeviceInfo.getType()` integer. */
    val rawType: Int,
    /** `AudioDeviceInfo.getProductName()` as a string, or null. */
    val productName: String?,
    /** `AudioDeviceInfo.isSink()`. */
    val isSink: Boolean,
    /** `AudioDeviceInfo.isSource()`. */
    val isSource: Boolean,
)

/**
 * The narrow seam between the audio transport source and the Android framework.
 *
 * It speaks in the platform's own raw values — profile integers, primitive
 * device records — on purpose: translation happens in exactly one place
 * (`audio/mapping`), so a later Android version that changes what a number
 * means breaks one function rather than every consumer.
 *
 * The seam is read-only by construction (OB-P10-REQ-011). It offers no
 * `setCommunicationDevice`, no SCO start/stop, no routing change of any kind:
 * adding a write method here would put an unauthorised capability behind an
 * innocuous name, and Phase 10 authorises observation only.
 *
 * Profile reads are single binder sessions: [readRawProfiles] binds the
 * proxies, reads states and addresses, and releases the proxies before
 * returning. Proxies are never held across reads, so there is no IPC binding
 * to leak if the observer is torn down mid-read.
 *
 * LE Audio access goes through [LeAudioHandle], not through this interface:
 * `BluetoothLeAudio` does not exist below API 33, and an unconditional
 * reference from this interface would risk a class-loading failure on older
 * phones. The split keeps the guard in one place (ADR-P10-005).
 *
 * Every method may return null / empty when the platform has nothing to say.
 * Silence is data here, not an error: an empty device list can mean "no
 * devices", and only the source above this seam may decide what that means in
 * context.
 */
interface AudioTransportHandle {

    /**
     * One binder session's worth of profile observations, as raw values.
     *
     * @param states raw `BluetoothProfile` connection-state integers per
     *   profile integer (A2DP, HEADSET). Absent profiles were not answerable.
     * @param connectedAddresses Bluetooth addresses connected under each profile.
     * @param headsetAudioState raw `BluetoothHeadset` SCO audio state, or null
     *   when the platform did not report it through this session.
     */
    data class RawProfileRead(
        val states: Map<Int, Int?>,
        val connectedAddresses: Map<Int, List<String>>,
        val headsetAudioState: Int?,
    )

    /** Binds the classic profile proxies, reads them, releases them. */
    fun readRawProfiles(): RawProfileRead

    /** The current audio devices as raw primitives (see [RawAudioDevice]). */
    fun readRawAudioDevices(): List<RawAudioDevice>

    /**
     * The platform ids (`AudioDeviceInfo.getId()`) of the currently active
     * output and input devices, where the platform exposes them. Empty when
     * unreported.
     */
    fun readActiveDeviceIds(): Set<Int>

    /**
     * Registers for audio-device changes and calls [emit] on each change.
     *
     * Returns an [AutoCloseable] rather than a [com.omnibuds.core.platform.PlatformRegistration]
     * deliberately: `AudioManager.unregisterAudioDeviceCallback` is synchronous,
     * and this registration is consumed inside `callbackFlow.awaitClose`, whose
     * cleanup block cannot suspend. Closing is idempotent — teardown can run on
     * a cancellation path that already closed it — because a racing teardown
     * must be harmless rather than a leak or a crash.
     */
    fun openAudioDeviceChanges(emit: () -> Unit): AutoCloseable
}
