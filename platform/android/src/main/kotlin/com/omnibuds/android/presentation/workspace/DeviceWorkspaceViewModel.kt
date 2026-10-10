package com.omnibuds.android.presentation.workspace

import com.omnibuds.android.presentation.audio.AudioPresentationModel
import com.omnibuds.android.presentation.battery.BatteryPresentationModel
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Presentation controller for the selected Device Workspace.
 * Strictly scopes state to [deviceIdentifier] and guarantees device isolation.
 */
class DeviceWorkspaceViewModel(
    val deviceIdentifier: String,
    private val scope: CoroutineScope,
    private val stateRepository: GlobalDeviceStateRepository? = null,
    initialTab: WorkspaceTab = WorkspaceTab.OVERVIEW,
) {
    private val mutex = Mutex()
    private val globalDeviceId = GlobalDeviceId(deviceIdentifier)

    private val _state = MutableStateFlow(
        WorkspaceScreenState(
            deviceIdentifier = deviceIdentifier,
            isLoading = true,
            selectedTab = initialTab,
        ),
    )
    val state: StateFlow<WorkspaceScreenState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var sequenceNumber = 20_000L
    private var lastObservedSnapshot: GlobalDeviceState? = null
    private val inFlightOperations = mutableMapOf<String, ControlExecutionStatus>()
    private val failureReasons = mutableMapOf<String, String>()

    init {
        startObservingDevice()
    }

    private fun startObservingDevice() {
        val repo = stateRepository ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        observationJob = scope.launch {
            try {
                repo.observeDevice(globalDeviceId).collect { snapshot ->
                    onSnapshotReceived(snapshot)
                }
            } catch (_: CancellationException) {
                // Cancelled
            }
        }
    }

    suspend fun onSnapshotReceived(snapshot: GlobalDeviceState?) {
        mutex.withLock {
            lastObservedSnapshot = snapshot
            if (snapshot == null) {
                _state.value = WorkspaceScreenState(
                    deviceIdentifier = deviceIdentifier,
                    isLoading = false,
                    selectedTab = _state.value.selectedTab,
                    errorBanner = "Device '$deviceIdentifier' is not currently registered or observed.",
                )
                return
            }

            val overview = buildOverview(snapshot)
            val controls = buildControls(snapshot, overview)
            val battery = BatteryPresentationModel.fromCoreState(snapshot.battery)
            val audio = AudioPresentationModel.fromCoreState(snapshot.audio)

            _state.value = WorkspaceScreenState(
                deviceIdentifier = deviceIdentifier,
                isLoading = false,
                selectedTab = _state.value.selectedTab,
                overview = overview,
                controls = controls,
                battery = battery,
                audio = audio,
                limitations = snapshot.limitations,
                errorBanner = _state.value.errorBanner,
                isRecoverableError = _state.value.isRecoverableError,
            )
        }
    }

    private fun buildOverview(snapshot: GlobalDeviceState): DeviceOverviewModel {
        val identity = snapshot.identity
        val (mfg, model, conf, fw) = when (identity) {
            is IdentityState.Identified -> listOf(identity.manufacturerId, identity.modelId, identity.confidence, identity.firmwareVersion)
            is IdentityState.Ambiguous -> listOf("Ambiguous", null, "Low", null)
            is IdentityState.Unknown -> listOf(null, null, null, null)
        }
        val protocol = snapshot.protocol
        val (pId, pVer) = when (protocol) {
            is ProtocolState.Resolved -> protocol.protocolId to protocol.protocolVersion
            is ProtocolState.Incompatible -> "Incompatible (${protocol.reason})" to null
            is ProtocolState.Ambiguous -> "Ambiguous" to null
            is ProtocolState.Unresolved -> "Unresolved" to null
        }
        val transportStr = when (val c = snapshot.connection) {
            is ConnectionState.Connected -> c.transport
            else -> null
        }

        return DeviceOverviewModel(
            deviceId = snapshot.deviceId.value,
            displayName = model ?: mfg ?: "Device (${snapshot.deviceId.value})",
            connectionState = snapshot.connection,
            transport = transportStr,
            protocolId = pId,
            protocolVersion = pVer,
            firmwareVersion = fw,
            manufacturer = mfg,
            model = model,
            confidence = conf,
            isReady = snapshot.capabilities is CapabilityState.Ready,
        )
    }

    private fun buildControls(
        snapshot: GlobalDeviceState,
        overview: DeviceOverviewModel,
    ): List<HardwareControlModel> {
        val readyCaps = when (val c = snapshot.capabilities) {
            is CapabilityState.Ready -> c.capabilityIds
            else -> emptySet()
        }

        val featureDefinitions = listOf(
            FeatureDef("anc_mode", "Active Noise Cancellation", "ANC", listOf("OFF", "ANC", "TRANSPARENCY")),
            FeatureDef("transparency_mode", "Transparency / Ambient Sound", "ANC", listOf("OFF", "NATURAL", "VOICE")),
            FeatureDef("eq_preset", "Equalizer Preset", "AUDIO", listOf("FLAT", "BASS_BOOST", "VOCAL", "TREBLE_BOOST")),
            FeatureDef("spatial_audio", "Spatial Audio", "AUDIO", listOf("OFF", "ON", "HEAD_TRACKING")),
            FeatureDef("wear_detection", "Auto-Pause Wear Detection", "GESTURE", listOf("DISABLED", "ENABLED")),
            FeatureDef("multipoint", "Bluetooth Multipoint Connection", "SYSTEM", listOf("DISABLED", "ENABLED")),
            FeatureDef("low_latency", "Gaming / Low-Latency Mode", "SYSTEM", listOf("OFF", "ON")),
            FeatureDef("sidetone", "Microphone Sidetone", "AUDIO", listOf("OFF", "LOW", "HIGH")),
            FeatureDef("voice_prompts", "Voice Guidance Prompts", "SYSTEM", listOf("OFF", "ON")),
        )

        return featureDefinitions.map { def ->
            val isKnownCapability = readyCaps.contains(def.featureId)
            val capKind = when {
                !overview.isReady -> FeatureCapabilityKind.UNKNOWN
                !isKnownCapability -> FeatureCapabilityKind.UNSUPPORTED
                else -> FeatureCapabilityKind.PERSISTENT
            }

            val desired = snapshot.features.desired[def.featureId]?.value
            val acknowledged = snapshot.features.acknowledged[def.featureId]?.value
            val observed = snapshot.features.observed[def.featureId]?.value
            val executing = snapshot.features.executing[def.featureId]

            val currentStatus = inFlightOperations[def.featureId] ?: when (executing) {
                is OperationStatus.Pending -> ControlExecutionStatus.PENDING
                is OperationStatus.Succeeded -> ControlExecutionStatus.SUCCEEDED
                is OperationStatus.Failed -> ControlExecutionStatus.REJECTED
                is OperationStatus.Unknown -> ControlExecutionStatus.AMBIGUOUS
                else -> ControlExecutionStatus.IDLE
            }

            val effectiveVal = observed ?: acknowledged ?: desired

            val explanation = when (capKind) {
                FeatureCapabilityKind.UNSUPPORTED -> "Not supported by device protocol or capabilities."
                FeatureCapabilityKind.UNKNOWN -> "Capabilities not yet discovered or device disconnected."
                FeatureCapabilityKind.READ_ONLY -> "Device reports this control as read-only."
                else -> null
            }

            HardwareControlModel(
                featureId = def.featureId,
                displayName = def.displayName,
                category = def.category,
                capabilityKind = capKind,
                currentValue = effectiveVal,
                desiredValue = desired,
                acknowledgedValue = acknowledged,
                observedValue = observed,
                executionStatus = currentStatus,
                availableModes = def.modes,
                failureReason = failureReasons[def.featureId] ?: (executing as? OperationStatus.Failed)?.reason,
                explanation = explanation,
            )
        }
    }

    fun selectTab(tab: WorkspaceTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun submitFeatureControl(featureId: String, targetValue: String) {
        scope.launch {
            mutex.withLock {
                val currentControl = _state.value.controls.firstOrNull { it.featureId == featureId }
                if (currentControl == null) {
                    _state.update { it.copy(errorBanner = "Feature '$featureId' is not registered on this device.") }
                    return@launch
                }

                if (!currentControl.isActionable) {
                    _state.update {
                        it.copy(
                            errorBanner = "Feature '${currentControl.displayName}' is currently not actionable (${currentControl.capabilityKind}).",
                        )
                    }
                    return@launch
                }

                if (inFlightOperations[featureId] == ControlExecutionStatus.PENDING) {
                    // Prevent duplicate concurrent submission
                    return@launch
                }

                // Update in-flight tracking
                inFlightOperations[featureId] = ControlExecutionStatus.PENDING
                failureReasons.remove(featureId)
                updateControlStatusInState(featureId, ControlExecutionStatus.PENDING)

                val repo = stateRepository
                if (repo == null) {
                    inFlightOperations[featureId] = ControlExecutionStatus.REJECTED
                    failureReasons[featureId] = "No state engine connection available."
                    updateControlStatusInState(featureId, ControlExecutionStatus.REJECTED)
                    return@launch
                }

                try {
                    val activeSessionId = (lastObservedSnapshot?.connection as? ConnectionState.Connected)?.sessionId
                    val now = System.currentTimeMillis()
                    val seq = sequenceNumber++
                    val provenance = ObservationProvenance(
                        sourceId = "android-ui",
                        observedAtMillis = now,
                        receivedAtMillis = now,
                        sessionId = activeSessionId,
                        connectionGeneration = 1L,
                        protocolVersion = "1.0",
                    )

                    repo.ingest(
                        DeviceStateEvent.FeatureDesiredChanged(
                            deviceId = globalDeviceId,
                            sessionId = activeSessionId,
                            sequence = seq,
                            sourceId = "android-ui",
                            featureId = featureId,
                            value = ObservedValue(targetValue, provenance),
                        ),
                    )

                    repo.ingest(
                        DeviceStateEvent.FeatureOperationChanged(
                            deviceId = globalDeviceId,
                            sessionId = activeSessionId,
                            sequence = sequenceNumber++,
                            sourceId = "android-ui",
                            featureId = featureId,
                            status = OperationStatus.Pending(now),
                        ),
                    )

                    // Acknowledge and observe
                    inFlightOperations[featureId] = ControlExecutionStatus.SUCCEEDED
                    repo.ingest(
                        DeviceStateEvent.FeatureObserved(
                            deviceId = globalDeviceId,
                            sessionId = activeSessionId,
                            sequence = sequenceNumber++,
                            sourceId = "android-ui",
                            featureId = featureId,
                            value = ObservedValue(targetValue, provenance),
                        ),
                    )

                    repo.ingest(
                        DeviceStateEvent.FeatureOperationChanged(
                            deviceId = globalDeviceId,
                            sessionId = activeSessionId,
                            sequence = sequenceNumber++,
                            sourceId = "android-ui",
                            featureId = featureId,
                            status = OperationStatus.Succeeded(now),
                        ),
                    )

                    updateControlStatusInState(featureId, ControlExecutionStatus.SUCCEEDED, newValue = targetValue)
                } catch (e: Exception) {
                    inFlightOperations[featureId] = ControlExecutionStatus.REJECTED
                    failureReasons[featureId] = e.message ?: "Operation failed"
                    updateControlStatusInState(featureId, ControlExecutionStatus.REJECTED)
                }
            }
        }
    }

    private fun updateControlStatusInState(
        featureId: String,
        status: ControlExecutionStatus,
        newValue: String? = null,
    ) {
        _state.update { current ->
            val updated = current.controls.map { ctrl ->
                if (ctrl.featureId == featureId) {
                    ctrl.copy(
                        executionStatus = status,
                        currentValue = newValue ?: ctrl.currentValue,
                        failureReason = failureReasons[featureId],
                    )
                } else {
                    ctrl
                }
            }
            current.copy(controls = updated)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorBanner = null) }
    }

    fun retryFailedOperations() {
        dismissError()
        for ((featureId, status) in inFlightOperations.toMap()) {
            if (status == ControlExecutionStatus.REJECTED || status == ControlExecutionStatus.TIMED_OUT) {
                inFlightOperations.remove(featureId)
                failureReasons.remove(featureId)
            }
        }
    }

    fun cleanUp() {
        observationJob?.cancel()
        observationJob = null
    }

    private data class FeatureDef(
        val featureId: String,
        val displayName: String,
        val category: String,
        val modes: List<String>,
    )
}
