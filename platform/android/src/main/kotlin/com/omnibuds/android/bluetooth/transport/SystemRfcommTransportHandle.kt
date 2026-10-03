@file:SuppressLint("MissingPermission")

package com.omnibuds.android.bluetooth.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.omnibuds.core.transport.RfcommEndpoint
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only Phase 6 file permitted to touch `android.bluetooth.BluetoothSocket`.
 *
 * Transcription again, mirroring [SystemGattTransportHandle]: the socket and its two streams are held here
 * and nowhere else, and [AndroidRfcommTransport] drives them through [RfcommTransportHandle] so its
 * lifecycle and error decisions are testable without a radio. Blocking socket calls run on the IO
 * dispatcher, so a read never occupies a caller's main thread (prompt §14's "no main-thread blocking").
 *
 * A known platform limit is stated rather than hidden: Android exposes client RFCOMM only by SDP **service
 * UUID** (`createRfcommSocketToServiceRecord`); there is no public API to dial a bare channel number from
 * an app. So an endpoint that names only a channel cannot be opened here, and [connect] reports that as a
 * refusal — it does not invent a channel-to-UUID resolution, which would be a service-discovery claim this
 * phase has no evidence for (PROTO-NOMAGIC-002).
 */
class SystemRfcommTransportHandle(
    private val device: BluetoothDevice,
    override val endpoint: RfcommEndpoint,
) : RfcommTransportHandle {

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun probe(): RawChannelResult =
        // A service-UUID endpoint is openable in principle; a channel-only endpoint is not reachable from an
        // app on Android, and that is a fact about the platform, reported as a refusal to use this transport.
        RawChannelResult(ok = endpoint.serviceUuid != null, status = null)

    override suspend fun connect(): RawConnectResult {
        val uuid = endpoint.serviceUuid ?: return RawConnectResult.Refused(null)
        return withContext(Dispatchers.IO) {
            try {
                val opened = device.createRfcommSocketToServiceRecord(toUuid(uuid.value))
                opened.connect()
                socket = opened
                input = opened.inputStream
                output = opened.outputStream
                RawConnectResult.Established
            } catch (io: java.io.IOException) {
                RawConnectResult.Refused(null)
            } catch (security: SecurityException) {
                RawConnectResult.Refused(null)
            }
        }
    }

    override suspend fun read(): RawReadResult = withContext(Dispatchers.IO) {
        val stream = input ?: return@withContext RawReadResult.Failed(null)
        try {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            val count = stream.read(buffer)
            when {
                count < 0 -> RawReadResult.Missing
                count == 0 -> RawReadResult.Value(ByteArray(0))
                else -> RawReadResult.Value(buffer.copyOf(count))
            }
        } catch (io: java.io.IOException) {
            RawReadResult.Failed(null)
        }
    }

    override suspend fun write(bytes: ByteArray): RawChannelResult = withContext(Dispatchers.IO) {
        val stream = output ?: return@withContext RawChannelResult(ok = false, status = null)
        try {
            stream.write(bytes)
            stream.flush()
            RawChannelResult(ok = true, status = null)
        } catch (io: java.io.IOException) {
            RawChannelResult(ok = false, status = null)
        }
    }

    override suspend fun close(): RawChannelResult = withContext(Dispatchers.IO) {
        val open = socket ?: return@withContext RawChannelResult(ok = true, status = null)
        socket = null
        input = null
        output = null
        runCatching { open.close() }
        RawChannelResult(ok = true, status = null)
    }

    private fun toUuid(text: String): UUID = UUID.fromString(text.removePrefix("0x").trim())

    private companion object {
        private const val READ_BUFFER_BYTES = 512
    }
}
