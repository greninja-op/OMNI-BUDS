# OmniBuds UI-to-Backend State Contracts

This document specifies the authoritative contracts connecting the Jetpack Compose UI layer with the pure-Kotlin presentation ViewModels and core hardware domain engines.

---

## 1. Core Contract Invariants

1. **Unidirectional Data Flow (UDF)**:
   - UI observes immutable `StateFlow<ScreenState>`.
   - User actions invoke explicit ViewModel methods (e.g., `startDiscovery()`, `applyControlValue(id, value)`).
   - ViewModels never expose mutable state directly to Compose.

2. **Non-Fabrication Policy**:
   - `UNKNOWN` hardware state is never converted to `UNSUPPORTED`, `0`, or `false`.
   - If battery level is null, UI renders "Not reported", never `0%`.
   - If active codec cannot be queried via public AOSP APIs, it is reported as `NOT_OBSERVABLE`.
   - If codec cannot be altered, it is reported as `NOT_SELECTABLE`.

3. **Optimistic UI Banned for Hardware Writes**:
   - Tapping an ANC or Equalizer switch moves the UI state to `PENDING_APPLICATION`.
   - The UI does **not** indicate `APPLIED` until the hardware transaction finishes `VERIFY` and returns a confirmed outcome.
   - If the write fails or times out, the switch rolls back to the previous confirmed state with an error banner.

---

## 2. Screen Contract Specifications

### 2.1 Devices Dashboard (`DevicesScreen`)

* **ViewModel**: `com.omnibuds.android.presentation.devices.DevicesViewModel`
* **State Flow**: `val state: StateFlow<DevicesScreenState>`
* **Data Sources**:
  - `AndroidBluetoothAdapterBridge` (Bluetooth adapter enabled, permissions)
  - `AndroidDeviceDiscoverySession` (Discovered BLE/Classic peripherals)
  - `ConnectedDeviceCensus` (Paired devices in OS settings)
* **User Actions & Invocations**:
  - `startDiscovery()` → Initiates BLE/Classic discovery session with 15s bounded timeout.
  - `stopDiscovery()` → Halts active discovery session immediately.
  - `selectDevice(deviceId: String)` → Notifies `AndroidSurfaceCoordinator`, navigates to `DeviceWorkspace`.
  - `retry()` → Retries failed Bluetooth permission or census inspection.
* **Failure Modes**:
  - `BLUETOOTH_DISABLED`: Prompts user to enable Bluetooth in Android settings.
  - `PERMISSION_REQUIRED`: Displays permission request rationale.
  - `NO_DEVICES_FOUND`: Renders purposeful empty state.

---

### 2.2 Device Workspace (`WorkspaceScreen`)

* **ViewModel**: `com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel`
* **State Flow**: `val state: StateFlow<WorkspaceScreenState>`
* **Data Sources**:
  - `GlobalDeviceStateRepository`: Aggregated snapshot of identity, battery, controls, and audio quality.
  - `HardwareFeatureEngine`: 6-state control machine managing hardware capabilities.
  - `PersistentConfigurationEngine`: Device-specific stored preferences.
* **User Actions & Invocations**:
  - `selectTab(tab: WorkspaceTab)` → Switches between Overview, Controls, Battery, Audio, Info.
  - `applyFeature(featureId, value)` → Dispatches hardware write transaction: `PRECHECK` → `APPLY` → `RE-OBSERVE` → `VERIFY` → `COMMIT`.
  - `retryFailedOperations()` → Re-evaluates failed transactions.
* **Telemetry Contracts**:
  - **Battery**: Left, Right, Case percentages (nullable). Null values render as unobserved.
  - **Audio Link**: A2DP or LE Audio transport state, profile connection status.
  - **Codec Notice**: Renders platform limitation disclaimer without hiding reality.

---

### 2.3 Settings & Diagnostics (`SettingsScreen`, `DiagnosticsScreen`)

* **ViewModels**:
  - `com.omnibuds.android.presentation.settings.SettingsViewModel`
  - `com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel`
* **Data Sources**:
  - `PersistentConfigurationEngine`: JSON-backed typed preferences.
  - `DiagnosticSink`: Structured log of protocol transactions, errors, and system events.
* **User Actions & Invocations**:
  - `setThemeMode(mode)` → Updates `OmniBudsThemeMode` and persists configuration.
  - `clearLogs()` → Resets in-memory telemetry buffer.
  - `exportDiagnostics()` → Serializes diagnostics to JSON for local export.
