# Phase 52 — Test Plan: Comprehensive Verification & Test Execution Strategy

## 1. Test Strategy Overview

The Phase 52 test plan coordinates validation across all architectural domains of OmniBuds:
- **Shared Kotlin Core** (lifecycle, state aggregation, protocols, dependency validator, audio/battery engines)
- **Android Library Target** (Bluetooth permissions, background lifecycle, tile services, widgets, notification managers)
- **Desktop Target** (Swing/headless UI rendering, navigation, window controller, simulated adapter abstractions)
- **Security & Privacy** (default-deny unknown devices, redaction of identifiers, offline boundary)
- **Hardware-in-the-Loop** (boundary enforcement, honesty reporting, deferred physical testing)

---

## 2. Test Execution Groups

### Group 1: Core Domain & Protocol Regression
- **Target**: `core/src/test/kotlin`
- **Compiler**: `kotlinc 2.0.21 -jvm-target 17 -Werror`
- **Execution Engine**: JUnit Jupiter 5.10.1
- **Focus**:
  - `DependencyDirectionTest`: Strict architectural boundary enforcement.
  - `ApplicationVersionTest`: Provenance and versioning schema invariants.
  - `AccessPolicyTest` & `DeviceClassificationTest`: Authorization rules.
  - `CodecControlEngineTest` & `PerCodecControlTest`: Audio codec operations and rollback.
  - `BatteryEngineTest` & `BatteryAdaptationTest`: Battery conflict resolution.
  - `ProtocolResolverTest` & `FirmwareCompatibilityResolverTest`: Firmware rules.
  - `LogRedactorTest` & `SecurityTests`: PII and credential sanitization.
- **Expected Outcome**: 1,693 tests passing, 0 failures.

### Group 2: Android Platform Integration Suite
- **Target**: `platform/android/src/test/kotlin`
- **Prerequisites**: AAPT2 compile and link (`com.omnibuds.android`), Android API 35 SDK (`android.jar`).
- **Focus**:
  - `AndroidAdapterStateSourceTest`: Adapter state observations.
  - `AndroidPermissionStateProviderTest`: Permission grant/deny transitions.
  - `NotificationCoordinatorTest` & `NotificationSecurityTest`: Notification intent integrity.
  - `TileStateMapperTest` & `TileSecurityTest`: Quick settings tile behavior.
  - `WidgetStateMapperTest` & `WidgetActionDispatcherTest`: App widget updates.
  - `AndroidLifecycleAndRecoveryTest`: Process death and recreation recovery.
  - `AndroidAccessibilityTest`: Semantics and accessibility contracts.
- **Expected Outcome**: 321 tests passing, 0 failures.

### Group 3: Desktop Platform & UI Integration Suite
- **Target**: `platform/desktop/src/test/kotlin`
- **Focus**:
  - `DesktopApplicationShellTest`: Window creation and lifecycle.
  - `DevicesScreenTest` & `DeviceWorkspaceTest`: UI presentation state rendering.
  - `DesktopAccessibilityTest`: Keyboard focus and accessible descriptors.
  - `DesktopLifecycleTest`: Session startup and graceful shutdown.
  - `MultiDeviceIsolationTest`: Multi-session independence without cross-talk.
- **Expected Outcome**: 42 tests passing, 0 failures.

### Group 4: Release Packaging & Artifact Validation
- **Target**: `build/release-dist`
- **Tools**: `sha256sum`, `python3` JSON schema validator, `unzip`
- **Focus**:
  - Confirm presence of all 5 distribution packages:
    1. `omnibuds-core-1.0.0.jar`
    2. `omnibuds-desktop-1.0.0.jar`
    3. `omnibuds-desktop-1.0.0-linux-x64.tar.gz`
    4. `omnibuds-android-1.0.0.aar`
    5. `omnibuds-companion-shell-1.0.0-unsigned.apk`
  - Bit-for-bit SHA-256 validation against `CHECKSUMS.sha256`.
  - Validate `release-manifest.json` schema v1 consistency.
  - Confirm signing status is `UNSIGNED` / `BLOCKED_NO_KEYS`.

### Group 5: Hardware-in-the-Loop & Physical Verification
- **Target**: Physical Bluetooth peripherals & host controllers
- **Protocol**: 10-Step Evidence Protocol (`DISCOVER` -> `VERIFY`)
- **Environment Reality**: Headless Linux container with NO Bluetooth controller, NO physical handsets, NO earbud hardware.
- **Evaluation Criteria**: Gate 15 must be evaluated honestly as `NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)`. Zero simulation results may be counted as physical proof.

---

## 3. Summary of Test Counts

| Module / Scope | Test Cases Found | Successful | Failed | Skipped | Status |
|---|---|---|---|---|---|
| `:core` | 1,693 | 1,693 | 0 | 0 | **PASSED** |
| `:platform:android` | 321 | 321 | 0 | 0 | **PASSED** |
| `:platform:desktop` | 42 | 42 | 0 | 0 | **PASSED** |
| **Total Automated Suite** | **2,056** | **2,056** | **0** | **0** | **PASSED (100%)** |
