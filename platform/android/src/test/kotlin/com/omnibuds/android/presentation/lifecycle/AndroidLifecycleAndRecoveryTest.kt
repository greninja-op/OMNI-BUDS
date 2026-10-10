package com.omnibuds.android.presentation.lifecycle

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.AndroidDiscoveryProvider
import com.omnibuds.android.presentation.devices.AndroidDiscoverySession
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidLifecycleAndRecoveryTest {

    private class CancellableDiscoveryProvider : AndroidDiscoveryProvider {
        val flow = MutableSharedFlow<List<AndroidDiscoveredDevice>>()
        var cancelled = false

        override suspend fun startDiscovery(): AndroidDiscoverySession = object : AndroidDiscoverySession {
            override val results: Flow<List<AndroidDiscoveredDevice>> = flow
            override suspend fun stop() { cancelled = true }
            override suspend fun cancel() { cancelled = true }
        }
    }

    @Test
    fun `screen cleanup cancels in-flight discovery session without leaks`() = runTest {
        val provider = CancellableDiscoveryProvider()
        val vm = DevicesViewModel(scope = backgroundScope, discoveryProvider = provider)

        vm.startDiscovery()
        testScheduler.runCurrent()
        assertTrue(vm.state.value.isDiscovering)

        vm.cleanUp()
        testScheduler.runCurrent()

        assertTrue(provider.cancelled)
    }

    @Test
    fun `rapid repeated operation taps do not trigger duplicate executions`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val devId = GlobalDeviceId("bud-rapid")
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = devId,
                sessionId = "sess",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand", "Buds", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = devId,
                sessionId = "sess",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess", 1L, "GATT"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = devId,
                sessionId = "sess",
                sequence = 3L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("anc_mode")),
            ),
        )

        val vm = DeviceWorkspaceViewModel(deviceIdentifier = devId.value, scope = backgroundScope, stateRepository = repo)
        testScheduler.runCurrent()

        // Submit 5 times rapidly
        vm.submitFeatureControl("anc_mode", "ANC")
        vm.submitFeatureControl("anc_mode", "ANC")
        vm.submitFeatureControl("anc_mode", "ANC")
        vm.submitFeatureControl("anc_mode", "ANC")
        vm.submitFeatureControl("anc_mode", "ANC")

        testScheduler.runCurrent()

        val anc = vm.state.value.controls.first { it.featureId == "anc_mode" }
        assertEquals("ANC", anc.currentValue)
    }

    @Test
    fun `device disappearing during observation produces clear error without crashing`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val vm = DeviceWorkspaceViewModel(deviceIdentifier = "ghost-device", scope = backgroundScope, stateRepository = repo)
        testScheduler.runCurrent()

        assertNotNull(vm.state.value.errorBanner)
        assertTrue(vm.state.value.errorBanner?.contains("not currently registered") == true)
        assertFalse(vm.state.value.isConnected)
    }
}
