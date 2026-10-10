package com.omnibuds.desktop.multidevice

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.platform.desktop.DesktopDiscoveredDevice
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.presentation.devices.DevicesViewModel
import com.omnibuds.desktop.presentation.workspace.DeviceWorkspaceViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiDeviceIsolationTest {

    private val devAId = "mac-addr-01"
    private val devBId = "mac-addr-02"

    @Test
    fun simultaneousDeviceUpdatesInRepositoryRemainStrictlyIsolated() = runTest {
        val repo = GlobalDeviceStateRepository()
        val adapter = SimulatedDesktopAdapter()

        val vmA = DeviceWorkspaceViewModel(devAId, repo, adapter, backgroundScope)
        val vmB = DeviceWorkspaceViewModel(devBId, repo, adapter, backgroundScope)

        // Device A identified as Sony
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = GlobalDeviceId(devAId),
                sessionId = "sess-a",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Sony", "XM5", "HIGH", "1.0"),
            ),
        )

        // Device B identified as Bose
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = GlobalDeviceId(devBId),
                sessionId = "sess-b",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Bose", "QC45", "HIGH", "1.0"),
            ),
        )
        testScheduler.runCurrent()

        assertEquals("Sony", vmA.state.value.overview?.manufacturer)
        assertEquals("Bose", vmB.state.value.overview?.manufacturer)

        // Give Device A capabilities, not Device B
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = GlobalDeviceId(devAId),
                sessionId = "sess-a",
                sequence = 2L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("feature.anc")),
            ),
        )
        testScheduler.runCurrent()

        val ancA = vmA.state.value.controls.first { it.featureId == "feature.anc" }
        assertTrue(ancA.isSupported)

        val ancB = vmB.state.value.controls.first { it.featureId == "feature.anc" }
        assertFalse(ancB.isSupported)
    }

    @Test
    fun disconnectingDeviceADoesNotClearDeviceBState() = runTest {
        val repo = GlobalDeviceStateRepository()
        val adapter = SimulatedDesktopAdapter()

        val vmA = DeviceWorkspaceViewModel(devAId, repo, adapter, backgroundScope)
        val vmB = DeviceWorkspaceViewModel(devBId, repo, adapter, backgroundScope)

        // Connect both
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId(devAId),
                sessionId = "sess-a",
                sequence = 1L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-a", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId(devBId),
                sessionId = "sess-b",
                sequence = 1L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-b", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        testScheduler.runCurrent()

        assertTrue(vmA.state.value.isDeviceConnected)
        assertTrue(vmB.state.value.isDeviceConnected)

        // Disconnect A
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId(devAId),
                sessionId = "sess-a",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Disconnected,
                generation = 2L,
            ),
        )
        testScheduler.runCurrent()

        assertFalse(vmA.state.value.isDeviceConnected)
        // Device B remains connected!
        assertTrue(vmB.state.value.isDeviceConnected)
    }

    @Test
    fun duplicateDeviceNamesAreNeverMergedWhenIdentifiersDiffer() = runTest {
        val adapter = SimulatedDesktopAdapter()
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        vm.startDiscovery()
        testScheduler.runCurrent()

        adapter.emitDiscoveredDevice(
            DesktopDiscoveredDevice(
                platformIdentifier = "uuid-111",
                name = "AirPods Pro",
                bluetoothAddress = "11:22:33:44:55:66",
                observedTransports = setOf(TransportKind.CLASSIC_BLUETOOTH),
            ),
        )
        adapter.emitDiscoveredDevice(
            DesktopDiscoveredDevice(
                platformIdentifier = "uuid-222",
                name = "AirPods Pro",
                bluetoothAddress = "AA:BB:CC:DD:EE:FF",
                observedTransports = setOf(TransportKind.CLASSIC_BLUETOOTH),
            ),
        )
        testScheduler.runCurrent()

        assertEquals(2, vm.state.value.discoveredDevices.size)
        assertEquals("uuid-111", vm.state.value.discoveredDevices[0].platformIdentifier)
        assertEquals("uuid-222", vm.state.value.discoveredDevices[1].platformIdentifier)
    }
}
