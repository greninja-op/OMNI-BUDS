package com.omnibuds.android.presentation.workspace

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Iteration 3: the workspace overview must never surface the internal
 * placeholder as a device name, and the discovery model's display name
 * must follow alias → name → neutral fallback.
 */
class DeviceOverviewIdentityTest {

    private fun repositoryWithIdentity(manufacturer: String, model: String?): GlobalDeviceStateRepository {
        val repo = GlobalDeviceStateRepository()
        runBlocking {
            repo.ingest(
                DeviceStateEvent.IdentityUpdated(
                    deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                    sequence = 1, sourceId = "test",
                    identity = IdentityState.Identified(manufacturer, model, "Low", null),
                ),
            )
        }
        return repo
    }

    @Test
    fun `placeholder manufacturer never becomes the display name`() {
        val repo = repositoryWithIdentity(manufacturer = "Unknown", model = null)
        val vm = DeviceWorkspaceViewModel("dev-1", CoroutineScope(Dispatchers.Unconfined), repo)
        val overview = vm.state.value.overview
        assertEquals("Bluetooth audio device", overview?.displayName)
        assertNull(overview?.manufacturer)
    }

    @Test
    fun `reported name becomes the display name`() {
        val repo = repositoryWithIdentity(manufacturer = "realme", model = "realme Buds T300")
        val vm = DeviceWorkspaceViewModel("dev-1", CoroutineScope(Dispatchers.Unconfined), repo)
        assertEquals("realme Buds T300", vm.state.value.overview?.displayName)
        assertEquals("realme", vm.state.value.overview?.manufacturer)
    }

    @Test
    fun `discovered device display name follows alias then name then fallback`() {
        val withBoth = AndroidDiscoveredDevice(
            id = "a", name = "boAt Rockerz 450", alias = "Boat", address = "a",
            connectionState = ConnectionState.Disconnected,
        )
        assertEquals("Boat", withBoth.displayName)
        assertEquals(
            "boAt Rockerz 450",
            withBoth.copy(alias = null).displayName,
        )
        assertEquals(
            "Bluetooth audio device",
            withBoth.copy(alias = null, name = null).displayName,
        )
        assertEquals(
            "Bluetooth audio device",
            withBoth.copy(alias = null, name = "Unknown").displayName,
        )
    }
}
