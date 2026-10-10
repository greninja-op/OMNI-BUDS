# Phase 46 — Initial Repository & Architecture Audit

## 1. Audit Objective and Scope

Phase 46 initiates the transition of OmniBuds from an Android-centric codebase toward a clean Kotlin Multiplatform (KMP) architecture capable of powering Android and future desktop targets (Linux, macOS, Windows) without duplicating core business logic or compromising Bluetooth safety.

This audit evaluates:
- Current Git branch and working tree state.
- Build configuration, toolchains, Kotlin and Android Gradle Plugin versions.
- Module dependency graph and layer boundaries.
- Android SDK couplings vs. genuinely portable domain logic.
- Baseline test results prior to code modification.
- Safe extraction and migration boundaries.

---

## 2. Repository and Toolchain Baseline

### 2.1 Git Status & Commit Baseline
- **Branch**: `main`
- **Base Commit**: `2c5af24` ("Phase 45: Firmware Compatibility & Device Revision Management (3/3) - docs + CONTEXT.md")
- **Working Tree**: Clean baseline (with local execution scripts `run_tests.sh`, `phase-45-launch.sh`).

### 2.2 Toolchain & Environment
- **Kotlin Compiler**: `kotlinc 2.0.21` at `/home/hatch/workspace/.toolchain/kotlinc/bin/kotlinc`
- **JDK Target**: OpenJDK 17 (`-jvm-target 17 -Werror`) at `/home/hatch/workspace/.toolchain/jdk-17.0.20.1+1-jre`
- **Android SDK**: Compile SDK 35, Min SDK 26, Target SDK 35 (`android.jar` at `/home/hatch/android-sdk/platforms/android-35/android.jar`, `aapt2` 35.0.0)
- **Dependencies**:
  - `kotlinx-coroutines-core-jvm` 1.9.0
  - `kotlinx-coroutines-test-jvm` 1.9.0
  - `kotlin-stdlib` 2.0.21
  - `junit-platform-console-standalone` 1.10.1 (Jupiter 5.10.1)
  - `kotlin-test` / `kotlin-test-junit5` 2.0.21
- **Gradle & Sandbox Constraints**:
  - Gradle daemon execution in this sandbox is restricted (`./gradlew: Permission denied`, offline environment).
  - Standalone deterministic toolchain (`run_tests.sh`) compiles and verifies all modules directly.
  - Baseline verification passed completely: **1927 / 1927 unit tests** (1655 core + 272 android).

---

## 3. Module Graph and Layer Architecture

The repository currently defines three modules:
1. `:core`: Platform-independent Kotlin domain and business logic.
2. `:platform:android`: Android platform adapter implementing Bluetooth, permissions, Quick Settings, widgets, notifications, and lifecycle.
3. `:tools:companion-shell`: Debug-only harness application.

### 3.1 `:core` Layer Structure
Machine-enforced by `DependencyDirectionTest.kt`, `:core` is partitioned into strict downwards-only layers:
- **Layer 0**: `common`, `state`
- **Layer 1**: `transport`, `platform`, `security`
- **Layer 2**: `device`, `capability`, `audio`, `config`, `diagnostics`
- **Layer 3**: `session`, `persistence`, `codec`
- **Layer 4**: `protocol`, `quality`
- **Layer 5**: `feature`, `vendor`, `lab`, `access`, `knowledge`, `extension`, `globalstate`, `lifecycle`, `testkit`, `recovery`, `hil`, `protocoltest`, `validation`, `processing`, `battery`, `configuration`, `verification`, `firmware`
- **Layer 6**: `sdk` (Community Protocol SDK)

### 3.2 Audit of Portability in `:core`
The audit examined all 32 areas of `:core/src/main/kotlin/com/omnibuds/core/`:
- **Imports**: Verified zero imports of `android.*`, `androidx.*`, `com.omnibuds.android.*`, `java.*`, or `javax.*`.
- **Framework Types**: Verified zero references to Android framework types (`BluetoothAdapter`, `BluetoothGatt`, `AudioManager`, etc.).
- **Concurrency**: Relies exclusively on `kotlinx.coroutines` (Flow, StateFlow, Channel, Mutex, Job) with no platform-specific threading primitives.
- **Clock**: Decoupled via `TimeProvider` interface; no direct `System.currentTimeMillis()` calls.
- **Finding**: `:core` is already genuinely portable Kotlin business logic.

---

## 4. Platform Adapter Coupling in `:platform:android`

The audit inspected all components in `platform/android/src/main/kotlin/com/omnibuds/android/`:
- **Bluetooth Subsystem**: `AndroidBluetoothPlatform`, `AndroidAdapterStateSource`, `AndroidConnectedDeviceSource`, `AndroidAudioTransportSource`, `AndroidCodecObservationSource`, `AndroidCodecControlAdapter`.
  - Directly couples to `android.bluetooth.*`, `android.content.Context`, `BroadcastReceiver`, `PackageManager`.
- **UI & System Services**:
  - `OmniBudsTileService`: Binds to `android.service.quicksettings.TileService`.
  - `OmniBudsNotificationReceiver` & `NotificationActionDispatcher`: Binds to `NotificationManager`, `PendingIntent`.
  - `OmniBudsWidgetProvider`: Binds to `AppWidgetProvider`, `RemoteViews`.
  - `OmniBudsLifecycleMonitor`: Implements `Application.ActivityLifecycleCallbacks`.
- **Finding**: These components belong strictly in platform-specific adapters and must remain outside the shared core.

---

## 5. Identified Gaps for Multiplatform Independence

While `:core` avoids platform imports, several platform contracts remained implicit or Android-biased:
1. **Platform Capabilities & OS Identification**:
   `BluetoothPlatformCapabilities` only exposed `val apiLevel: Int?` (an Android SDK version). Desktop hosts running Linux BlueZ, macOS CoreBluetooth, or Windows WinRT have no API level; they need structured OS type (`PlatformType`), OS version, and platform capability descriptors (`PlatformDescriptor`).
2. **Stable Identifier Generation**:
   Operation correlation IDs and verification nonces were constructed ad-hoc across modules. A multiplatform `PlatformIdentifierSource` with deterministic test doubles was needed.
3. **Storage Abstraction**:
   Persistence contracts (`DeviceRepository`, `CapabilityRepository`) did not have an underlying atomic key-value or record storage abstraction (`PlatformStoragePort`) to separate desktop files/keyrings from Android DataStore/SharedPreferences.
4. **Lifecycle Signals**:
   Process lifecycle was monitored through Android `ActivityLifecycleCallbacks` without a portable `PlatformLifecycleSource` and `PlatformLifecycleState` contract.
5. **Transport Factory Seams**:
   Transport opening contracts needed an explicit `PlatformTransportFactory` interface to allow desktop transport backends (Phase 47) to plug in cleanly alongside Android GATT/RFCOMM.

---

## 6. Safe Extraction Strategy

- Add clean, multiplatform platform abstractions to `com.omnibuds.core.platform` (Layer 1).
- Introduce `PlatformType`, `PlatformDescriptor`, `PlatformIdentifierSource`, `PlatformStoragePort`, `PlatformLifecycleState`, `PlatformLifecycleSource`, `PlatformDiagnosticSink`, and `PlatformTransportFactory`.
- Maintain complete backwards compatibility with existing `:core` and `:platform:android` call sites.
- Update `gradle/libs.versions.toml` to register `kotlin-multiplatform`.
- Ensure zero breaking changes across all 1927 baseline tests.
