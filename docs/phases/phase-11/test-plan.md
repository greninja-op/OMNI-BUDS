# Phase 11 — Codec Capability Engine: Test Plan

## 1. Strategy

JVM unit tests for everything testable without hardware; architecture tests for
boundaries; explicit non-coverage for framework calls. Deterministic fakes behind
ports — production code reports only platform evidence (RULE 16).

## 2. Core tests (`core/src/test/.../codec/`)

| Class | N | Proves |
|---|---|---|
| `CodecDomainTest` | 6 | All 10 identities; aptX variants separate; LC3→LE_AUDIO; classic→CLASSIC_A2DP; display≠identity |
| `CodecCapabilityStateTest` | 9 | SUPPORTED≠ACTIVE; AVAILABLE≠ACTIVE; ENABLED≠NEGOTIATED; NEGOTIATED≠ACTIVE; CONFIGURABLE orthogonal; UNKNOWN≠UNSUPPORTED; enum≠support; evidence defaults; isActive exact |
| `CodecMetadataTest` | 8 | Unknown stays unknown; no 0/16/stereo defaults; bitrate never invented; exact/adaptive preserved; quality defaults UNKNOWN |
| `CodecEvidenceTest` | 4 | Observation outranks inference; NOT_OBSERVABLE≠unsupported; details carry no secrets; unknown default |
| `CodecCapabilityEngineTest` | 11 | Start publishes; A/B isolation; stop→STALE; reconnect re-observes; B survives A disconnect; refresh guards; failed start; idempotence; immutability; transport from family; limitations recorded |
| `CodecScopeTest` | 4 | No switching vocabulary; no interception vocabulary; no android imports; no invented bitrates |

## 3. Android tests (`platform/android/src/test/.../audio/codec/`)

| Class | N | Proves |
|---|---|---|
| `CodecMappingTest` | 6 | Both constant families map; LC3→LE_AUDIO (never A2DP); unknown→null; Adaptive/Lossless have no constant |
| `AndroidCodecObservationSourceTest` | 6 | Empty→all-UNKNOWN/NOT_OBSERVABLE; list→SUPPORTED/OBSERVED; unlisted→UNKNOWN (not UNSUPPORTED); bad ids dropped; runtime null; all 9 required codecs represented |

## 4. Not covered (explicit)

- `SystemCodecObservationHandle` binder calls — hardware-only.
- `CodecApi35` service connection — hardware-only.
- Real API-35 `getSupportedCodecTypes()` values — hardware-only.

## 5. Execution

Manual Kotlin/JUnit toolchain (kotlinc 2.0.21, JVM 17, `-Werror`); JUnit Platform
Console 1.10.1. Gradle daemon unusable in sandbox (loopback TCP interception) —
see validation.md.
