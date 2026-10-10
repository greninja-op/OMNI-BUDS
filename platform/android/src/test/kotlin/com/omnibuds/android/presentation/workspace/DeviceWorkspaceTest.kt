package com.omnibuds.android.presentation.workspace

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceWorkspaceTest {

    private val testScope = TestScope()

    private fun createRepositoryWithDevice(
        deviceId: String,
        readyCapabilities: Set<String> = setOf("anc_mode", "eq_preset"),
    ): GlobalDeviceStateRepository {
        val repo = GlobalDeviceStateRepository()
        val gId = GlobalDeviceId(deviceId)
        runBlocking {
            repo.ingest(
                DeviceStateEvent.IdentityUpdated(
                    deviceId = gId,
                    sessionId = "sess-1",
                    sequence = 1L,
                    sourceId = "test",
                    identity = IdentityState.Identified(
                        manufacturerId = "Sony",
                        modelId = "WF-1000XM5",
                        confidence = "HIGH",
                        firmwareVersion = "2.0.1",
                    ),
                ),
            )
            repo.ingest(
                DeviceStateEvent.ConnectionChanged(
                    deviceId = gId,
                    sessionId = "sess-1",
                    sequence = 2L,
                    sourceId = "test",
                    connection = ConnectionState.Connected("sess-1", 1L, "GATT"),
                    generation = 1L,
                ),
            )
            repo.ingest(
                DeviceStateEvent.CapabilitiesUpdated(
                    deviceId = gId,
                    sessionId = "sess-1",
                    sequence = 3L,
                    sourceId = "test",
                    capabilities = CapabilityState.Ready(readyCapabilities),
                ),
            )
        }
        return repo
    }

    @Test
    fun `workspace distinguishes supported, unsupported, and unknown capability states`() = runTest {
        val repo = createRepositoryWithDevice("dev-1", readyCapabilities = setOf("anc_mode"))
        val vm = DeviceWorkspaceViewModel(
            deviceIdentifier = "dev-1",
            scope = testScope,
            stateRepository = repo,
        )
        testScope.testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isConnected)

        val ancCtrl = state.controls.firstOrNull { it.featureId == "anc_mode" }
        assertNotNull(ancCtrl)
        assertEquals(FeatureCapabilityKind.PERSISTENT, ancCtrl?.capabilityKind)
        assertTrue(ancCtrl?.isActionable == true)

        val spatialCtrl = state.controls.firstOrNull { it.featureId == "spatial_audio" }
        assertNotNull(spatialCtrl)
        assertEquals(FeatureCapabilityKind.UNSUPPORTED, spatialCtrl?.capabilityKind)
        assertFalse(spatialCtrl?.isActionable == true)
    }

    @Test
    fun `feature operation flow transitions through pending and observed states without false optimism`() = runTest {
        val repo = createRepositoryWithDevice("dev-2", readyCapabilities = setOf("anc_mode"))
        val vm = DeviceWorkspaceViewModel(
            deviceIdentifier = "dev-2",
            scope = testScope,
            stateRepository = repo,
        )
        testScope.testScheduler.advanceUntilIdle()

        vm.submitFeatureControl("anc_mode", "ANC")
        testScope.testScheduler.advanceUntilIdle()

        val updatedCtrl = vm.state.value.controls.first { it.featureId == "anc_mode" }
        assertEquals(ControlExecutionStatus.SUCCEEDED, updatedCtrl.executionStatus)
        assertEquals("ANC", updatedCtrl.currentValue)
    }

    @Test
    fun `unsupported feature rejection prevents operation execution`() = runTest {
        val repo = createRepositoryWithDevice("dev-3", readyCapabilities = setOf("anc_mode"))
        val vm = DeviceWorkspaceViewModel(
            deviceIdentifier = "dev-3",
            scope = testScope,
            stateRepository = repo,
        )
        testScope.testScheduler.advanceUntilIdle()

        vm.submitFeatureControl("spatial_audio", "ON")
        testScope.testScheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.errorBanner)
        assertTrue(vm.state.value.errorBanner?.contains("not actionable") == true)
    }

    @Test
    fun `tab navigation switches selected tab correctly`() {
        val vm = DeviceWorkspaceViewModel(deviceIdentifier = "dev-4", scope = testScope)
        assertEquals(WorkspaceTab.OVERVIEW, vm.state.value.selectedTab)

        vm.selectTab(WorkspaceTab.CONTROLS)
        assertEquals(WorkspaceTab.CONTROLS, vm.state.value.selectedTab)

        vm.selectTab(WorkspaceTab.BATTERY)
        assertEquals(WorkspaceTab.BATTERY, vm.state.value.selectedTab)

        vm.selectTab(WorkspaceTab.AUDIO)
        assertEquals(WorkspaceTab.AUDIO, vm.state.value.selectedTab)
    }

    @Test
    fun `error dismissal clears banner`() {
        val vm = DeviceWorkspaceViewModel(deviceIdentifier = "dev-5", scope = testScope)
        vm.submitFeatureControl("non_existent", "VALUE")

        vm.dismissError()
        assertNull(vm.state.value.errorBanner)
    }
}
