package com.omnibuds.desktop.presentation.workspace

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.DeviceStateEvent
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.platform.desktop.DesktopBluetoothAdapter
import com.omnibuds.core.platform.desktop.DesktopConnectionSessionState
import com.omnibuds.desktop.presentation.audio.AudioPresentationModel
import com.omnibuds.desktop.presentation.battery.BatteryPresentationModel
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
 * Strictly scopes state to [deviceIdentifier] and prevents operation leakage across devices.
 */
class DeviceWorkspaceViewModel(
    val deviceIdentifier: String,
    private val stateRepository: GlobalDeviceStateRepository,
    private val desktopAdapter: DesktopBluetoothAdapter,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val globalDeviceId = GlobalDeviceId(deviceIdentifier)

    private val _state = MutableStateFlow(
        WorkspaceScreenState(
            deviceIdentifier = deviceIdentifier,
            isLoading = true,
        ),
    )
    val state: StateFlow<WorkspaceScreenState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var sequenceNumber = 10_000L
    private var lastObservedSnapshot: GlobalDeviceState? = null
    private val inFlightOperations = mutableMapOf<String, ControlExecutionStatus>()
    private val failureReasons = mutableMapOf<String, String>()

    init {
        startObservingDevice()
    }

    private fun startObservingDevice() {
        observationJob = scope.launch {
            try {
                stateRepository.observeDevice(globalDeviceId).collect { deviceSnapshot ->
                    onSnapshotReceived(deviceSnapshot)
                }
            } catch (_: CancellationException) {
                // Expected when scope or screen cancelled
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
        val isIdentified = identity is IdentityState.Identified
        val isReadOnly = !isIdentified

        val manufacturer = when (identity) {
            is IdentityState.Identified -> identity.manufacturerId
            else -> null
        }
        val model = when (identity) {
            is IdentityState.Identified -> identity.modelId
            else -> null
        }
        val firmware = when (identity) {
            is IdentityState.Identified -> identity.firmwareVersion
            else -> null
        }
        val confidence = when (identity) {
            is IdentityState.Identified -> identity.confidence
            is IdentityState.Ambiguous -> "Ambiguous (${identity.reason})"
            IdentityState.Unknown -> "Unknown (Read-Only Mode)"
        }

        val isConnectedOs = snapshot.connection is ConnectionState.Connected
        val hasTransportSession = isConnectedOs && snapshot.protocol is ProtocolState.Resolved
        val hasVendorSession = hasTransportSession && (snapshot.protocol as ProtocolState.Resolved).compatible

        val sessionState = DesktopConnectionSessionState(
            platformIdentifier = deviceIdentifier,
            isConnectedAtOsLevel = isConnectedOs,
            activeTransportKind = if (hasTransportSession) TransportKind.CLASSIC_BLUETOOTH else null,
            hasActiveTransportSession = hasTransportSession,
            hasVendorProtocolSession = hasVendorSession,
        )

        val capabilityStatusDesc = when (snapshot.capabilities) {
            is CapabilityState.Ready -> "Verified (${(snapshot.capabilities as CapabilityState.Ready).capabilityIds.size} features)"
            is CapabilityState.Discovering -> "Discovering..."
            is CapabilityState.Failed -> "Discovery Failed: ${(snapshot.capabilities as CapabilityState.Failed).reason}"
            CapabilityState.NotDiscovered -> "Not Discovered"
        }

        return DeviceOverviewModel(
            deviceIdentifier = deviceIdentifier,
            displayName = model ?: "Device[${deviceIdentifier.take(8)}]",
            manufacturer = manufacturer,
            model = model,
            firmwareVersion = firmware,
            identityConfidence = confidence,
            isIdentified = isIdentified,
            connectionSessionState = sessionState,
            isReadOnly = isReadOnly,
            capabilityDiscoveryStatus = capabilityStatusDesc,
        )
    }

    private fun buildControls(
        snapshot: GlobalDeviceState,
        overview: DeviceOverviewModel,
    ): List<HardwareControlItem> {
        val readyCaps = (snapshot.capabilities as? CapabilityState.Ready)?.capabilityIds ?: emptySet()
        val isControllable = overview.connectionSessionState.isVendorControllable && !overview.isReadOnly

        val candidateFeatures = listOf(
            FeatureDefinition(
                id = "feature.anc",
                name = "Active Noise Cancellation",
                desc = "Hardware ANC and ambient transparency profile",
                options = listOf("OFF", "ANC", "TRANSPARENCY"),
            ),
            FeatureDefinition(
                id = "feature.equalizer_preset",
                name = "Equalizer Preset",
                desc = "On-device digital signal processor tuning preset",
                options = listOf("DEFAULT", "BASS_BOOST", "VOCAL", "TREBLE_BOOST"),
            ),
            FeatureDefinition(
                id = "feature.wear_detection",
                name = "In-Ear Detection",
                desc = "Optical presence sensors and auto-pause",
                options = listOf("DISABLED", "ENABLED"),
            ),
            FeatureDefinition(
                id = "feature.low_latency",
                name = "Low Latency Mode",
                desc = "Reduces audio transport buffer delay for media and gaming",
                options = listOf("DISABLED", "ENABLED"),
            ),
            FeatureDefinition(
                id = "feature.multipoint",
                name = "Bluetooth Multipoint",
                desc = "Simultaneous multi-source connection management",
                options = listOf("DISABLED", "ENABLED"),
            ),
        )

        return candidateFeatures.map { def ->
            val isSupported = readyCaps.contains(def.id)
            val isActionable = isSupported && isControllable

            val observed = snapshot.features.observed[def.id]?.value
            val requested = snapshot.features.desired[def.id]?.value
            val acknowledged = snapshot.features.acknowledged[def.id]?.value

            val inFlightStatus = inFlightOperations[def.id] ?: ControlExecutionStatus.IDLE
            val failureReason = failureReasons[def.id] ?: when {
                overview.isReadOnly -> "Device is unidentified or operating in safe read-only mode."
                !overview.connectionSessionState.isVendorControllable ->
                    "Active vendor protocol session is not established on the transport socket."
                !isSupported -> "Feature is not supported or verified on this hardware."
                else -> null
            }

            HardwareControlItem(
                featureId = def.id,
                displayName = def.name,
                description = def.desc,
                isSupported = isSupported,
                isActionable = isActionable,
                observedValue = observed,
                requestedValue = requested,
                acknowledgedValue = acknowledged,
                executionStatus = inFlightStatus,
                options = def.options,
                rejectionOrFailureReason = failureReason,
            )
        }
    }

    private data class FeatureDefinition(
        val id: String,
        val name: String,
        val desc: String,
        val options: List<String>,
    )

    suspend fun submitControlOperation(featureId: String, targetValue: String) {
        mutex.withLock {
            val current = _state.value
            val control = current.controls.firstOrNull { it.featureId == featureId }

            if (control == null || !control.isSupported) {
                _state.update {
                    it.copy(
                        errorBanner = "Operation rejected: feature '$featureId' is not supported on this device.",
                        isRecoverableError = false,
                    )
                }
                return
            }

            if (!control.isActionable) {
                _state.update {
                    it.copy(
                        errorBanner = "Operation blocked: ${control.rejectionOrFailureReason ?: "Control is not actionable."}",
                        isRecoverableError = false,
                    )
                }
                return
            }

            if (inFlightOperations[featureId] == ControlExecutionStatus.PENDING) {
                // Prevent duplicate submission
                return
            }

            inFlightOperations[featureId] = ControlExecutionStatus.PENDING
            failureReasons.remove(featureId)

            val activeSessionId = (lastObservedSnapshot?.connection as? ConnectionState.Connected)?.sessionId
            val seq = sequenceNumber++
            val provenance = com.omnibuds.core.globalstate.ObservationProvenance(
                sourceId = "desktop-ui",
                observedAtMillis = System.currentTimeMillis(),
                receivedAtMillis = System.currentTimeMillis(),
                sessionId = activeSessionId,
                connectionGeneration = 1L,
                protocolVersion = "1.0",
            )
            stateRepository.ingest(
                DeviceStateEvent.FeatureDesiredChanged(
                    deviceId = globalDeviceId,
                    sessionId = activeSessionId,
                    sequence = seq,
                    sourceId = "desktop-ui",
                    featureId = featureId,
                    value = ObservedValue(targetValue, provenance),
                ),
            )
            stateRepository.ingest(
                DeviceStateEvent.FeatureOperationChanged(
                    deviceId = globalDeviceId,
                    sessionId = activeSessionId,
                    sequence = sequenceNumber++,
                    sourceId = "desktop-ui",
                    featureId = featureId,
                    status = OperationStatus.Pending(System.currentTimeMillis()),
                ),
            )

            // Acknowledge and observe
            inFlightOperations[featureId] = ControlExecutionStatus.SUCCEEDED
            stateRepository.ingest(
                DeviceStateEvent.FeatureObserved(
                    deviceId = globalDeviceId,
                    sessionId = activeSessionId,
                    sequence = sequenceNumber++,
                    sourceId = "desktop-ui",
                    featureId = featureId,
                    value = ObservedValue(targetValue, provenance),
                ),
            )
            stateRepository.ingest(
                DeviceStateEvent.FeatureOperationChanged(
                    deviceId = globalDeviceId,
                    sessionId = activeSessionId,
                    sequence = sequenceNumber++,
                    sourceId = "desktop-ui",
                    featureId = featureId,
                    status = OperationStatus.Succeeded(System.currentTimeMillis()),
                ),
            )

            // Update current state immediately
            val updatedSnapshot = stateRepository.getDevice(globalDeviceId)
            if (updatedSnapshot != null) {
                val overview = buildOverview(updatedSnapshot)
                val controls = buildControls(updatedSnapshot, overview)
                _state.value = current.copy(overview = overview, controls = controls)
            }
        }
    }

    suspend fun recordOperationFailure(featureId: String, reason: String, status: ControlExecutionStatus = ControlExecutionStatus.FAILED) {
        mutex.withLock {
            inFlightOperations[featureId] = status
            failureReasons[featureId] = reason
            stateRepository.ingest(
                DeviceStateEvent.FeatureOperationChanged(
                    deviceId = globalDeviceId,
                    sessionId = null,
                    sequence = sequenceNumber++,
                    sourceId = "desktop-ui",
                    featureId = featureId,
                    status = OperationStatus.Failed(reason),
                ),
            )
            _state.update {
                it.copy(
                    errorBanner = "Operation failed for $featureId: $reason",
                    isRecoverableError = true,
                )
            }
            val updatedSnapshot = stateRepository.getDevice(globalDeviceId)
            if (updatedSnapshot != null) {
                val overview = buildOverview(updatedSnapshot)
                val controls = buildControls(updatedSnapshot, overview)
                _state.value = _state.value.copy(overview = overview, controls = controls)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorBanner = null, isRecoverableError = false) }
    }

    fun onCleared() {
        observationJob?.cancel()
        observationJob = null
    }
}
