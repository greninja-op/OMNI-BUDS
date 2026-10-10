# Phase 48 — Desktop Application Technical Specifications

## 1. Module & Artifact Coordinates

- **Module Path**: `:platform:desktop`
- **Source Root**: `platform/desktop/src/main/kotlin/com/omnibuds/desktop/`
- **Test Root**: `platform/desktop/src/test/kotlin/com/omnibuds/desktop/`
- **Target JDK**: Java 17 bytecode target (`-jvm-target 17`)
- **Compilation Options**: `-Werror` (warnings as errors), `-Xfriend-paths` for test visibility
- **Dependencies**: `:core` (compile-time and runtime), `kotlinx-coroutines-core` 1.9.0, `kotlin-stdlib` 2.0.21

---

## 2. Shell & Window Specification

### `DesktopWindowController`
- `WindowState`:
  - `title: String` (Default: `"OmniBuds — Desktop Hardware Control"`)
  - `width: Int` (Default: `1024`, clamped to >= `640`)
  - `height: Int` (Default: `720`, clamped to >= `480`)
  - `isMaximized: Boolean` (Default: `false`)
  - `isMinimized: Boolean` (Default: `false`)
  - `isVisible: Boolean` (Default: `true`)
- Methods:
  - `resize(width: Int, height: Int)`: Updates dimensions, resets `isMaximized = false`.
  - `toggleMaximize()`: Inverts `isMaximized`.
  - `minimize()`: Sets `isMinimized = true`.
  - `restore()`: Sets `isMinimized = false`.

### `DesktopNavigationCoordinator`
- Destinations:
  - `DesktopDestination.Devices` (Root screen)
  - `DesktopDestination.DeviceWorkspace(deviceIdentifier: String)`
  - `DesktopDestination.Diagnostics`
  - `DesktopDestination.Settings`
  - `DesktopDestination.About`
- Contract:
  - `navigateTo(destination: DesktopDestination)`: Pushes destination to history stack; duplicate consecutive destinations ignored.
  - `goBack(): Boolean`: Pops previous destination; returns `false` if stack is empty (root reached).

---

## 3. Screen State Models

### A. Devices Screen (`DevicesScreenState`)
```kotlin
data class DevicesScreenState(
    val availability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.UNKNOWN,
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<DiscoveredDeviceItem> = emptyList(),
    val knownDevices: List<DiscoveredDeviceItem> = emptyList(),
    val selectedDeviceIdentifier: String? = null,
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = false,
    val isLoading: Boolean = false,
)
```

### B. Device Workspace Screen (`WorkspaceScreenState`)
```kotlin
data class WorkspaceScreenState(
    val deviceIdentifier: String,
    val isLoading: Boolean = false,
    val overview: DeviceOverviewModel? = null,
    val controls: List<HardwareControlItem> = emptyList(),
    val battery: BatteryPresentationModel = BatteryPresentationModel(),
    val audio: AudioPresentationModel = AudioPresentationModel(),
    val limitations: List<String> = emptyList(),
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = false,
)
```

### C. Diagnostics Screen (`DiagnosticsScreenState`)
```kotlin
data class DiagnosticsScreenState(
    val applicationVersion: String = "1.0.0",
    val platformDescriptor: PlatformDescriptor = PlatformDescriptor.unobserved(),
    val adapterAvailability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.UNKNOWN,
    val events: List<DiagnosticEventItem> = emptyList(),
    val droppedEventsCount: Long = 0L,
    val filterSeverity: DiagnosticSeverity? = null,
    val exportResult: String? = null,
    val isExporting: Boolean = false,
)
```

### D. Settings Screen (`SettingsScreenState`)
```kotlin
data class SettingsScreenState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val discoveryTimeoutSeconds: Int = 15, // Range: 5..120
    val autoRefreshDevices: Boolean = false,
    val diagnosticRetentionLimit: Int = 512, // Range: 64..2048
    val isDirty: Boolean = false,
    val lastSavedEpochMillis: Long? = null,
    val errorBanner: String? = null,
)
```

---

## 4. Accessibility Semantic Model

### `AccessibilityRole`
- `APPLICATION`, `WINDOW`, `PANE`, `LIST`, `LIST_ITEM`, `BUTTON`, `TEXT_FIELD`, `TOGGLE`, `COMBO_BOX`, `STATUS_BADGE`, `ALERT`, `STATIC_TEXT`.

### `AccessibilityNode`
```kotlin
data class AccessibilityNode(
    val id: String,
    val label: String,
    val role: AccessibilityRole,
    val state: AccessibilityState = AccessibilityState(),
    val children: List<AccessibilityNode> = emptyList(),
    val actions: Set<AccessibilityAction> = emptySet(),
    val keyboardShortcut: String? = null,
)
```
- Traversal: Linear tab order is built via depth-first flattened traversal of actionable nodes (`BUTTON`, `TEXT_FIELD`, `TOGGLE`, `COMBO_BOX`, `LIST_ITEM`).
