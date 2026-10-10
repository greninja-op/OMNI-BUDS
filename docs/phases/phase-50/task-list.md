# Phase 50 — Unified UI/UX Consolidation Task List

| Task ID | Task Description | Dependencies | Affected Files | Required Tests | Status |
|---|---|---|---|---|---|
| **T50-01** | Register `presentation` area in core architecture layer map | None | `core/.../architecture/DependencyDirectionTest.kt` | `DependencyDirectionTest` | Completed |
| **T50-02** | Implement unified design tokens in core | T50-01 | `core/.../presentation/theme/OmniBudsTokens.kt` | `DesignTokenConsistencyTest` | Completed |
| **T50-03** | Implement unified state mapping & terminology in core | T50-01 | `core/.../presentation/state/DevicePresentationState.kt` | `StateMappingTest` | Completed |
| **T50-04** | Implement unified control & capability models in core | T50-01 | `core/.../presentation/control/UnifiedControlModels.kt` | `ControlModelTest` | Completed |
| **T50-05** | Implement unified battery & audio models in core | T50-01 | `core/.../presentation/battery/UnifiedBatteryModel.kt`<br/>`core/.../presentation/audio/UnifiedAudioModel.kt` | `BatteryAudioModelTest` | Completed |
| **T50-06** | Implement centralized string dictionary & localization | T50-01 | `core/.../presentation/strings/OmniBudsStrings.kt` | `StringsConsistencyTest` | Completed |
| **T50-07** | Implement unified accessibility specifications & contracts | T50-01 | `core/.../presentation/accessibility/AccessibilitySpec.kt` | `AccessibilityContrastTest` | Completed |
| **T50-08** | Implement unified reusable component specification | T50-02 | `core/.../presentation/components/ComponentCatalogSpec.kt` | `ComponentCatalogTest` | Completed |
| **T50-09** | Update Desktop theme and renderer to consume unified core tokens | T50-02, T50-03 | `platform/desktop/.../theme/DesktopTheme.kt`<br/>`platform/desktop/.../ui/DesktopUiRenderer.kt` | `DesktopApplicationShellTest` | Completed |
| **T50-10** | Update Android theme and presentation to consume unified core tokens | T50-02, T50-03 | `platform/android/.../presentation/theme/AndroidThemeTokens.kt`<br/>`platform/android/.../ui/compose/theme/OmniBudsComposeTheme.kt` | `AndroidApplicationShellTest` | Completed |
| **T50-11** | Align Desktop battery & audio models to core unified models | T50-05 | `platform/desktop/.../presentation/battery/BatteryPresentationModel.kt`<br/>`platform/desktop/.../presentation/audio/AudioPresentationModel.kt` | `DesktopBatteryPresentationTest` | Completed |
| **T50-12** | Align Android battery & audio models to core unified models | T50-05 | `platform/android/.../presentation/battery/BatteryPresentationModel.kt`<br/>`platform/android/.../presentation/audio/AudioPresentationModel.kt` | `AndroidBatteryPresentationTest` | Completed |
| **T50-13** | Align Desktop workspace state & controls to unified 6-state capability model | T50-04 | `platform/desktop/.../presentation/workspace/WorkspaceScreenState.kt` | `DesktopWorkspaceTest` | Completed |
| **T50-14** | Align Android workspace state & controls to unified 6-state capability model | T50-04 | `platform/android/.../presentation/workspace/WorkspaceScreenState.kt` | `AndroidWorkspaceTest` | Completed |
| **T50-15** | Add unified cross-platform token, terminology, and accessibility tests | T50-02..T50-14 | `core/src/test/.../presentation/UnifiedDesignSystemTest.kt` | Core Test Suite | Completed |
| **T50-16** | Execute complete regression suite across Core, Android, and Desktop | T50-15 | All test runners | `p50-verify.sh` | Completed |
| **T50-17** | Complete all phase documentation & risk register | T50-16 | `docs/phases/phase-50/*.md` | Doc verification | Completed |
