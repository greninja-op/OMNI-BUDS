package com.omnibuds.android.presentation.integration

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AndroidSurfaceIntegrationTest {

    @Test
    fun `surface coordinator resolves explicitly selected device across all surfaces`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val dev1 = GlobalDeviceId("dev-1")
        val dev2 = GlobalDeviceId("dev-2")

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = dev1,
                sessionId = "s1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand1", "Buds1", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = dev1,
                sessionId = "s1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("s1", 1L, "GATT"),
                generation = 1L,
            ),
        )

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = dev2,
                sessionId = "s2",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand2", "Buds2", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = dev2,
                sessionId = "s2",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("s2", 1L, "GATT"),
                generation = 1L,
            ),
        )

        val coordinator = AndroidSurfaceCoordinator(repository = repo)
        val allDevices = repo.allDevices.first()

        // Without explicit selection, first connected device is chosen
        val defaultTarget = coordinator.resolveActiveTarget(allDevices)
        assertNotNull(defaultTarget)

        // When explicitly selected, coordinator resolves that device
        coordinator.setSelectedDevice(dev2)
        val explicitTarget = coordinator.resolveActiveTarget(allDevices)
        assertEquals(dev2, explicitTarget)
    }

    @Test
    fun `empty repository yields null active target`() {
        val repo = GlobalDeviceStateRepository()
        val coordinator = AndroidSurfaceCoordinator(repository = repo)

        val target = coordinator.resolveActiveTarget(emptyMap())
        assertNull(target)
    }
}
