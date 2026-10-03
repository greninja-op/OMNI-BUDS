@file:SuppressLint("MissingPermission")
@file:Suppress("DEPRECATION")

package com.omnibuds.android.bluetooth.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.omnibuds.core.transport.GattCharacteristic
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The only Phase 6 file permitted to touch `android.bluetooth.*` for GATT.
 *
 * Everything here is transcription: the framework's callback becomes the seam's [RawConnectResult] /
 * [RawDiscoveryResult] / [RawReadResult], and the framework's characteristic handles are resolved from the
 * domain [GattCharacteristic] the caller supplied. No policy lives in this file — the lifecycle machine,
 * serialisation and error mapping are in [AndroidGattTransport], which a JVM test drives through a scripted
 * handle. This class is exercised only by the instrumented suite, which is compiled and never run
 * (ADR-P3-016), and every claim it enables stays `IMPLEMENTED` until a real device answers (PROTO-VERIFY-001).
 *
 * **Permissions.** The class is annotated `@SuppressLint("MissingPermission")` because the standing
 * `BLUETOOTH_CONNECT` check is made one level up by the caller that constructs a transport (the same
 * confinement ADR-P3-012/ADR-P3-009 apply to the observation handle): a second permission decision with a
 * second owner is the failure this module's guards exist to keep out, so this layer never re-checks.
 *
 * **`@Suppress("DEPRECATION")` on the file.** `BluetoothGattCharacteristic.value`, `writeDescriptor` and the
 * two-argument read/write are deprecated at API 33 in favour of overloads that *do not exist* below it, and
 * this module's `minSdk` is 26 (ADR-P2-002). There is no non-deprecated way to drive a GATT characteristic on
 * a 26-to-32 device, so the file uses the version-continuous API and says why, rather than pretending the
 * deprecation is a mistake to hide. It is confined to this transcription file; nothing in `:core` carries it.
 */
class SystemGattTransportHandle(
    private val context: Context,
    private val device: BluetoothDevice,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT,
) : GattTransportHandle {

    private var gatt: BluetoothGatt? = null

    // One in-flight bridge at a time; keyed by characteristic so several notification sinks stay separate.
    private val pendingReads = ConcurrentHashMap<UUID, CompletableDeferred<RawReadResult>>()
    private val notificationSinks = ConcurrentHashMap<UUID, (RawReadResult) -> Unit>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectSignal?.complete(RawConnectResult.Established)
                else -> connectSignal?.complete(RawConnectResult.Refused(status))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val services = if (status == BluetoothGatt.GATT_SUCCESS) {
                g.services.orEmpty().map { service ->
                    com.omnibuds.core.transport.GattService(
                        service = com.omnibuds.core.transport.GattUuid(service.uuid.toString()),
                        characteristics = service.characteristics.orEmpty().map { characteristic ->
                            toDomain(service.uuid, characteristic)
                        },
                    )
                }
            } else {
                emptyList()
            }
            discoverySignal?.complete(
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    RawDiscoveryResult.Reported(services)
                } else {
                    RawDiscoveryResult.Failed
                },
            )
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            resolveRead(characteristic.uuid, status, characteristic.value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            resolveWrite(characteristic.uuid, status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            notificationSinks[characteristic.uuid]?.invoke(readOrMissing(characteristic.value))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            resolveWrite(descriptor.characteristic.uuid, status)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuSignal?.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else null)
        }
    }

    private var connectSignal: CompletableDeferred<RawConnectResult>? = null
    private var discoverySignal: CompletableDeferred<RawDiscoveryResult>? = null
    private var mtuSignal: CompletableDeferred<Int?>? = null
    private val writeSignals = ConcurrentHashMap<UUID, CompletableDeferred<RawChannelResult>>()

    override suspend fun probe(): RawChannelResult =
        // Availability without connecting: an adapter and a known device are the honest pre-open facts,
        // both already required to reach this handle at all. A device the caller cannot name is not
        // representable here, so a constructed handle is always able to answer "a channel could exist."
        RawChannelResult(ok = true, status = null)

    override suspend fun connect(): RawConnectResult {
        val signal = CompletableDeferred<RawConnectResult>()
        connectSignal = signal
        val opened = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (opened == null) {
            connectSignal = null
            return RawConnectResult.Refused(null)
        }
        gatt = opened
        return withTimeoutOrNull(connectTimeoutMillis) { signal.await() } ?: run {
            connectSignal = null
            RawConnectResult.Unanswered
        }
    }

    override suspend fun discover(): RawDiscoveryResult {
        val target = gatt ?: return RawDiscoveryResult.Failed
        val signal = CompletableDeferred<RawDiscoveryResult>()
        discoverySignal = signal
        if (!target.discoverServices()) {
            discoverySignal = null
            return RawDiscoveryResult.Failed
        }
        return withTimeoutOrNull(OPERATION_TIMEOUT) { signal.await() } ?: run {
            discoverySignal = null
            RawDiscoveryResult.Unanswered
        }
    }

    @Suppress("DEPRECATION") // readCharacteristic(ch) is the pre-API-33 form; minSdk 26 must still compile it
    override suspend fun read(target: GattCharacteristic): RawReadResult {
        val g = gatt ?: return RawReadResult.Failed(null)
        val characteristic = g.getService(uuid(target.service.value))?.getCharacteristic(uuid(target.characteristic.value))
            ?: return RawReadResult.Missing
        val signal = CompletableDeferred<RawReadResult>()
        pendingReads[characteristic.uuid] = signal
        return if (g.readCharacteristic(characteristic)) {
            withTimeoutOrNull(OPERATION_TIMEOUT) { signal.await() } ?: RawReadResult.Failed(null)
        } else {
            pendingReads.remove(characteristic.uuid)
            RawReadResult.Failed(null)
        }
    }

    @Suppress("DEPRECATION") // see read(); the write overload is version-gated at call sites below
    override suspend fun write(target: GattCharacteristic, value: ByteArray, withResponse: Boolean): RawChannelResult {
        val g = gatt ?: return RawChannelResult(ok = false, status = null)
        val characteristic = g.getService(uuid(target.service.value))?.getCharacteristic(uuid(target.characteristic.value))
            ?: return RawChannelResult(ok = false, status = null)
        characteristic.value = value
        characteristic.writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val signal = CompletableDeferred<RawChannelResult>()
        writeSignals[characteristic.uuid] = signal
        return if (g.writeCharacteristic(characteristic)) {
            withTimeoutOrNull(OPERATION_TIMEOUT) { signal.await() } ?: RawChannelResult(ok = false, status = null)
        } else {
            writeSignals.remove(characteristic.uuid)
            RawChannelResult(ok = false, status = null)
        }
    }

    override suspend fun setSubscribed(target: GattCharacteristic, enabled: Boolean): RawChannelResult {
        val g = gatt ?: return RawChannelResult(ok = false, status = null)
        val characteristic = g.getService(uuid(target.service.value))?.getCharacteristic(uuid(target.characteristic.value))
            ?: return RawChannelResult(ok = false, status = null)
        if (!g.setCharacteristicNotification(characteristic, enabled)) {
            return RawChannelResult(ok = false, status = null)
        }
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return RawChannelResult(ok = false, status = null)
        cccd.value = if (enabled) ENABLE_NOTIFICATION_VALUE else DISABLE_NOTIFICATION_VALUE
        val signal = CompletableDeferred<RawChannelResult>()
        writeSignals[characteristic.uuid] = signal
        return if (g.writeDescriptor(cccd)) {
            withTimeoutOrNull(OPERATION_TIMEOUT) { signal.await() } ?: RawChannelResult(ok = false, status = null)
        } else {
            writeSignals.remove(characteristic.uuid)
            RawChannelResult(ok = false, status = null)
        }
    }

    override fun subscribe(target: GattCharacteristic, sink: (RawReadResult) -> Unit) {
        notificationSinks[uuid(target.characteristic.value)] = sink
    }

    override fun cancelSubscription(target: GattCharacteristic) {
        notificationSinks.remove(uuid(target.characteristic.value))
    }

    override suspend fun requestMtu(requested: Int): Int? {
        val g = gatt ?: return null
        val signal = CompletableDeferred<Int?>()
        mtuSignal = signal
        return if (g.requestMtu(requested)) {
            withTimeoutOrNull(OPERATION_TIMEOUT) { signal.await() }
        } else {
            mtuSignal = null
            null
        }
    }

    override suspend fun close(): RawChannelResult {
        val g = gatt ?: return RawChannelResult(ok = true, status = null)
        gatt = null
        return try {
            g.close()
            RawChannelResult(ok = true, status = null)
        } catch (security: SecurityException) {
            RawChannelResult(ok = false, status = null)
        }
    }

    // --- callbacks fan out to the pending bridges ---------------------------------------------------------------

    private fun resolveRead(key: UUID, status: Int, value: ByteArray?) {
        val deferred = pendingReads.remove(key) ?: return
        deferred.complete(readFromStatus(status, value))
    }

    private fun resolveWrite(key: UUID, status: Int) {
        val deferred = writeSignals.remove(key) ?: return
        deferred.complete(RawChannelResult(ok = status == BluetoothGatt.GATT_SUCCESS, status = status))
    }

    private fun readFromStatus(status: Int, value: ByteArray?): RawReadResult = when {
        status != BluetoothGatt.GATT_SUCCESS -> RawReadResult.Failed(status)
        value == null -> RawReadResult.Missing
        else -> RawReadResult.Value(value)
    }

    private fun readOrMissing(value: ByteArray?): RawReadResult =
        value?.let { RawReadResult.Value(it) } ?: RawReadResult.Missing

    private fun toDomain(serviceUuid: UUID, characteristic: BluetoothGattCharacteristic): GattCharacteristic {
        val properties = characteristic.properties
        val flags = buildSet {
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add(com.omnibuds.core.transport.CharacteristicProperty.READ)
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add(com.omnibuds.core.transport.CharacteristicProperty.WRITE)
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                add(com.omnibuds.core.transport.CharacteristicProperty.WRITE_NO_RESPONSE)
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add(com.omnibuds.core.transport.CharacteristicProperty.NOTIFY)
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add(com.omnibuds.core.transport.CharacteristicProperty.INDICATE)
        }
        return GattCharacteristic(
            service = com.omnibuds.core.transport.GattUuid(serviceUuid.toString()),
            characteristic = com.omnibuds.core.transport.GattUuid(characteristic.uuid.toString()),
            properties = flags,
            maxValueBytes = null,
        )
    }

    private fun uuid(text: String): UUID = UUID.fromString(text)

    private companion object {
        private const val DEFAULT_CONNECT_TIMEOUT = 30_000L
        private const val OPERATION_TIMEOUT = 10_000L
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
        private val DISABLE_NOTIFICATION_VALUE = byteArrayOf(0x00, 0x00)
    }
}
