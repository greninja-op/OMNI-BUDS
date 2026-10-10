package com.omnibuds.desktop.workspace

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.presentation.workspace.ControlExecutionStatus
import com.omnibuds.desktop.presentation.workspace.DeviceWorkspaceViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceWorkspaceTest {

    private val deviceId = "dev-test-1"
    private val globalDeviceId = GlobalDeviceId(deviceId)

    private fun createSetup(scope: kotlinx.coroutines.CoroutineScope): Pair<GlobalDeviceStateRepository, DeviceWorkspaceViewModel> {
        val repo = GlobalDeviceStateRepository()
        val adapter = SimulatedDesktopAdapter()
        val vm = DeviceWorkspaceViewModel(
            deviceIdentifier = deviceId,
            stateRepository = repo,
            desktopAdapter = adapter,
            scope = scope,
        )
        return repo to vm
    }

    @Test
    fun identifiedAndConnectedDeviceWithCapabilitiesExposesActionableControls() = runTest {
        val (repo, vm) = createSetup(backgroundScope)

        // Ingest identified device
        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified(
                    manufacturerId = "Sony",
                    modelId = "WH-1000XM5",
                    confidence = "HIGH",
                    firmwareVersion = "2.0.1",
                ),
            ),
        )

        // Ingest connection and resolved compatible protocol
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected(
                    sessionId = "sess-1",
                    generation = 1L,
                    transport = "CLASSIC",
                ),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ProtocolResolved(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 3L,
                sourceId = "test",
                protocol = ProtocolState.Resolved(
                    protocolId = "sony.headphones",
                    protocolVersion = "1.0",
                    compatible = true,
                ),
            ),
        )

        // Ingest capabilities: ANC and Wear Detection supported
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 4L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(
                    capabilityIds = setOf("feature.anc", "feature.wear_detection"),
                ),
            ),
        )
        testScheduler.runCurrent()

        val state = vm.state.value
        assertNotNull(state.overview)
        assertTrue(state.overview!!.isIdentified)
        assertFalse(state.overview!!.isReadOnly)
        assertTrue(state.overview!!.connectionSessionState.isVendorControllable)

        val anc = state.controls.first { it.featureId == "feature.anc" }
        assertTrue(anc.isSupported)
        assertTrue(anc.isActionable)

        val eq = state.controls.first { it.featureId == "feature.equalizer_preset" }
        assertFalse(eq.isSupported)
        assertFalse(eq.isActionable)
    }

    @Test
    fun unknownDeviceStaysInReadOnlyModeWithAllControlsDisabled() = runTest {
        val (repo, vm) = createSetup(backgroundScope)

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Unknown,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected(
                    sessionId = "sess-1",
                    generation = 1L,
                    transport = "CLASSIC",
                ),
                generation = 1L,
            ),
        )
        testScheduler.runCurrent()

        val state = vm.state.value
        assertNotNull(state.overview)
        assertFalse(state.overview!!.isIdentified)
        assertTrue(state.overview!!.isReadOnly)
        assertTrue(state.hasUnidentifiedWarning)

        // All controls must be non-actionable
        for (ctrl in state.controls) {
            assertFalse(ctrl.isActionable)
        }
    }

    @Test
    fun controlSubmissionSetsPendingAndObservesOutcome() = runTest {
        val (repo, vm) = createSetup(backgroundScope)

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand", "Model", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-1", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ProtocolResolved(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 3L,
                sourceId = "test",
                protocol = ProtocolState.Resolved("test.proto", "1.0", compatible = true),
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 4L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("feature.anc")),
            ),
        )
        testScheduler.runCurrent()

        vm.submitControlOperation("feature.anc", "ANC")
        testScheduler.runCurrent()

        val state = vm.state.value
        val anc = state.controls.first { it.featureId == "feature.anc" }
        assertEquals("ANC", anc.observedValue)
        assertEquals(ControlExecutionStatus.SUCCEEDED, anc.executionStatus)
    }

    @Test
    fun operationFailureUpdatesStatusAndDisplaysError() = runTest {
        val (repo, vm) = createSetup(backgroundScope)

        repo.ingest(
            DeviceStateEvent.IdentityUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 1L,
                sourceId = "test",
                identity = IdentityState.Identified("Brand", "Model", "HIGH", "1.0"),
            ),
        )
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 2L,
                sourceId = "test",
                connection = ConnectionState.Connected("sess-1", 1L, "CLASSIC"),
                generation = 1L,
            ),
        )
        repo.ingest(
            DeviceStateEvent.ProtocolResolved(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 3L,
                sourceId = "test",
                protocol = ProtocolState.Resolved("test.proto", "1.0", compatible = true),
            ),
        )
        repo.ingest(
            DeviceStateEvent.CapabilitiesUpdated(
                deviceId = globalDeviceId,
                sessionId = "sess-1",
                sequence = 4L,
                sourceId = "test",
                capabilities = CapabilityState.Ready(setOf("feature.anc")),
            ),
        )
        testScheduler.runCurrent()

        vm.recordOperationFailure("feature.anc", "Firmware write rejected by peripheral", ControlExecutionStatus.REJECTED)
        testScheduler.runCurrent()

        val state = vm.state.value
        val anc = state.controls.first { it.featureId == "feature.anc" }
        assertEquals(ControlExecutionStatus.REJECTED, anc.executionStatus)
        assertNotNull(state.errorBanner)
        assertTrue(state.errorBanner!!.contains("Firmware write rejected"))
    }
}
