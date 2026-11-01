# OmniBuds UI Redesign — Phase 1: Architectural & Visual Audit

## 1. Executive Summary

OmniBuds is an open-source, universal hardware-control platform for Bluetooth earbuds and headphones built with a backend-first philosophy across 52 development phases. While the domain logic, capability models, persistence engines, and test coverage (2,056 JVM tests, 100% pass) are robust, the user interface remains an unpolished, sparse scaffold.

This audit establishes the ground truth of the application:
1. What backend functionality is genuinely implemented and verified.
2. What hardware features are blocked or honest platform limitations.
3. The visual defects and UX shortcomings of the current reference implementation (captured live from the connected Xiaomi POCO X6 Pro).
4. The structural boundary between pure-Kotlin presentation models and declarative Jetpack Compose UI.

---

## 2. Full Application Architecture Audit

```
┌────────────────────────────────────────────────────────────────────────┐
│                        :platform:android                               │
│  ┌────────────────────────┐  ┌──────────────────────────────────────┐  │
│  │   ui.compose.*         │  │   presentation.* (Pure Kotlin MVI)   │  │
│  │   - AppShell           │  │   - DevicesViewModel                 │  │
│  │   - DevicesScreen      │  │   - DeviceWorkspaceViewModel         │  │
│  │   - WorkspaceScreen    │  │   - BatteryPresentationModel         │  │
│  │   - SettingsScreen     │  │   - AudioPresentationModel           │  │
│  │   - DiagnosticsScreen  │  │   - AndroidSurfaceCoordinator        │  │
│  │   - AboutScreen        │  │   - AndroidAccessibilitySemantics    │  │
│  └───────────┬────────────┘  └──────────────────┬───────────────────┘  │
└──────────────┼──────────────────────────────────┼──────────────────────┘
               │                                  │
┌──────────────▼──────────────────────────────────▼──────────────────────┐
│                            :core                                       │
│  ┌──────────────────────┐  ┌────────────────────────────────────────┐  │
│  │ core.presentation    │  │ core.device / core.capability          │  │
│  │ - OmniBudsTokens     │  │ - HardwareFeatureEngine (6-state)      │  │
│  │ - OmniBudsStrings    │  │ - AudioTransportEngine                 │  │
│  │ - UnifiedControlModel│  │ - BatteryEngine                        │  │
│  │ - AccessibilitySpec  │  │ - PersistentConfigurationEngine        │  │
│  └──────────────────────┘  │ - PersistenceVerificationFramework     │  │
│                            │ - VendorRegistry (empty by design)     │  │
│                            └────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Backend Feature Verification & Classification

| Component / Subsystem | Implementation Status | Verification Status | Architectural Classification | Notes |
|---|:---:|:---:|---|---|
| **Device Discovery & Census** | Complete | Verified (321 Android unit tests + offline fixtures) | **Implemented & Verified** | Probes bonded/connected Bluetooth devices via Android BluetoothAdapter; classifies device kinds. |
| **Hardware Feature Control Engine** | Complete | Verified (1,693 Core unit tests) | **Implemented & Verified** | 6-state finite state machine (ANC, Ambient, Equalizer) with precheck, apply, re-observe, verify, commit. |
| **Battery & Power Engine** | Complete | Verified (Unit tests) | **Implemented & Verified** | Models Left bud, Right bud, and Case independently. Nullable values; never fabricates 0%. |
| **Audio Transport & Routing** | Complete | Verified (Unit tests) | **Implemented & Verified** | Observes A2DP/HFP/LE Audio links. Outside media path (no audio decoding/recording). |
| **Audio Codec Selection** | Intentionally blocked | Honest platform limitation | **Blocked by Platform** | AOSP provides no unprivileged public API for active codec selection; adapter reports `NOT_SELECTABLE`. |
| **Active Codec Telemetry** | Intentionally blocked | Honest platform limitation | **Blocked by Platform** | No public Android API exposes the active codec; adapter reports `NOT_OBSERVABLE`. |
| **Vendor Protocol Adapters** | Framework complete | Registry ships empty | **Implemented (Empty Registry)** | `VendorRegistry` exists, but ships empty because no manufacturer protocol has license-cleared byte evidence. |
| **Configuration Persistence** | Complete | Verified (Unit tests) | **Implemented & Verified** | Typed JSON storage, schema versioning, checksums, corruption recovery, and evidence-backed verification. |
| **Surface Coordination** | Complete | Verified (Unit tests) | **Implemented & Verified** | `AndroidSurfaceCoordinator` synchronizes Main App, Quick Settings Tile, Notification, and App Widget. |
| **Android Quick Settings Tile** | Complete | Verified (Seam tests) | **Implemented & Verified** | `OmniBudsTileService` displays current connection and triggers primary feature toggle. |
| **Persistent Notification** | Complete | Verified (Seam tests) | **Implemented & Verified** | `OmniBudsNotificationManager` manages media-style status notification. |
| **Pure-Kotlin Presentation Layer** | Complete | Verified (2,056 total tests) | **Implemented & Verified** | Complete MVI/MVVM view models and state flows decoupled from Android framework dependencies. |
| **Compose UI Screens** | Scaffold complete | Unverified on real hardware | **Partially Implemented** | Screens exist in `com.omnibuds.android.ui.compose.*`, but visual hierarchy is unfinished. |

---

## 3. Visual & UX Audit of Current Reference Screen

Based on the live screenshot captured from the connected Xiaomi POCO X6 Pro (`2311DRK48I`, Android 14):

```
┌──────────────────────────────────────────────────────────┐
│ 1:05  ..                                   [88%] 5G  LTE │
├──────────────────────────────────────────────────────────┤
│ OmniBuds                                                 │  ◄ [DEFECT 1: Flat plain top bar, no status/branding]
├──────────────────────────────────────────────────────────┤
│                                                          │
│ Devices                       [ Scan for Earbuds ]       │  ◄ [DEFECT 2: Dissonant cyan pill button, poor balance]
│                                                          │
│                                                          │
│                                                          │
│                                                          │
│       No compatible earbuds or headphones found.         │  ◄ [DEFECT 3: Massive empty void; small text-only empty
│       Tap 'Scan for Earbuds' to discover nearby          │     state; zero graphics, zero scanning radar, zero
│       devices.                                           │     bluetooth guidance]
│                                                          │
│                                                          │
│                                                          │
│                                                          │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  [ (()) ]         [ * ]            [ ||| ]       [ (i) ] │  ◄ [DEFECT 4: Clashing purple pill highlight; generic
│   Devices       Settings            Diag          About  │     M3 navigation bar; lacks hardware aesthetic]
└──────────────────────────────────────────────────────────┘
```

### Detailed Visual Defects Identified:

1. **Severe Canvas Emptiness & Weak Density**:
   - The entire middle 75% of the viewport is blank `#121316` black space.
   - The empty state consists of two lines of unstyled secondary grey body text centered in an immense void.
   - There is no scanning animation, no Bluetooth readiness indicator, no illustration, and no quick-action setup card.

2. **Palette Dissonance & Inconsistent Brand Identity**:
   - The "Scan for Earbuds" button uses a bright cyan blue (`#38BDF8`), which directly clashes with the established OmniBuds brand palette (fluorescent yellow accent on dark charcoal/black).
   - The bottom navigation bar uses an out-of-the-box Material 3 tonal purple pill indicator, creating a chaotic 3-way color conflict (Cyan + Purple + Charcoal).

3. **Weak Visual Hierarchy**:
   - The headline "Devices" shares visual priority with the button beside it.
   - There are no structural cards, dividers, or surface elevations to anchor the user's eye.

4. **Primitive Navigation Bar**:
   - The navigation items lack distinctive tactile styling.
   - The navigation labels (`Diag` instead of `Diagnostics`) feel abbreviated and unpolished.

5. **Lack of Hardware Context**:
   - The user cannot see whether Bluetooth is currently powered on, whether permissions are active, or how many devices are paired in the Android OS without tapping the scan button.

---

## 4. Architectural Invariants to Protect

The redesign must strictly uphold the following non-negotiable rules:
1. **Never Fabricate Hardware State**: If battery is unavailable, show `null` / "Not reported", never `0%` or synthetic bars.
2. **Never Simulate Controls**: If a device does not support ANC or codec switching, do not render a fake slider or toggle. Clearly display `UNSUPPORTED` or `NOT_OBSERVABLE`.
3. **Downward Layer Dependency**: Compose UI files must depend solely on presentation models and tokens. No direct imports of private or hardware internals.
4. **Offline Testability**: All presentation logic must remain in pure Kotlin so JVM test suites pass without device or emulator dependencies.
