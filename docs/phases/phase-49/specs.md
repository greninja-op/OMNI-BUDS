# Phase 49 — Android UI Technical Specifications

## 1. Package Structure & Module Placement

All Phase 49 presentation and UI implementations reside within `:platform:android`:

```
platform/android/src/main/kotlin/com/omnibuds/android/
├── presentation/                      # Pure-Kotlin Presentation Layer (Zero Compose imports)
│   ├── about/
│   │   └── AboutScreenState.kt        # App version, build, open-source attributions
│   ├── accessibility/
│   │   └── AndroidAccessibilitySemantics.kt # Semantic nodes, TalkBack helpers, touch target validator
│   ├── audio/
│   │   └── AudioPresentationModel.kt  # Audio routing, active/supported codec presentation
│   ├── battery/
│   │   └── BatteryPresentationModel.kt# Component battery levels, charging state, staleness checks
│   ├── devices/
│   │   ├── AndroidDeviceDiscoverySession.kt # Discovery abstraction & result stream
│   │   ├── DevicesScreenState.kt      # Screen modes, discovered/known device models
│   │   └── DevicesViewModel.kt        # Discovery flow, permission guidance, adapter state
│   ├── diagnostics/
│   │   ├── DiagnosticsScreenState.kt  # Diagnostic event models, filter levels
│   │   └── DiagnosticsViewModel.kt    # Event buffering, sanitized JSON exporter
│   ├── integration/
│   │   └── AndroidSurfaceCoordinator.kt # Syncs selection across UI, Tile, Notification, Widget
│   ├── navigation/
│   │   └── AndroidNavigation.kt       # Destinations, back-stack management, navigation controller
│   ├── settings/
│   │   ├── SettingsScreenState.kt     # App preferences model, theme mode enum
│   │   └── SettingsViewModel.kt       # Safe preference persistence & corrupted key fallback
│   ├── shell/
│   │   └── AndroidApplicationShell.kt # Global UI shell, theme token manager, error banners
│   ├── theme/
│   │   └── AndroidThemeTokens.kt      # Spacing, typography, shape, color palettes (Light/Dark/High-Contrast)
│   └── workspace/
│       ├── DeviceWorkspaceViewModel.kt# Focused per-device controller, feature operations
│       └── WorkspaceScreenState.kt    # Overview, hardware controls, limitations, tabs
└── ui/compose/                        # Declarative Jetpack Compose UI Layer
    ├── components/
    │   └── OmniBudsComponents.kt      # Reusable cards, status chips, buttons, headers
    ├── screens/
    │   ├── AboutScreen.kt             # About view composable
    │   ├── DevicesScreen.kt           # Device discovery and known devices list
    │   ├── DiagnosticsScreen.kt       # Diagnostic log viewer and export trigger
    │   ├── SettingsScreen.kt          # User preferences toggles and sliders
    │   └── WorkspaceScreen.kt         # Device tabs (Overview, Controls, Audio, Battery)
    ├── shell/
    │   └── OmniBudsAppShell.kt        # Scaffold, top bar, navigation rail/bar, content router
    └── theme/
        └── OmniBudsComposeTheme.kt    # MaterialTheme mapping from AndroidThemeTokens
```

---

## 2. Core Presentation Models & Data Contracts

### 2.1 DevicesScreenState
```kotlin
data class DevicesScreenState(
    val bluetoothState: BluetoothPlatformState = BluetoothPlatformState(...),
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<AndroidDiscoveredDevice> = emptyList(),
    val knownDevices: List<AndroidDiscoveredDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val errorBanner: String? = null,
    val isRecoverable: Boolean = false,
    val permissionGuidance: String? = null,
) {
    val screenMode: DevicesScreenMode
        get() = ... // Derives BLUETOOTH_UNAVAILABLE, BLUETOOTH_DISABLED, PERMISSION_REQUIRED,
                    // DISCOVERY_IN_PROGRESS, DEVICES_FOUND, NO_DEVICES_FOUND
}
```

### 2.2 WorkspaceScreenState & HardwareControlModel
```kotlin
data class WorkspaceScreenState(
    val deviceIdentifier: String,
    val isLoading: Boolean = false,
    val selectedTab: WorkspaceTab = WorkspaceTab.OVERVIEW,
    val overview: DeviceOverviewModel? = null,
    val controls: List<HardwareControlModel> = emptyList(),
    val battery: BatteryPresentationModel? = null,
    val audio: AudioPresentationModel? = null,
    val limitations: List<String> = emptyList(),
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = false,
)

data class HardwareControlModel(
    val featureId: String,
    val displayName: String,
    val category: String,
    val capabilityKind: FeatureCapabilityKind,
    val currentValue: String?,
    val desiredValue: String?,
    val acknowledgedValue: String?,
    val observedValue: String?,
    val executionStatus: ControlExecutionStatus,
    val availableModes: List<String>,
    val failureReason: String? = null,
    val explanation: String? = null,
) {
    val isActionable: Boolean
        get() = capabilityKind in listOf(FeatureCapabilityKind.PERSISTENT, FeatureCapabilityKind.SESSION_ONLY)
}
```

### 2.3 BatteryPresentationModel
```kotlin
data class BatteryPresentationModel(
    val summaryPercentage: Int?,
    val leftBudPercentage: Int?,
    val rightBudPercentage: Int?,
    val casePercentage: Int?,
    val isCharging: Boolean,
    val isStale: Boolean,
    val lastObservedEpochMillis: Long?,
)
```
- **Rule**: If a component (e.g. charging case) is not reported by hardware, its property is strictly `null`. It is never populated with a synthetic `0%`.
- **Staleness**: Telemetry observed > 60,000 ms ago is marked `isStale = true`.

### 2.4 AudioPresentationModel
```kotlin
data class AudioPresentationModel(
    val activeCodecName: String?,
    val codecFamily: String?,
    val isObservable: Boolean,
    val unobservableReason: String?,
    val supportedCodecs: List<String>,
    val isConfigurable: Boolean,
)
```
- **Platform Limitation Handling**: On Android devices where the active Bluetooth codec is inaccessible via public framework APIs, `isObservable = false` with an explicit reason string.

---

## 3. Surface Coordinator & Integration Specification

`AndroidSurfaceCoordinator` implements cross-surface synchronization:
- Exposes `activeSurfaceDevice: StateFlow<GlobalDeviceState?>`.
- Synchronizes with Phase 25 Quick Settings Tile (`OmniBudsTileService`), Phase 26 Notifications (`NotificationCoordinator`), and Phase 27 Widget (`OmniBudsWidgetProvider`).
- Automatically resolves to the selected device, or falls back to the single connected device when exactly one device is active.
