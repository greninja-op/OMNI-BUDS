package com.omnibuds.desktop.devices

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability
import com.omnibuds.core.platform.desktop.DesktopDiscoveredDevice
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.presentation.devices.DevicesViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicesScreenTest {

    @Test
    fun adapterAvailabilityStatesHandledTruthfully() = runTest {
        val adapter = SimulatedDesktopAdapter(initialAvailability = DesktopBluetoothAvailability.AVAILABLE)
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        assertTrue(vm.state.value.availability.isUsable)
        assertTrue(vm.state.value.isReadyForDiscovery)
        assertNull(vm.state.value.permissionGuidance)

        adapter.setAvailability(DesktopBluetoothAvailability.DISABLED)
        testScheduler.runCurrent()
        assertEquals(DesktopBluetoothAvailability.DISABLED, vm.state.value.availability)
        assertFalse(vm.state.value.isReadyForDiscovery)
        assertNotNull(vm.state.value.permissionGuidance)

        adapter.setAvailability(DesktopBluetoothAvailability.PERMISSION_REQUIRED)
        testScheduler.runCurrent()
        assertEquals(DesktopBluetoothAvailability.PERMISSION_REQUIRED, vm.state.value.availability)
        assertTrue(vm.state.value.availability.isAuthorizationIssue)

        adapter.setAvailability(DesktopBluetoothAvailability.UNAVAILABLE)
        testScheduler.runCurrent()
        assertEquals(DesktopBluetoothAvailability.UNAVAILABLE, vm.state.value.availability)

        adapter.setAvailability(DesktopBluetoothAvailability.UNSUPPORTED)
        testScheduler.runCurrent()
        assertEquals(DesktopBluetoothAvailability.UNSUPPORTED, vm.state.value.availability)
    }

    @Test
    fun successfulDiscoveryPopulatesDevicesListWithoutDuplicates() = runTest {
        val adapter = SimulatedDesktopAdapter(initialAvailability = DesktopBluetoothAvailability.AVAILABLE)
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        assertTrue(vm.state.value.isEmptyList)

        vm.startDiscovery()
        testScheduler.runCurrent()
        assertTrue(vm.state.value.isDiscovering)

        // Emit device 1
        adapter.emitDiscoveredDevice(
            DesktopDiscoveredDevice(
                platformIdentifier = "dev-1",
                name = "OmniBuds Pro",
                bluetoothAddress = "AA:BB:CC:DD:EE:01",
                observedTransports = setOf(TransportKind.CLASSIC_BLUETOOTH),
                rssiDbm = -65,
                isConnectedAtOsLevel = true,
            ),
        )
        testScheduler.runCurrent()

        // Emit device 2
        adapter.emitDiscoveredDevice(
            DesktopDiscoveredDevice(
                platformIdentifier = "dev-2",
                name = "Studio Headphones",
                bluetoothAddress = "AA:BB:CC:DD:EE:02",
                observedTransports = setOf(TransportKind.BLE),
                rssiDbm = -78,
                isConnectedAtOsLevel = false,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(2, vm.state.value.discoveredDevices.size)
        assertEquals("OmniBuds Pro", vm.state.value.discoveredDevices[0].name)
        assertEquals("Studio Headphones", vm.state.value.discoveredDevices[1].name)

        // Re-emit device 1 with updated RSSI
        adapter.emitDiscoveredDevice(
            DesktopDiscoveredDevice(
                platformIdentifier = "dev-1",
                name = "OmniBuds Pro",
                bluetoothAddress = "AA:BB:CC:DD:EE:01",
                observedTransports = setOf(TransportKind.CLASSIC_BLUETOOTH),
                rssiDbm = -60,
                isConnectedAtOsLevel = true,
            ),
        )
        testScheduler.runCurrent()

        // Count must remain 2, RSSI updated
        assertEquals(2, vm.state.value.discoveredDevices.size)
        assertEquals(-60, vm.state.value.discoveredDevices[0].rssiDbm)

        vm.stopDiscovery()
        testScheduler.runCurrent()
        assertFalse(vm.state.value.isDiscovering)
    }

    @Test
    fun discoveryStartFailureDisplaysRecoverableError() = runTest {
        val adapter = SimulatedDesktopAdapter(initialAvailability = DesktopBluetoothAvailability.AVAILABLE)
        adapter.shouldFailDiscoveryStart = true
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        vm.startDiscovery()
        testScheduler.runCurrent()
        assertFalse(vm.state.value.isDiscovering)
        assertNotNull(vm.state.value.errorBanner)
        assertTrue(vm.state.value.isRecoverableError)

        vm.clearError()
        assertNull(vm.state.value.errorBanner)
    }

    @Test
    fun adapterLossDuringDiscoveryCancelsActiveDiscoverySession() = runTest {
        val adapter = SimulatedDesktopAdapter(initialAvailability = DesktopBluetoothAvailability.AVAILABLE)
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        vm.startDiscovery()
        testScheduler.runCurrent()
        assertTrue(vm.state.value.isDiscovering)

        // Radio killed
        adapter.setAvailability(DesktopBluetoothAvailability.DISABLED)
        testScheduler.runCurrent()
        assertFalse(vm.state.value.isDiscovering)
        assertNotNull(vm.state.value.errorBanner)
    }

    @Test
    fun deviceSelectionUpdatesSelectedIdentifier() = runTest {
        val adapter = SimulatedDesktopAdapter(initialAvailability = DesktopBluetoothAvailability.AVAILABLE)
        val vm = DevicesViewModel(adapter, backgroundScope)
        testScheduler.runCurrent()

        assertNull(vm.state.value.selectedDeviceIdentifier)
        vm.selectDevice("dev-target-123")
        assertEquals("dev-target-123", vm.state.value.selectedDeviceIdentifier)
    }
}
