# Phase 46 — Comprehensive Test Plan: KMP Core & Platform Independence

## 1. Test Strategy Overview

OmniBuds operates under strict automated, deterministic test discipline. No physical Bluetooth hardware is required or permitted for automated verification.

The test strategy combines:
1. **Machine-Checked Architecture Tests**: Enforcing downwards-only dependency flow and banning Android/JVM-only imports from the portable core (`DependencyDirectionTest.kt`).
2. **Deterministic Domain Unit Tests**: Exercising core multiplatform contracts using fake adapters and in-memory implementations (`PlatformIndependenceTest.kt`).
3. **Android Platform Adapter Tests**: Exercising Android-specific platform adapter implementations and metadata mappings (`AndroidPlatformIndependenceTest.kt`).
4. **Full Suite Regressions**: Re-running all 1927 baseline unit tests to guarantee zero regressions across Phases 0–45.

---

## 2. Test Suites and Execution Matrix

| Suite | Module | Location | Target | Description |
|---|---|---|---|---|
| **Platform Independence** | `:core` | `PlatformIndependenceTest.kt` | JVM 17 | Tests `PlatformType`, `PlatformDescriptor`, `DeterministicIdentifierSource`, `InMemoryStoragePort`, `PlatformLifecycleState`, `NoOpDiagnosticSink`. |
| **Architecture Rules** | `:core` | `DependencyDirectionTest.kt` | JVM 17 | Verifies zero imports of `android.*`, `java.*`, `javax.*`, test doubles, or layer violations. |
| **Android Adapters** | `:platform:android` | `AndroidPlatformIndependenceTest.kt` | JVM 17 + Android 35 SDK | Tests `AndroidPlatformDescriptor`, `AndroidPlatformIdentifierSource`, `AndroidPlatformLifecycleSource`. |
| **Core Regression Suite** | `:core` | `core/src/test/kotlin/**` | JVM 17 | All 1655 baseline core tests across Phases 0–45. |
| **Android Regression Suite**| `:platform:android` | `platform/android/src/test/kotlin/**` | JVM 17 + Android 35 SDK | All 272 baseline Android tests across Phases 0–45. |

---

## 3. Test Cases for New Multiplatform Seams

### 3.1 `PlatformIndependenceTest` (`:core`)
1. `platformTypeIdentifiesDesktopVsMobileAccurately`:
   - Verifies `isDesktop` is true for Linux, macOS, Windows, Desktop Generic; false for Android and Unknown.
   - Verifies `isMobile` is true for Android; false for all others.
2. `platformDescriptorCapturesMetadataWithoutFabrication`:
   - Verifies unobserved descriptor contains nulls and unknown type.
   - Verifies custom descriptor preserves OS name, version, architecture, and candidate transports.
3. `deterministicIdentifierSourceProvidesSequentialIds`:
   - Verifies sequential generation of operation IDs with prefix and reset behavior.
   - Verifies sequential nonce generation.
4. `inMemoryStoragePortSatisfiesStorageContract`:
   - Verifies get, set, remove, contains, and clear operations.
   - Tests that missing keys return `Success(null)` rather than throwing.
5. `platformLifecycleStateDistinguishesInteractive`:
   - Verifies `isInteractive` is true for `FOREGROUND`; false for `BACKGROUND`, `SUSPENDED`, `TERMINATING`.
6. `noOpDiagnosticSinkSafelyIgnoresEvents`:
   - Verifies `isEnabled` is false and `emit` returns `Success(Unit)` without side effects.
7. `platformCapabilitiesCarryPlatformType`:
   - Verifies `BluetoothPlatformCapabilities` defaults to `UNKNOWN` and preserves copied values.

### 3.2 `AndroidPlatformIndependenceTest` (`:platform:android`)
1. `androidPlatformDescriptorReportsAccuratePlatformType`:
   - Verifies `AndroidPlatformDescriptor.fromApiLevel(34)` sets `PlatformType.ANDROID`, `apiLevel = 34`, `isMobile = true`, `isDesktop = false`.
2. `androidPlatformIdentifierSourceGeneratesValidUniqueIds`:
   - Verifies UUID-backed generation produces non-blank, non-colliding operation IDs and nonces.
3. `androidPlatformLifecycleSourceTracksStateTransitions`:
   - Verifies state transitions and interactive flags in `AndroidPlatformLifecycleSource`.

---

## 4. Test Execution Commands & Expected Baselines

- **Toolchain**: Standalone `kotlinc 2.0.21`, JDK 17, `junit-platform-console-standalone-1.10.1.jar`.
- **Baseline Test Count**: 1927 / 1927 passed (1655 core + 272 android).
- **Target Post-Phase 46 Count**: 1937 / 1937 passed (1662 core + 275 android; +10 new tests).
