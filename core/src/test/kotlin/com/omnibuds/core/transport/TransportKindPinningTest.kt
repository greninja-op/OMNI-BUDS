package com.omnibuds.core.transport

import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Each Bluetooth transport boundary answers for exactly one [TransportKind].
 *
 * The transport-boundaries document originally recorded this as an invariant that could not be
 * expressed in types, because an interface cannot constrain an inherited abstract property without
 * supplying an implementation. A default getter is not an implementation of behaviour - it is a
 * constant - so the rule is now enforceable. This test is what stops a later phase from quietly
 * re-pointing, say, `RfcommTransport` at `GATT` and inheriting the "assume GATT" defect the type
 * exists to prevent (ADR-P0-003, PROTO-XPORT-001).
 *
 * The readers below do nothing but expose each interface's own pinned value, so the assertions test
 * the interfaces rather than a copy of their documentation.
 */
class TransportKindPinningTest {

    @Test
    fun everyBoundaryPinsItsOwnKind() {
        assertEquals(TransportKind.BLE, BlePin.kind)
        assertEquals(TransportKind.GATT, GattPin.kind)
        assertEquals(TransportKind.CLASSIC_BLUETOOTH, ClassicPin.kind)
        assertEquals(TransportKind.RFCOMM, RfcommPin.kind)
        assertEquals(TransportKind.LE_AUDIO, LeAudioPin.kind)
    }

    @Test
    fun theFiveBoundariesAreDistinctAndNonePinsAnEscapeHatch() {
        val pinned = setOf(BlePin.kind, GattPin.kind, ClassicPin.kind, RfcommPin.kind, LeAudioPin.kind)

        assertEquals(5, pinned.size, "five boundaries must name five distinct transports")
        assertFalse(TransportKind.UNKNOWN in pinned, "a boundary may not report itself unidentified")
        assertFalse(TransportKind.VENDOR_SPECIFIC in pinned, "no vendor mechanism exists in Phase 2")
    }

    @Test
    fun aPinnedKindSurvivesBeingReadThroughTheSuperType() {
        // Read as a TransportContract: the value must still come from the type, never from a caller.
        val asContract: TransportContract = GattPin
        val asBluetooth: BluetoothTransport = GattPin

        assertEquals(TransportKind.GATT, asContract.kind)
        assertEquals(TransportKind.GATT, asBluetooth.kind)
    }
}

/**
 * Base for the pin readers only. Every member throws, because a reader that answered would be a
 * transport implementation in a phase that forbids one (Phase 2 prompt section 6); this file is test
 * source, where a double is allowed to exist (ADR-P1-013).
 */
private abstract class KindReader : BluetoothTransport {

    /** A pin reader never holds a channel, so it honestly reports itself closed. */
    override val isOpen: Boolean
        get() = false

    override suspend fun open(): Nothing = unsupported()
    override suspend fun close(): Nothing = unsupported()

    override suspend fun exchange(request: TransportRequest, timeoutMillis: Long): Nothing = unsupported()

    override suspend fun probeAvailability(): Nothing = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("a pin reader exposes TransportKind.kind and nothing else")
}

private object BlePin : KindReader(), BleTransport


private object GattPin : KindReader(), GattTransport


private object ClassicPin : KindReader(), ClassicTransport


private object RfcommPin : KindReader(), RfcommTransport


private object LeAudioPin : KindReader(), LeAudioTransport
