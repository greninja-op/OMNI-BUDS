# Phase 48 — Desktop Application Task List

## 1. Summary of Work

| Task ID | Component / Area | Description | Target Files | Verification | Status |
|---|---|---|---|---|---|
| **TSK-48-01** | Module Configuration | Configure `:platform:desktop` in `settings.gradle.kts` and add `platform/desktop/build.gradle.kts`. | `settings.gradle.kts`, `platform/desktop/build.gradle.kts` | Gradle sync & kotlinc compile | Complete |
| **TSK-48-02** | Design Tokens & Theme | Implement WCAG-compliant color palette, typography, spacing, shapes, and density tokens. | `theme/DesktopTheme.kt` | Unit tests in `DesktopAccessibilityTest` | Complete |
| **TSK-48-03** | Semantic Accessibility | Implement `AccessibilityNode`, roles, states, actions, and `DesktopFocusManager` with Tab/Shift+Tab. | `accessibility/DesktopAccessibility.kt` | Unit tests in `DesktopAccessibilityTest` | Complete |
| **TSK-48-04** | Desktop Platform Seams | Implement desktop platform descriptor, identifier source, lifecycle, storage port, and diagnostic sink. | `platform/DesktopPlatform.kt` | Unit tests in `SettingsAndDiagnosticsTest` | Complete |
| **TSK-48-05** | Devices Screen | Implement `DevicesScreenState`, `DiscoveredDeviceItem`, and `DevicesViewModel` with discovery lifecycle. | `presentation/devices/DevicesViewModel.kt`, `DevicesScreenState.kt` | Unit tests in `DevicesScreenTest` | Complete |
| **TSK-48-06** | Device Workspace | Implement `WorkspaceScreenState` and `DeviceWorkspaceViewModel` with verified controls & read-only lock. | `presentation/workspace/DeviceWorkspaceViewModel.kt`, `WorkspaceScreenState.kt` | Unit tests in `DeviceWorkspaceTest` | Complete |
| **TSK-48-07** | Battery Presentation | Implement `BatteryPresentationModel` preserving nulls, staleness tracking (>60s), and zero synthetic 0%. | `presentation/battery/BatteryPresentationModel.kt` | Unit tests in `BatteryPresentationTest` | Complete |
| **TSK-48-08** | Audio & Codec Model | Implement `AudioPresentationModel` distinguishing active, supported, and configurable codecs + OS limits. | `presentation/audio/AudioPresentationModel.kt` | Unit tests in `AudioCodecPresentationTest` | Complete |
| **TSK-48-09** | Diagnostics & Redaction | Implement `DiagnosticsViewModel` with bounded ring buffer (256), `LogRedactor`, and sanitized export. | `presentation/diagnostics/DiagnosticsViewModel.kt`, `DiagnosticsScreenState.kt` | Unit tests in `SettingsAndDiagnosticsTest` | Complete |
| **TSK-48-10** | Settings & Persistence | Implement `SettingsViewModel` with safe bounds clamping (timeout 5..120s, retention 64..2048) and recovery. | `presentation/settings/SettingsViewModel.kt`, `SettingsScreenState.kt` | Unit tests in `SettingsAndDiagnosticsTest` | Complete |
| **TSK-48-11** | Application Shell | Implement `DesktopWindowController`, `DesktopNavigationCoordinator`, and `DesktopApplicationShell`. | `shell/DesktopApplicationShell.kt`, `DesktopWindowController.kt`, `navigation/DesktopNavigation.kt` | Unit tests in `DesktopApplicationShellTest` | Complete |
| **TSK-48-12** | Headless Semantic UI | Implement `DesktopUiRenderer` generating full semantic trees for all 5 screens and application shell. | `ui/DesktopUiRenderer.kt`, `DesktopApplicationMain.kt` | Unit tests in `DesktopAccessibilityTest` | Complete |
| **TSK-48-13** | Test Suite Implementation | Author 10 test suites covering all shell, view model, accessibility, and lifecycle scenarios. | `platform/desktop/src/test/kotlin/com/omnibuds/desktop/**` | JUnit 5 execution (37/37 passing) | Complete |
| **TSK-48-14** | Full Unified Regression | Run unified test suite across core (1674), android (275), and desktop (37) = 1986 tests. | Unified test run | 1986/1986 tests pass (0 failures, 0 skips) | Complete |
| **TSK-48-15** | Phase 48 Documentation | Author requirements, design, specs, task list, test plan, validation, decisions, and risk register. | `docs/phases/phase-48/*.md` | Document review | Complete |
