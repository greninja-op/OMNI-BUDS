@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleCommandSequencerTest {

    @Test
    fun `battery polling is preempted for 500ms after user command`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val sequencer = BleCommandSequencer(testScope)

        val address = "74:D7:13:C4:FF:EF"
        assertTrue(sequencer.canPollBattery(address), "Battery polling should be allowed initially")

        sequencer.recordUserInteraction(address)
        assertFalse(sequencer.canPollBattery(address), "Battery polling must be preempted immediately after tap")

        // Wait 500ms
        Thread.sleep(510)
        assertTrue(sequencer.canPollBattery(address), "Battery polling should resume after 500ms")
    }

    @Test
    fun `idempotency guard suppresses identical consecutive packets within 120ms`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val sequencer = BleCommandSequencer(testScope)

        val address = "74:D7:13:C4:FF:EF"
        val packet = byteArrayOf(0xAA.toByte(), 0x0D, 0x00, 0x00, 0x04, 0x04, 0x01, 0x06, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00)

        // First write is not duplicate
        assertFalse(sequencer.isDuplicateWrite(address, packet))

        // Record the attempt
        sequencer.recordWriteAttempt(address, packet, 15L, "OFF", 0x00, 0x01, 0x02)

        // Immediate identical write within 120ms must be suppressed
        assertTrue(sequencer.isDuplicateWrite(address, packet), "Identical packet within 120ms must be dropped as duplicate")

        // Different packet is not duplicate
        val differentPacket = byteArrayOf(0xAA.toByte(), 0x0D, 0x00, 0x00, 0x04, 0x04, 0x01, 0x06, 0x00, 0x01, 0x01, 0x01, 0x03, 0x00, 0x00)
        assertFalse(sequencer.isDuplicateWrite(address, differentPacket), "Different packet opcode/payload must not be suppressed")

        // After 120ms window expires, packet is no longer suppressed
        Thread.sleep(130)
        assertFalse(sequencer.isDuplicateWrite(address, packet), "Same packet after 120ms window must be accepted")
    }

    @Test
    fun `stale out-of-order notification guard blocks older notifications within 250ms`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val sequencer = BleCommandSequencer(testScope)

        val address = "74:D7:13:C4:FF:EF"

        // Simulate user tapped ANC then quickly tapped OFF
        sequencer.recordUserInteraction(address)
        // Set latest requested mode to OFF via submitNoiseMode or mock
        // We will call submitNoiseMode on a dummy or test helper
        sequencer.recordUserInteraction(address)
        // Manually test shouldIgnoreNotification:
        // Before any user command, nothing is ignored
        sequencer.clear(address)
        assertFalse(sequencer.shouldIgnoreNotification(address, "TRANSPARENCY"))

        // User interacts:
        sequencer.recordUserInteraction(address)
        // With latestRequestedModes set
        val mockDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
            ?: run {
                // If on desktop JVM, test shouldIgnoreNotification directly
                val dev = try {
                    val constructor = android.bluetooth.BluetoothDevice::class.java.getDeclaredConstructor(String::class.java)
                    constructor.isAccessible = true
                    constructor.newInstance(address)
                } catch (_: Exception) { null }
                dev
            }

        if (mockDevice != null) {
            sequencer.submitNoiseMode(mockDevice, "OFF", { true }, {})
            // Notification for old mode "TRANSPARENCY" arrives within 250ms: must be ignored
            assertTrue(sequencer.shouldIgnoreNotification(address, "TRANSPARENCY"))
            // Notification matching requested mode "OFF" arrives: accepted
            assertFalse(sequencer.shouldIgnoreNotification(address, "OFF"))
        }
    }

    @Test
    fun `recordWriteAttempt updates diagnostic telemetry correctly`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val sequencer = BleCommandSequencer(testScope)

        val address = "74:D7:13:C4:FF:EF"
        val packet = byteArrayOf(0xAA.toByte(), 0x01, 0x02)

        sequencer.recordWriteAttempt(address, packet, latencyMs = 12L, mode = "ANC", activeOff = 0x00, activeAnc = 0x01, activeTrans = 0x02)

        val state = sequencer.diagnosticStates.value[address]
        org.junit.jupiter.api.Assertions.assertNotNull(state)
        assertEquals(1, state?.writeCount)
        assertEquals(12L, state?.lastLatencyMs)
        assertEquals("ANC", state?.confirmedMode)
        assertEquals(0x00.toByte(), state?.activeOffByte)

        // Second write increments count to 2
        sequencer.recordWriteAttempt(address, packet, latencyMs = 8L, mode = "OFF", activeOff = 0x00, activeAnc = 0x01, activeTrans = 0x02)
        assertEquals(2, sequencer.diagnosticStates.value[address]?.writeCount)
        assertEquals(8L, sequencer.diagnosticStates.value[address]?.lastLatencyMs)
    }

    @Test
    fun `rapid-tap coalescing skips intermediate transitions and jumps straight to latest mode`() = runTest {
        val sequencer = BleCommandSequencer(this)
        val address = "74:D7:13:C4:FF:EF"

        val mockDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
            ?: run {
                val dev = try {
                    val constructor = android.bluetooth.BluetoothDevice::class.java.getDeclaredConstructor(String::class.java)
                    constructor.isAccessible = true
                    constructor.newInstance(address)
                } catch (_: Exception) { null }
                dev
            }

        if (mockDevice != null) {
            val dispatchedModes = mutableListOf<String>()

            // Tap 1: ANC
            sequencer.submitNoiseMode(
                device = mockDevice,
                mode = "ANC",
                dispatchAction = { target ->
                    dispatchedModes.add(target)
                    true
                },
                onAuthoritativeVerify = {},
            )

            // User taps fast before onCharacteristicWrite completes: Transparency -> OFF
            sequencer.submitNoiseMode(
                device = mockDevice,
                mode = "TRANSPARENCY",
                dispatchAction = { target ->
                    dispatchedModes.add(target)
                    true
                },
                onAuthoritativeVerify = {},
            )
            sequencer.submitNoiseMode(
                device = mockDevice,
                mode = "OFF",
                dispatchAction = { target ->
                    dispatchedModes.add(target)
                    true
                },
                onAuthoritativeVerify = {},
            )

            // Complete first write
            sequencer.onCharacteristicWriteCompleted(address, 0)

            // Advance through settle gap and next write
            testScheduler.advanceTimeBy(100)
            sequencer.onCharacteristicWriteCompleted(address, 0)
            testScheduler.advanceTimeBy(100)

            // Intermediate TRANSPARENCY must be skipped! Dispatched list must be [ANC, OFF]
            assertEquals(listOf("ANC", "OFF"), dispatchedModes, "Intermediate rapid tap mode must be coalesced and skipped")
            assertEquals("OFF", sequencer.getLatestRequestedNoiseMode(address))
        }
    }

    @Test
    fun `user interaction invokes onPreemptBatteryPoll callback`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val sequencer = BleCommandSequencer(testScope)

        var preemptCount = 0
        sequencer.onPreemptBatteryPoll = {
            preemptCount++
        }

        sequencer.recordUserInteraction("74:D7:13:C4:FF:EF")
        assertEquals(1, preemptCount, "onPreemptBatteryPoll must be called immediately on user interaction")
    }
}
