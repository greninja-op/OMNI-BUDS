# Phase 10 — Audio Transport Engine: Test Plan

## 1. Strategy

JVM unit tests for everything testable without hardware; architecture tests for phase
boundaries; explicit non-coverage for framework calls (recorded, not implied).

## 2. Core tests (`core/src/test/.../audio/`)

| Test class | Tests | What it proves |
|---|---|---|
| `AudioConnectionStateTest` | 6 | 7 states distinct; CONNECTED≠ACTIVE; UNKNOWN≠DISCONNECTED; direction independence |
| `AudioReconcilerTest` | 9 | Each of the 5 rules with scripted contradictions; diagnostic bounding; SCO non-attribution |
| `LeAudioSupportTest` | 5 | API 33+ SUPPORTED; <33 API_TOO_OLD; null UNKNOWN; pure function of apiLevel |
| `AudioControlTopologyTest` | 4 | `verify()` needs both planes; planes are different types |
| `AudioTransportEngineTest` | 11 | start/stop idempotence; failed start → STOPPED; event merging; removal; refresh guards; failing reads keep previous + diagnostic; lifecycle values |
| `PhaseTenScopeTest` | 8 | No android imports; no capture/codec/routing/mic/vendor vocabulary; single snapshot flow; pure reconciler |
| `OmniBudsErrorCategoryTest` | updated | 3 new categories in all exhaustive tables |

## 3. Android tests (`platform/android/src/test/.../bluetooth/audio/`)

| Test class | Tests | What it proves |
|---|---|---|
| `AudioStateMappingTest` | 7 | Raw int → domain for profiles, headset audio, device types, direction-from-sink/source, blank names, profile kinds |
| `AndroidAudioTransportSourceTest` | 8 | Profile translation; HFP audio vs connection split; HSP UNKNOWN (ADR-P10-004); LE Audio only from guarded handle; refused permission → empty (no throw); device translation; callback register/unregister determinism |

The fake-handle pattern (Phase 2 §9): the handle is the only framework-touching part, so
fakes make the source's logic testable on a JVM. `BluetoothProfile`/`AudioDeviceInfo`
constants are compile-time inlined — no framework instantiation.

## 4. What is NOT covered by automated tests

- `SystemAudioTransportHandle` framework calls (binder, AudioManager) — hardware-only.
- `LeAudioApi33.bind()` service connection — hardware-only.
- Real callback timing on a device — hardware-only.

These are listed here so the suite is never misread as covering them.

## 5. Execution

Manual Kotlin/JUnit toolchain (kotlinc 2.0.21, JVM 17, `-Werror`): production and test
sources compile clean; JUnit Platform Console runs the suite. The Gradle daemon cannot run
in this sandbox (loopback TCP interception), so `./gradlew test` was not used — see
validation.md.
