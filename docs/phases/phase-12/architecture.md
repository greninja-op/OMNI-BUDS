# Phase 12 — Codec Control: Architecture

**Status:** Authoritative for Phase 12.

---

## Module map

```
core/codec/                          (layer 3 — extended in Phase 12)
├── CodecControlCapability.kt        # 5 independent dimensions + evidence
├── CodecConfiguration.kt            # normalized, all-nullable config
├── CodecConfigField.kt              # field enum
├── CodecFieldSupport.kt             # per-codec field sets (LDAC: QUALITY_MODE)
├── CodecOperationType.kt            # SELECT/CONFIGURE/ENABLE/DISABLE/RESET/REFRESH
├── CodecOperation.kt                # operation value (validated, caller operationId)
├── CodecOperationResult.kt          # 14-state sealed result hierarchy
├── CodecVerificationStrategy.kt     # PLATFORM_OBSERVATION … NONE
├── CodecControlCapabilityResolver.kt# port: canObserve/Select/Configure/Verify
├── CodecControlAdapter.kt           # port: apply() + observeAfterApply()
├── CodecControlState.kt             # requested vs observed vs confirmed
└── CodecControlEngine.kt            # transaction engine

core/common/
├── SideEffectClass.kt               # MOVED from feature (Phase 12): layer 0
└── OmniBudsErrorCategory.kt         # + CODEC_OPERATION_FAILED

platform/android/.../bluetooth/audio/codec/
├── AndroidCodecControlAdapter.kt            # honest: apply() → NotAvailable
└── AndroidCodecControlCapabilityResolver.kt # honest: selectable/configurable/verifiable = false
```

## Dependency notes

- `codec` (layer 3) uses `SideEffectClass` from `common` (layer 0). It was
  moved out of `feature` (layer 5) in Phase 12 because layer 3 may not depend
  on layer 5 (`DependencyDirectionTest`).
- `feature` keeps working unchanged via a direct import of the moved class.
- The engine depends on the two ports (`CodecControlCapabilityResolver`,
  `CodecControlAdapter`) — never on Android types, never on raw packets.

## Control flow

```
Caller
  │  CodecOperation (value)
  ▼
CodecControlEngine.execute()
  │  PRECHECK (resolver + liveness + validation)
  │  per-device Mutex
  ▼
CodecControlAdapter.apply()          ← Android: NotAvailable
  │  RE-OBSERVE
  ▼
  VERIFY (strategy-gated)
  │  COMMIT only on Verified
  ▼
CodecOperationResult (structured)
```

## What the engine never does

- Never touches `android.*` (scope-tested).
- Never uses reflection, shell, system properties (scope-tested).
- Never blocks a thread (`Thread.sleep` banned; `withTimeout` used).
- Never retries a side-effecting write blindly.
- Never converts `UNKNOWN` to `UNSUPPORTED`.
- Never reports requested state as confirmed.
- Never swallows `CancellationException`.
