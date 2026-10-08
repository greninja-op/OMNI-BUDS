package com.omnibuds.android.bluetooth.audio

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.LeAudioSupport
import com.omnibuds.core.audio.leAudioSupport

/**
 * LE Audio access, isolated so the class loader never sees it below API 33.
 *
 * `android.bluetooth.BluetoothLeAudio` does not exist before API 33. Any class
 * that references it unconditionally risks a verification failure the moment
 * it is loaded on an older phone — even if the referencing method is never
 * called. This interface is therefore implemented by a class that lives in its
 * own file, references `BluetoothLeAudio` nowhere else, and is instantiated
 * only when [leAudioSupport] returns [LeAudioSupport.SUPPORTED]
 * (ADR-P10-005, OB-P10-REQ-019).
 *
 * The rest of the platform layer talks to this interface, never to the
 * framework class. On API < 33 there is no implementation registered at all;
 * the absence of an implementation *is* the "LE Audio unavailable" answer.
 */
interface LeAudioHandle {
    /** Raw `BluetoothLeAudio.getConnectionState()` values by device address. */
    fun readRawConnectionStates(): Map<String, Int?>

    /** Whether the LE Audio proxy is currently bound and answering. */
    val isBound: Boolean
}
