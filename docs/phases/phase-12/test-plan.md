# Phase 12 — Test Plan

**Status:** Executed. All tests pass.

---

## Toolchain

kotlinc 2.0.21, JVM 17, `-Werror`, JUnit Platform Console Standalone 1.10.1,
`kotlinx-coroutines-test`. Same manual toolchain as Phases 9–11 (Gradle daemon
unusable in sandbox).

## Domain tests (`core/.../codec/`)

| File | Covers |
|---|---|
| `CodecControlCapabilityTest` | 5 dimensions, LDAC valid combos, independence |
| `CodecOperationTest` | value validation (9 tests) |
| `CodecOperationResultTest` | `isConfirmed` only for Verified; terminal refusals |
| `CodecControlEngineTest` | transactions, verification, requested-vs-confirmed (15 tests) |
| `CodecControlConcurrencyTest` | serialization, multi-device isolation |
| `CodecControlTimeoutTest` | TIMEOUT never success |
| `CodecControlCancellationTest` | cancellation safety |
| `CodecControlRollbackTest` | rollback / uncertain state |
| `PerCodecControlTest` | AAC, LDAC, aptX ×4, LC3, SBC, UNKNOWN distinctions |
| `CodecControlScopeTest` | forbidden vocabulary (6 tests) |

## Android tests (`platform/android/.../codec/`)

| File | Covers |
|---|---|
| `AndroidCodecControlAdapterTest` | NotAvailable honesty, null observation, resolver all-false (6 tests) |

## Fakes

`CodecControlTestFakes`: `FakeResolver` (scripted capabilities),
`FakeAdapter` (scripted outcomes, delays), `FakeLiveness`. Fakes never enter
production code.

## Key scenarios verified

- Request LDAC → observation stays AAC → `VERIFICATION_FAILED`, confirmed ≠ LDAC
- `NONE` strategy → `APPLIED_UNVERIFIED`, confirmed stays UNKNOWN
- Hanging adapter → `TimedOut`
- Cancelled op → `CancellationException`, nothing committed
- Disconnect mid-transaction → `DeviceDisconnected`
- Concurrent selects on one device → serialized, never interleaved
- Device A (LDAC) / Device B (AAC) → isolated
- LC3 + CLASSIC_A2DP → `Rejected`
- AAC + qualityMode → `Rejected` (field unsupported)
- `UNKNOWN ≠ UNSUPPORTED ≠ FAILED`; `NOT_CONFIGURABLE ≠ UNSUPPORTED`

## Regression

Full core suite + Android suite green (see validation.md).
