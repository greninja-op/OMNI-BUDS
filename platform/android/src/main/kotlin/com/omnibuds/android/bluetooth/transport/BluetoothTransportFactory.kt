package com.omnibuds.android.bluetooth.transport

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.omnibuds.core.transport.GattTransport
import com.omnibuds.core.transport.RfcommEndpoint
import com.omnibuds.core.transport.RfcommTransport
import com.omnibuds.core.transport.TransportResolver
import com.omnibuds.core.transport.UndeterminedTransportResolver

/**
 * The Android transport factory — the one place a `:core` transport interface becomes a mechanism here.
 *
 * It is a factory, not a connection: constructing a transport from a [BluetoothDevice] allocates no socket
 * and opens no GATT client; those happen only when a caller that has already decided to use the channel
 * calls `open()` (ADR-P6-008, prompt §12's "do not automatically connect to every candidate transport").
 * The device reaches the `System*Handle` and never the domain contract, so no address crosses into
 * `:core`'s view of the channel (SEC-ID-003).
 *
 * [resolver] defaults to the safe-unknown [UndeterminedTransportResolver]: this phase ships a selection
 * *contract*, not a policy, so the factory hands out the resolver that decides nothing (ADR-P6-005). A
 * later phase supplies one that orders candidates on evidence it can act on.
 *
 * **Manual construction, no DI framework**, matching [com.omnibuds.android.di]: a [Context] and a
 * [BluetoothDevice] are the only inputs, and a caller that wants to fake a channel swaps in its own
 * [GattTransportHandle] / [RfcommTransportHandle] rather than reaching for a mock framework — which is how
 * the JVM tests drive these classes with no radio.
 */
class BluetoothTransportFactory(
    private val context: Context,
    val resolver: TransportResolver = UndeterminedTransportResolver,
) {
    /** A GATT control channel bound to [device]; nothing is opened by this call. */
    fun gattTransport(device: BluetoothDevice): GattTransport =
        AndroidGattTransport(SystemGattTransportHandle(context.applicationContext, device))

    /** An RFCOMM control channel bound to [device] at [endpoint]; no socket is created by this call. */
    fun rfcommTransport(device: BluetoothDevice, endpoint: RfcommEndpoint): RfcommTransport =
        AndroidRfcommTransport(SystemRfcommTransportHandle(device, endpoint))
}
