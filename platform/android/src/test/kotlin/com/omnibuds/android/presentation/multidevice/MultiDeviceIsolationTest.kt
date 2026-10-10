package com.omnibuds.android.presentation.multidevice

import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiDeviceIsolationTest {

    private val testScope = TestScope()

    @Test
    fun `two connected devices maintain strict isolation without state leakage`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val dev1 = GlobalDeviceId("DEV-11-22-33")
        val dev2 = GlobalDeviceId("DEV-44-55-66")

        // Register dev1
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = dev1,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("BrandA", "BudsA", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = dev1,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-1", 1L, "GATT"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = dev1,
                sessionId = "sess-1",
                sequence = 3L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("anc_mode")),
            ),
        )

        // Register dev2 with different capabilities
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = dev2,
                sessionId = "sess-2",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("BrandB", "BudsB", "HIGH", "2.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = dev2,
                sessionId = "sess-2",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-2", 1L, "RFCOMM"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = dev2,
                sessionId = "sess-2",
                sequence = 3L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("spatial_audio")),
            ),
        )

        val vm1 = DeviceWorkspaceViewModel(deviceIdentifier = dev1.value, scope = testScope, stateRepository = repo)
        val vm2 = DeviceWorkspaceViewModel(deviceIdentifier = dev2.value, scope = testScope, stateRepository = repo)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals("BudsA", vm1.state.value.overview?.displayName)
        assertEquals("BudsB", vm2.state.value.overview?.displayName)

        // dev1 has ANC actionable, spatial audio unsupported
        val dev1Anc = vm1.state.value.controls.first { it.featureId == "anc_mode" }
        val dev1Spatial = vm1.state.value.controls.first { it.featureId == "spatial_audio" }
        assertTrue(dev1Anc.isActionable)
        assertFalse(dev1Spatial.isActionable)

        // dev2 has spatial audio actionable, ANC unsupported
        val dev2Anc = vm2.state.value.controls.first { it.featureId == "anc_mode" }
        val dev2Spatial = vm2.state.value.controls.first { it.featureId == "spatial_audio" }
        assertFalse(dev2Anc.isActionable)
        assertTrue(dev2Spatial.isActionable)

        // Disconnecting dev1 does not disconnect dev2
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = dev1,
                sessionId = "sess-1",
                sequence = 4L,
                sourceId = "test",
                connection = ConnectionState.Disconnected,
                generation = 2L,
            ),
        )
        testScope.testScheduler.advanceUntilIdle()

        assertFalse(vm1.state.value.isConnected)
        assertTrue(vm2.state.value.isConnected)
    }

    @Test
    fun `devices with identical display names are disambiguated by unique identifier`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val devA = GlobalDeviceId("AA:11:22:33:44:55")
        val devB = GlobalDeviceId("BB:11:22:33:44:55")

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = devA,
                sessionId = null,
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Sony", "LinkBuds", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = devB,
                sessionId = null,
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Sony", "LinkBuds", "HIGH", "1.0"),
            ),
        )

        val vmA = DeviceWorkspaceViewModel(deviceIdentifier = devA.value, scope = testScope, stateRepository = repo)
        val vmB = DeviceWorkspaceViewModel(deviceIdentifier = devB.value, scope = testScope, stateRepository = repo)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals("LinkBuds", vmA.state.value.overview?.displayName)
        assertEquals("LinkBuds", vmB.state.value.overview?.displayName)
        assertNotEquals(vmA.state.value.deviceIdentifier, vmB.state.value.deviceIdentifier)
    }
}
