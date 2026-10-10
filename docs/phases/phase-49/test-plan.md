# Phase 49 — Android UI Test Plan

## 1. Testing Strategy

The Phase 49 test plan validates the Android UI presentation and integration layers across 10 specialized test suites. Due to the sandbox environment (no physical Bluetooth hardware, no Android emulator, no display server, and offline dependency restrictions preventing the Compose compiler plugin from running against Kotlin 2.0.21), the test architecture exercises 100% of the presentation models, view models, lifecycle handlers, accessibility semantics, and cross-surface coordinators in pure-Kotlin unit tests running on JUnit 5 Jupiter with `kotlinx-coroutines-test`.

---

## 2. Test Suite Breakdown

| Test Suite | File Path | Focus Area | Test Count |
|---|---|---|---|
| **AndroidApplicationShellTest** | `platform/android/src/test/.../shell/AndroidApplicationShellTest.kt` | Shell initialization, navigation history, theme switching, global error banners. | 6 |
| **DevicesScreenTest** | `platform/android/src/test/.../devices/DevicesScreenTest.kt` | Adapter state transitions, permission rejection/guidance, discovery start/stop/cancel, device deduplication. | 7 |
| **DeviceWorkspaceTest** | `platform/android/src/test/.../workspace/DeviceWorkspaceTest.kt` | Workspace state isolation, capability categorization, feature control submission, operation status tracking. | 4 |
| **BatteryPresentationTest** | `platform/android/src/test/.../battery/BatteryPresentationTest.kt` | Component battery mapping, null case safety (no fake 0%), staleness detection (>60s). | 4 |
| **AudioCodecPresentationTest** | `platform/android/src/test/.../audio/AudioCodecPresentationTest.kt` | Active codec resolution, LE Audio / A2DP segregation, unobservable codec explanation. | 3 |
| **MultiDeviceIsolationTest** | `platform/android/src/test/.../multidevice/MultiDeviceIsolationTest.kt` | Strict device state separation, independent workspaces, no cross-device contamination. | 3 |
| **SettingsAndDiagnosticsTest** | `platform/android/src/test/.../settings/SettingsAndDiagnosticsTest.kt` | Preference persistence, corrupted key fallback, bounded diagnostic log buffer, sanitized JSON export. | 4 |
| **AndroidLifecycleAndRecoveryTest** | `platform/android/src/test/.../lifecycle/AndroidLifecycleAndRecoveryTest.kt` | Screen cleanup / job cancellation, rapid tap deduplication, unregistered device graceful handling. | 3 |
| **AndroidAccessibilityTest** | `platform/android/src/test/.../accessibility/AndroidAccessibilityTest.kt` | TalkBack content descriptions, busy/disabled state announcement, 48dp minimum touch target validation. | 3 |
| **AndroidSurfaceIntegrationTest** | `platform/android/src/test/.../integration/AndroidSurfaceIntegrationTest.kt` | Synchronization across Main UI, Quick Settings Tile (P25), Notification (P26), and Widget (P27). | 2 |

**Total Phase 49 Test Count**: 39 tests.

---

## 3. Regression & Architectural Compliance Tests

- **Architecture Boundary Verification**: `DependencyDirectionTest` in `:core` verifies that core modules contain zero reverse dependencies on Android presentation or UI packages, and validates authorized package boundaries.
- **Compiler Strictness**: Compiled with `-Werror` (warnings treated as errors) on Kotlin 2.0.21 with JVM target 17.
- **Offline Integrity**: Verified without external network access or active display servers.
