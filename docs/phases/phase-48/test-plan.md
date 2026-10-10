# Phase 48 — Desktop Application Test Plan

## 1. Test Philosophy & Strategy

Due to the absence of a display server (X11/Wayland) and offline sandbox policies in the execution environment:
1. **Zero Graphical Dependencies**: All view models, presentation state mappers, navigation coordinators, accessibility semantic models, and window controllers are purely functional and reactive. They require no running toolkit loop.
2. **Deterministic Virtual Time**: Coroutine flows and background jobs are tested using `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `testScheduler.runCurrent()`).
3. **Hardware Truthfulness**: Physical Bluetooth hardware operations are deferred to Phase 52. All desktop adapter behaviors (availability changes, discovery observations, error codes) are simulated using `SimulatedDesktopAdapter` strictly residing in test sources (`platform/desktop/src/test/`).

---

## 2. Test Suite Architecture

| Test Class | Package | Tests | Scope & Assertions |
|---|---|---|---|
| `DesktopApplicationShellTest` | `com.omnibuds.desktop.shell` | 6 | Launch defaults, navigation backstack, window controller geometry/maximize/minimize, shortcuts (`Ctrl+1..4`), theme preference propagation, global error banners, clean shutdown. |
| `DevicesScreenTest` | `com.omnibuds.desktop.devices` | 5 | Adapter availability states (`AVAILABLE`, `DISABLED`, `PERMISSION_REQUIRED`, etc.), discovery deduplication, start scan failure recovery, adapter loss during active scan cancellation, device selection. |
| `DeviceWorkspaceTest` | `com.omnibuds.desktop.workspace` | 4 | Verified controllable device with actionable features, unidentified device read-only mode, control submission pending/succeeded lifecycle, operation failure banner. |
| `BatteryPresentationTest` | `com.omnibuds.desktop.battery` | 4 | Preserves `null` for unobserved telemetry (never synthetic 0%), staleness detection (>60s), case vs bud level isolation, charging state indicators. |
| `AudioCodecPresentationTest` | `com.omnibuds.desktop.audio` | 3 | Distinguishes active vs supported codecs, honest reporting of OS codec unobservability (platform limitations), pending codec state changes. |
| `MultiDeviceIsolationTest` | `com.omnibuds.desktop.multidevice` | 3 | Simultaneous multi-device repository updates remain strictly isolated, disconnecting device A leaves device B unaffected, duplicate device names with distinct IDs never collide. |
| `SettingsAndDiagnosticsTest` | `com.omnibuds.desktop.settings` | 4 | Settings persistence and reload, corrupted preferences fallback to clamped bounds, sensitive data redaction in diagnostic messages, sanitized JSON export document structure. |
| `DesktopAccessibilityTest` | `com.omnibuds.desktop.accessibility` | 4 | Complete semantic tree generation for all screens, linear tab keyboard focus traversal (forward & backward), alert accessibility role presentation, high-contrast theme token validation. |
| `DesktopLifecycleTest` | `com.omnibuds.desktop.lifecycle` | 4 | Window close / ViewModel clearance releases active discovery sessions, live background device updates reactively refresh open workspace, rapid repeated submissions deduplicated without corruption. |

---

## 3. Test Isolation & Friend Paths

- **Test Doubles**: `SimulatedDesktopAdapter` implements `DesktopBluetoothAdapter` and resides exclusively in `platform/desktop/src/test/kotlin/com/omnibuds/desktop/SimulatedDesktopAdapter.kt`.
- **Friend Paths**: Compilations configure `-Xfriend-paths` targeting `:platform:desktop` main classes to permit internal visibility where required for test verification.
- **Architectural Isolation**: `DependencyDirectionTest` in `:core` verifies that `:core` does not depend on or import `:platform:desktop`.
