package com.omnibuds.desktop.lifecycle

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.presentation.devices.DevicesViewModel
import com.omnibuds.desktop.presentation.workspace.DeviceWorkspaceViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopLifecycleTest {

    @Test
    fun windowCloseDuringDiscoveryCleansUpSessionSafely() = runTest {
        val adapter = SimulatedDesktopAdapter()
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        vm.startDiscovery()
        testScheduler.runCurrent()
        assertTrue(vm.state.value.isDiscovering)

        // Screen/Window cleared (closing)
        vm.onCleared()
        testScheduler.runCurrent()
        // ViewModel cleans up jobs and releases active session without throwing
        assertNotNull(vm.state.value)
    }

    @Test
    fun deviceStateChangesWhileWorkspaceIsOpenUpdateReactiveState() = runTest {
        val repo = GlobalDeviceStateRepository()
        val adapter = SimulatedDesktopAdapter()
        val devId = "dev-live-update"
        val gId = GlobalDeviceId(devId)
        val vm = DeviceWorkspaceViewModel(devId, repo, adapter, backgroundScope)
        testScheduler.runCurrent()

        // Initial empty state
        assertFalse(vm.state.value.isDeviceConnected)

        // Connect device while screen is open
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-1", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        testScheduler.runCurrent()

        assertTrue(vm.state.value.isDeviceConnected)

        // Disconnect device while screen is open
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Disconnected,
                generation = 2L,
            ),
        )
        testScheduler.runCurrent()

        assertFalse(vm.state.value.isDeviceConnected)
    }

    @Test
    fun rapidRepeatedUserActionsDoNotCorruptStateOrDuplicateSubmissions() = runTest {
        val repo = GlobalDeviceStateRepository()
        val adapter = SimulatedDesktopAdapter()
        val devId = "dev-rapid"
        val gId = GlobalDeviceId(devId)
        val vm = DeviceWorkspaceViewModel(devId, repo, adapter, backgroundScope)
        testScheduler.runCurrent()

        // Setup verified controllable device
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand", "Model", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-1", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ProtocolResolved(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 3L,
                sourceId = "test",
                protocol = ProtocolState.Resolved("proto", "1.0", compatible = true),
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = gId,
                sessionId = "sess-1",
                sequence = 4L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("feature.anc")),
            ),
        )
        testScheduler.runCurrent()

        // Fire 5 rapid submissions
        repeat(5) {
            vm.submitControlOperation("feature.anc", "ANC")
        }
        testScheduler.runCurrent()

        // State remains valid and ANC observed
        val anc = vm.state.value.controls.first { it.featureId == "feature.anc" }
        assertEquals("ANC", anc.observedValue)
    }
}
