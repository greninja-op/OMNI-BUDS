package com.omnibuds.android.presentation.devices

import com.omnibuds.android.compat.BluetoothPlatformState
import com.omnibuds.android.compat.PermissionState
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevicesScreenTest {

    private class FakeDiscoveryProvider : AndroidDiscoveryProvider {
        val flow = MutableSharedFlow<List<AndroidDiscoveredDevice>>(replay = 1, extraBufferCapacity = 64)
        var wasStarted = false
        var wasStopped = false
        var wasCancelled = false

        override suspend fun startDiscovery(): AndroidDiscoverySession {
            wasStarted = true
            return object : AndroidDiscoverySession {
                override val results: Flow<List<AndroidDiscoveredDevice>> = flow
                override suspend fun stop() { wasStopped = true }
                override suspend fun cancel() { wasCancelled = true }
            }
        }
    }

    @Test
    fun `initial screen mode reflects available adapter and empty device list`() = runTest {
        val vm = DevicesViewModel(scope = backgroundScope)
        assertEquals(DevicesScreenMode.NO_DEVICES_FOUND, vm.state.value.screenMode)
        assertFalse(vm.state.value.isDiscovering)
        assertTrue(vm.state.value.discoveredDevices.isEmpty())
    }

    @Test
    fun `disabled bluetooth reports BLUETOOTH_DISABLED mode`() = runTest {
        val vm = DevicesViewModel(scope = backgroundScope)
        vm.updatePlatformState(
            BluetoothPlatformState(
                adapterPresent = true,
                adapterEnabled = false,
                connectPermission = PermissionState.GRANTED,
                scanPermission = PermissionState.GRANTED,
            ),
        )

        assertEquals(DevicesScreenMode.BLUETOOTH_DISABLED, vm.state.value.screenMode)
        assertNotNull(vm.state.value.permissionGuidance)
    }

    @Test
    fun `absent bluetooth hardware reports BLUETOOTH_UNAVAILABLE mode`() = runTest {
        val vm = DevicesViewModel(scope = backgroundScope)
        vm.updatePlatformState(
            BluetoothPlatformState(
                adapterPresent = false,
                adapterEnabled = false,
                connectPermission = PermissionState.GRANTED,
                scanPermission = PermissionState.GRANTED,
            ),
        )

        assertEquals(DevicesScreenMode.BLUETOOTH_UNAVAILABLE, vm.state.value.screenMode)
    }

    @Test
    fun `denied connect permission reports PERMISSION_DENIED mode respectfully`() = runTest {
        val vm = DevicesViewModel(scope = backgroundScope)
        vm.updatePlatformState(
            BluetoothPlatformState(
                adapterPresent = true,
                adapterEnabled = true,
                connectPermission = PermissionState.DENIED,
                scanPermission = PermissionState.GRANTED,
            ),
        )

        assertEquals(DevicesScreenMode.PERMISSION_DENIED, vm.state.value.screenMode)
        assertTrue(vm.state.value.permissionGuidance?.contains("BLUETOOTH_CONNECT") == true)
    }

    @Test
    fun `discovery start, observation emission, and deduplication`() = runTest {
        val fakeProvider = FakeDiscoveryProvider()
        val vm = DevicesViewModel(scope = backgroundScope, discoveryProvider = fakeProvider)

        vm.startDiscovery()
        testScheduler.runCurrent()

        assertTrue(fakeProvider.wasStarted)
        assertTrue(vm.state.value.isDiscovering)
        assertEquals(DevicesScreenMode.DISCOVERY_IN_PROGRESS, vm.state.value.screenMode)

        val dev1 = AndroidDiscoveredDevice(
            id = "AA:BB:CC:11:22:33",
            name = "Test Bud Pro",
            address = "AA:BB:CC:11:22:33",
            connectionState = ConnectionState.Connected("sess-1", 1L, "GATT"),
            transportKind = TransportKind.BLE,
        )
        fakeProvider.flow.emit(listOf(dev1))
        testScheduler.runCurrent()

        assertEquals(1, vm.state.value.discoveredDevices.size)
        assertEquals("Test Bud Pro", vm.state.value.discoveredDevices.first().name)

        // Duplicate emission with updated RSSI does not duplicate device in list
        val dev1Updated = dev1.copy(rssi = -65)
        fakeProvider.flow.emit(listOf(dev1Updated))
        testScheduler.runCurrent()

        assertEquals(1, vm.state.value.discoveredDevices.size)
        assertEquals(-65, vm.state.value.discoveredDevices.first().rssi)

        // Stopping discovery
        vm.stopDiscovery()
        testScheduler.runCurrent()

        assertTrue(fakeProvider.wasStopped)
        assertFalse(vm.state.value.isDiscovering)
        assertEquals(DevicesScreenMode.DEVICES_FOUND, vm.state.value.screenMode)
    }

    @Test
    fun `cancelled discovery terminates cleanly`() = runTest {
        val fakeProvider = FakeDiscoveryProvider()
        val vm = DevicesViewModel(scope = backgroundScope, discoveryProvider = fakeProvider)

        vm.startDiscovery()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isDiscovering)

        vm.cancelDiscovery()
        testScheduler.advanceUntilIdle()

        assertTrue(fakeProvider.wasCancelled)
        assertFalse(vm.state.value.isDiscovering)
    }

    @Test
    fun `device selection updates state`() = runTest {
        val vm = DevicesViewModel(scope = backgroundScope)
        assertNull(vm.state.value.selectedDeviceId)

        vm.selectDevice("bud-999")
        assertEquals("bud-999", vm.state.value.selectedDeviceId)
    }
}
