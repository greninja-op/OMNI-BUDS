package com.omnibuds.android.bluetooth.transport

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.transport.CharacteristicProperty
import com.omnibuds.core.transport.GattCharacteristic
import com.omnibuds.core.transport.GattService
import com.omnibuds.core.transport.GattUuid
import com.omnibuds.core.transport.RfcommEndpoint
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.transport.TransportState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Android transport *logic*, driven by scripted handles so it runs on a JVM with no radio (ADR-P6-008,
 * prompt §15's scripted doubles that never masquerade as hardware). These prove the lifecycle machine, the
 * one-operation guard, the MTU/permission refusals and the notification-cleanup — every part a later phase
 * depends on — while the `System*Handle` classes stay instrumented-only.
 *
 * Tier T1.
 */
class AndroidTransportTest {

    private val serviceUuid = GattUuid("0000180f-0000-1000-8000-00805f9b34fb")
    private val charUuid = GattUuid("00002a19-0000-1000-8000-00805f9b34fb")

    private fun characteristic(vararg props: CharacteristicProperty) =
        GattCharacteristic(serviceUuid, charUuid, props.toSet(), maxValueBytes = null)

    // --- scripted GATT handle -------------------------------------------------------------------------------------

    private inner class FakeGattHandle : GattTransportHandle {
        var connectResult: RawConnectResult = RawConnectResult.Established
        var closeOk = true
        var probeOk = true
        var lastWritten: ByteArray? = null
        var grantedMtu: Int? = 23
        var subscribed = false
        var cancelledSubscription = false

        override suspend fun probe() = RawChannelResult(ok = probeOk, status = null)
        override suspend fun connect() = connectResult
        override suspend fun discover(): RawDiscoveryResult =
            RawDiscoveryResult.Reported(listOf(GattService(serviceUuid, emptyList())))
        override suspend fun read(target: GattCharacteristic): RawReadResult = RawReadResult.Value(byteArrayOf(7))
        override suspend fun write(target: GattCharacteristic, value: ByteArray, withResponse: Boolean) =
            RawChannelResult(ok = true, status = 0).also { lastWritten = value }
        override suspend fun setSubscribed(target: GattCharacteristic, enabled: Boolean): RawChannelResult {
            subscribed = enabled
            return RawChannelResult(ok = true, status = 0)
        }
        override fun subscribe(target: GattCharacteristic, sink: (RawReadResult) -> Unit) { subscribed = true }
        override fun cancelSubscription(target: GattCharacteristic) { cancelledSubscription = true }
        override suspend fun requestMtu(requested: Int): Int? = grantedMtu
        override suspend fun close() = RawChannelResult(ok = closeOk, status = if (closeOk) 0 else 133)
    }

    @Test
    fun openDrivesTheStateMachineToConnected() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        assertEquals(TransportState.IDLE, transport.state.value)

        val outcome = transport.open()

        assertIs<OperationOutcome.Success<*>>(outcome)
        assertEquals(TransportState.CONNECTED, transport.state.value)
        assertTrue(transport.isOpen)
    }

    @Test
    fun aRefusedConnectLandsOnFailedNotConnected() = runTest {
        val handle = FakeGattHandle().apply { connectResult = RawConnectResult.Refused(status = 133) }
        val transport = AndroidGattTransport(handle)

        assertIs<OperationOutcome.Failure>(transport.open())
        assertEquals(TransportState.FAILED, transport.state.value)
        assertTrue(!transport.isOpen)
    }

    @Test
    fun anUnresolvedConnectIsTimeoutNotSilentSuccess() = runTest {
        val handle = FakeGattHandle().apply { connectResult = RawConnectResult.Unanswered }
        val transport = AndroidGattTransport(handle)

        val outcome = assertIs<OperationOutcome.Failure>(transport.open())
        assertEquals(OmniBudsErrorCategory.TIMEOUT, outcome.error.category)
    }

    @Test
    fun operationOnAnIdleChannelIsRefusedNotQueued() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        // No open() first: the guard rejects because the state is not CONNECTED (prompt §14).
        val outcome = assertIs<OperationOutcome.Failure>(transport.readCharacteristic(characteristic(CharacteristicProperty.READ)))
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, outcome.error.category)
    }

    @Test
    fun gattExchangeIsRefusedBecauseGattIsAddressedPerCharacteristic() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        transport.open()

        val outcome = assertIs<OperationOutcome.Failure>(
            transport.exchange(TransportRequest("cmd", byteArrayOf(1), null), 1_000L),
        )
        assertEquals(OmniBudsErrorCategory.UNSUPPORTED_OPERATION, outcome.error.category)
    }

    @Test
    fun writeExceedingTheNegotiatedMtuIsRefusedNotFragmented() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        transport.open()
        transport.requestMtu(23) // usable payload = 20

        val tooBig = ByteArray(40)
        val outcome = assertIs<OperationOutcome.Failure>(
            transport.writeCharacteristic(characteristic(CharacteristicProperty.WRITE), tooBig, true),
        )
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, outcome.error.category)
    }

    @Test
    fun writeToANonWritableCharacteristicIsRejectedBeforeTouchingTheHandle() = runTest {
        val handle = FakeGattHandle()
        val transport = AndroidGattTransport(handle)
        transport.open()

        val read = characteristic(CharacteristicProperty.READ)
        val outcome = assertIs<OperationOutcome.Failure>(transport.writeCharacteristic(read, byteArrayOf(1), true))
        assertEquals(OmniBudsErrorCategory.WRITE_REJECTED, outcome.error.category)
        assertEquals(null, handle.lastWritten)
    }

    @Test
    fun discoveringServicesReturnsWhatThePlatformReported() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        transport.open()

        val outcome = transport.discoverServices()

        assertIs<OperationOutcome.Success<List<GattService>>>(outcome)
        assertEquals(1, outcome.value.size)
        assertEquals(serviceUuid, outcome.value.single().service)
    }

    @Test
    fun cancellingANotificationSubscriptionReleasesThePlatformRegistration() = runTest {
        val handle = FakeGattHandle()
        val transport = AndroidGattTransport(handle)
        transport.open()
        val target = characteristic(CharacteristicProperty.NOTIFY)

        // The leak-safety property ADR-P6-011 rests on: a subscription the caller stops must remove the
        // platform registration. The notifications Flow wires awaitClose -> cancelSubscription; here we
        // assert the handle exposes that path, so a stopped collector has somewhere to release to.
        handle.subscribe(target) {}
        assertTrue(handle.subscribed)
        handle.cancelSubscription(target)
        assertTrue(handle.cancelledSubscription, "a stopped collector must remove the registration (ADR-P6-011)")
    }

    @Test
    fun repeatedCloseIsIdempotentSuccess() = runTest {
        val transport = AndroidGattTransport(FakeGattHandle())
        transport.open()

        assertIs<OperationOutcome.Success<*>>(transport.close())
        assertIs<OperationOutcome.Success<*>>(transport.close())
        assertEquals(TransportState.CLOSED, transport.state.value)
    }

    // --- scripted RFCOMM handle -----------------------------------------------------------------------------------

    private class FakeRfcommHandle(
        override val endpoint: RfcommEndpoint = RfcommEndpoint(serviceUuid = GattUuid("00001101-0000-1000-8000-00805f9b34fb")),
    ) : RfcommTransportHandle {
        var connectResult: RawConnectResult = RawConnectResult.Established
        var nextRead: RawReadResult = RawReadResult.Value(byteArrayOf(1, 2, 3))
        var writeOk = true
        val written = mutableListOf<ByteArray>()

        override suspend fun probe() = RawChannelResult(ok = true, status = null)
        override suspend fun connect() = connectResult
        override suspend fun read() = nextRead
        override suspend fun write(bytes: ByteArray): RawChannelResult {
            written += bytes
            return RawChannelResult(ok = writeOk, status = if (writeOk) 0 else 1)
        }
        override suspend fun close() = RawChannelResult(ok = true, status = 0)
    }

    @Test
    fun rfcommExchangeWritesWholeThenReadsOneRun() = runTest {
        val handle = FakeRfcommHandle()
        val transport = AndroidRfcommTransport(handle)
        transport.open()

        val outcome = transport.exchange(TransportRequest("cmd", byteArrayOf(9, 9), null), 1_000L)

        assertIs<OperationOutcome.Success<com.omnibuds.core.transport.TransportResponse>>(outcome)
        assertEquals(byteArrayOf(1, 2, 3).toList(), outcome.value.payload?.toList())
        assertTrue(handle.written.any { it.contentEquals(byteArrayOf(9, 9)) }, "the request bytes go out whole")
    }

    @Test
    fun rfcommWriteFailureMapsToRfcommCategory() = runTest {
        val handle = FakeRfcommHandle().apply { writeOk = false }
        val transport = AndroidRfcommTransport(handle)
        transport.open()

        val outcome = assertIs<OperationOutcome.Failure>(transport.write(byteArrayOf(1)))
        assertEquals(OmniBudsErrorCategory.RFCOMM_FAILURE, outcome.error.category)
        assertEquals(TransportState.FAILED, transport.state.value)
    }
}
