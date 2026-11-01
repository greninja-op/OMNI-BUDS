# OmniBuds UI Redesign — Implementation Plan & Roadmap

## 1. Plan Overview

This plan defines the step-by-step roadmap to transform the OmniBuds user interface from its current unpolished state into an authentic, dark hardware-control product.

---

## 2. Phased Execution Roadmap

### Stage 1: Design Tokens & Theme Alignment
1. **Update Core Design Tokens**:
   - Refactor `OmniBudsTokens.kt` to define the authoritative **fluorescent yellow (`#D4FF00`) / deep black (`#0B0C0E`) / dark charcoal (`#16181C`)** palette.
   - Update `OmniBudsColors.Dark` primary token from cyan (`#38BDF8`) to fluorescent yellow (`#D4FF00`).
2. **Align Android Platform Theme**:
   - Update `AndroidThemeTokens.kt` and `OmniBudsComposeTheme.kt` with the new color palette.
   - Configure high-contrast and dark theme defaults.

### Stage 2: Comprehensive Outline Vector Icon Library
1. **Expand `OmniBudsOutlineIcons.kt`**:
   - Add all required uniform outline icons:
     - `Headphones` (Earbuds & Headset options)
     - `Bluetooth` & `BluetoothSearching`
     - `BatteryFull`, `BatteryCharging`, `BatteryAlert`
     - `NoiseControl` & `Transparency`
     - `Equalizer` & `Sliders`
     - `Settings`, `Diagnostics`, `Info`, `ArrowBack`
     - `CheckCircle` & `WarningAlert`
   - Enforce 100% vector geometry in 24x24dp viewports with zero raster graphics or emojis.

### Stage 3: Atomic Component Suite
1. **Develop Reusable Hardware Components** in `com.omnibuds.android.ui.compose.components.*`:
   - `OmniTopAppBar`: Technical header with Bluetooth indicator and back action.
   - `OmniCard`: Standard charcoal surface with 1dp `#262930` border.
   - `DeviceSummaryCard`: Paired and connected accessory card with mini-battery strip.
   - `SegmentedControlSwitch`: 3-way hardware selector with fluorescent yellow thumb.
   - `BatteryLevelIndicator`: Tri-component battery strip with null-safe handling.
   - `ScanningRadarCard`: Concentric ring scanning radar.
   - `StatusPillBadge`: High-contrast semantic status pill.
   - `CapabilityNoticeCard`: Honest platform limitation card.
   - `ErrorRecoveryBanner`: High-visibility error box with retry and dismiss.

### Stage 4: Screen Redesigns
1. **`DevicesScreen.kt`**:
   - Purposeful empty state with headset vector illustration and prominent fluorescent yellow scan CTA.
   - Active scanning view with animated radar and live discovered cards.
   - Populated device dashboard with active accessory hero card and paired list.
2. **`WorkspaceScreen.kt`**:
   - Hardware overview tab with hero earbud graphic and battery telemetry.
   - Hardware controls tab with tactile segmented noise cancellation switches and equalizer presets.
   - Dedicated battery tab with left/right/case details and charging states.
   - Audio & codec tab with active transport details and honest `NOT_OBSERVABLE` limitation notice.
   - Device info tab with firmware revision, MAC address, and capability discovery matrix.
3. **`SettingsScreen.kt` & `DiagnosticsScreen.kt`**:
   - Restyle into unified dark hardware aesthetic with clear group cards and monospace telemetry logs.
4. **`AboutScreen.kt`**:
   - Display version `1.0.0`, open-source governance, and licensing without marketing fluff.

### Stage 5: Navigation & App Shell Overhaul
1. **Refactor `OmniBudsAppShell.kt`**:
   - Replace generic M3 navigation bar with custom `OmniBottomNavBar`.
   - Apply fluorescent yellow active indicator pill and outline vector icons.
   - Ensure seamless back-navigation handling across all screens.

### Stage 6: Automated Testing & On-Device ADB Validation
1. **Run Local Gradle Test Suites**:
   - Execute `./gradlew test` across `:core`, `:platform:android`, and `:platform:desktop` to guarantee zero regressions (maintain 2,056 passing tests).
2. **Deploy to Connected Device (Xiaomi POCO X6 Pro)**:
   - Assemble debug APK via `./gradlew :tools:companion-shell:assembleDebug`.
   - Deploy via ADB and launch app.
   - Capture live screenshots using Python device bridge to visually verify the redesign on actual hardware.

---

## 3. Implementation Guardrails & Risk Management

| Risk | Impact | Mitigation Strategy |
|---|---|---|
| **Breaking Domain Tests** | High | All UI changes are strictly confined to `:platform:android:ui:compose` and presentation tokens. Core domain engines are untouched. |
| **Simulating Unsupported Hardware** | Critical | Strict non-fabrication rule: unobserved battery values stay null; active codec displays honest `NOT_OBSERVABLE` notice. |
| **Excessive Fluorescent Yellow** | Medium | Yellow is used strictly for accents, primary action buttons, active switches, and selected navigation. Backgrounds and surfaces remain deep black and charcoal. |
| **Inconsistent Icons** | High | All icons are imported from the centralized `OmniBudsOutlineIcons` catalog. No ad-hoc icons or emojis. |
| **Offline Container Incompatibility** | High | The Compose UI layer remains cleanly decoupled from headless JVM unit tests. Seam tests continue to verify presentation models in pure Kotlin. |
