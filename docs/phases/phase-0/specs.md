# Phase 0 — Specifications (project-wide)

These specifications bind every later phase. They are conventions for code that does not exist yet; nothing here is implemented in Phase 0.
Sources: `docs/phases/phase-0/execution-prompt.md` §9, `docs/MASTER-CONTEXT.md` §§10, 15, 23, 24, 27, 30, 51, 52.

---

## 1. Naming conventions

### 1.1 Kotlin identifiers

| Kind | Convention | Example |
|---|---|---|
| Class / object | `UpperCamelCase`, noun, no `Impl` suffix on the class unless an interface actually exists | `DeviceFingerprint`, `GattTransport` |
| Interface | Bare capability noun — **no** `I` prefix, **no** `able` gimmick | `EarbudProtocol`, not `IEarbudProtocol` |
| Implementing type | Qualified by mechanism, kept out of shared layers | `AndroidGattTransport` |
| `data class` model | Noun describing state, never `Manager`/`Helper`/`Util` | `AudioTransportState` |
| Function | `verb`, returns-domain named by type not by name | `discoverCapabilities()`, `readBattery()` |
| Boolean property | `is`/`has`/`can` prefix; a boolean is never used where a state enum is required | `isReadable`, `hasCaseBattery` |
| Suspension | `suspend` for anything that may await I/O | `suspend fun writeEqualizer(...)` |

### 1.2 Files and packages

- One top-level declaration per file; file name matches the declaration.
- Package root: `omnibuds` with sub-packages by bounded area, not by Android framework habit — `domain`, `capability`, `transport`, `protocol`, `audio`, `session`, `persistence`, `diagnostics`, `platform.android.*`, `ui.*`.
- No package name may contain a vendor name unless it is a vendor protocol extension module.
- Directory names mirror packages; no `utils`, `misc`, `common2`, `new` packages.

### 1.3 Enums and states

- Enum type name is a state noun: `CapabilityState`, `CodecState`, `VerificationLevel`, `ProtocolConfidence`.
- Members are `UPPER_SNAKE_CASE`: `SUPPORTED_PERSISTENT`, `LAB_TESTED`.
- Where a value must be serialised or displayed, a stable machine key and a display label are kept separate; the display label never becomes the identity.
- Adding an enum member is an ADR-requiring change when it affects capability or codec semantics.

### 1.4 Tests

- Class: `<Subject>Test` for unit, `<Subject>ContractTest` for protocol/contract, `<Subject>IntegrationTest`, `<Subject>HardwareTest` for hardware-in-the-loop.
- Method: behavior-plus-condition, e.g. `unknownCapabilityIsNeverRenderedAsUnsupported()`.
- Every test method carries its `TEST-<SCOPE>-<NNN>` id in its name or a documented tag so the test-plan stays traceable.
- A hardware test must be recognisable as such by name and must be excluded from the default automated run.

### 1.5 Protocol and capability naming

- Protocol artefacts: `ProtocolDefinition`, `CommandDefinition`, `ResponseDefinition`, `CapabilityMapping`, `Parser`, `Encoder`. Names describe the operation's *meaning*, not its bytes: `readBatteryStatus`, never `send0x0A`.
- Vendor extensions live in a vendor-namespaced capability id (`<vendor>.<feature>`), never as a invented universal capability.
- Capability ids are stable strings held in one registry (e.g. `noiseControl.anc`, `audio.equalizer`, `input.gesture.doubleTapLeft`) — one place, referenced everywhere.
- A capability whose meaning is inferred but unproven keeps the id and carries `ProtocolConfidence = INFERRED`; the id is never renamed to look certain.

---

## 2. State conventions

### 2.1 The rule

State is modelled explicitly. A state enum is used wherever "which of these is it?" has more than two honest answers. `null` is not a general-purpose substitute for state.

### 2.2 Three representation tiers

| Tier | Use when | Absence is represented as | Forbidden |
|---|---|---|---|
| Enum state | Capability, codec, verification, session, adapter state | An explicit member: `UNKNOWN` | Collapsing `UNKNOWN` into `UNSUPPORTED` or a supported value |
| Nullable scalar | A measured quantity that may simply not be reported: battery percent, sample rate, bitrate | `null`, rendered as "unknown" | `0`, `-1`, `0%`, empty string as a stand-in |
| Typed failure | An operation could not complete | A structured error result | Thrown-and-swallowed exceptions, `runCatching { }` hiding state |

Reconciliation of master §23 ("unknown battery is `null`, not `0%`") with prompt §9.2 ("do not use `null` as a substitute for every state"): `null` is correct for *unreported measurements*, never for *capability, codec or verification status*. See ADR-P0-016.

### 2.3 Mandatory state vocabularies

```text
CapabilityState        UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE
                       | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED
CodecState             SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE
                       | CONFIGURABLE
VerificationLevel      INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED
                       | PERSISTENCE_VERIFIED
SessionState           DISCONNECTED | CONNECTING | ACTIVE_SESSION | SAVED
                       | STALE | FORGETTABLE
```

Rules:

1. Every capability record carries its state, `isReadable`, `isWritable`, transport binding, protocol binding, `requiresConnection`, persistence behavior and verification status.
2. `UNKNOWN` means "not discovered or not provable from here". `UNSUPPORTED` means "positively established that the device does not implement it". These are different statements and must never be produced from the same evidence.
3. A value may move up the ladder only on evidence of the matching tier (see `testing-governance.md`); it moves down on any failed verification, with the failure recorded.
4. `DISCONNECTED` is not `UNKNOWN`; a device that was active and went away is known to be absent.
5. Unknown values must stay unknown all the way to the UI, notification, widget and diagnostic report. No placeholder digits, no "—" promoted into a real-looking number.

---

## 3. Error conventions

Errors are structured values, not string messages, and every Bluetooth/protocol operation returns a typed result.

Canonical set (master §30; superset of prompt §9.3 — ADR-P0-012):

```text
BluetoothDisabled        PermissionDenied           DeviceDisconnected
TransportUnavailable     GattFailure                RfcommFailure
ProtocolMismatch         UnsupportedFeature         WriteRejected
VerificationFailed       Timeout                    FirmwareMismatch
CodecUnavailable
```

Rules:

1. Each category names its retry class: `SAFE_TO_RETRY` (idempotent reads, discovery), `RETRY_AFTER_REREAD` (timed-out writes), `NEVER_RETRY` (rejected or side-effecting writes whose effect is unknown).
2. Errors carry: category, operation id, transport, whether the device state is now considered suspect, and a user-facing message distinct from the diagnostic detail.
3. `UnsupportedFeature` may only be produced from a capability already established `UNSUPPORTED` — never from a missing lookup.
4. `Timeout` on a write never implies failure or success; it implies unknown state and triggers §4.
5. No error is converted into a fabricated value. A failed battery read yields `null`/unknown battery, not 0%.
6. Errors that leave the session untrustworthy mark the session suspect, forcing re-discovery before further writes.

---

## 4. Retry conventions

```text
READ operation, Timeout
  → bounded retries (default: 2 attempts, backoff), then structured Timeout error.

WRITE operation, Timeout
  → DO NOT resend the write.
  → READ the affected state.
     ├─ state matches intent      → treat as applied, record WRITE_RESULT_CONFIRMED_BY_READ
     ├─ state contradicts intent  → WriteRejected or VerificationFailed, surface to user
     └─ read also fails           → state UNKNOWN; session suspect; no automatic re-write
```

Rules:

1. Blind automatic retry of side-effecting commands is prohibited (prompt §9.5).
2. Retries are bounded, cancellable and logged with attempt count; there is no unbounded retry loop anywhere in the Bluetooth path.
3. A retry policy is declared per operation in the protocol definition, not improvised at the call site.
4. Reconnection-driven re-synchronisation must re-read state rather than replay a queued write, unless the user explicitly asks for re-application.

---

## 5. Coroutine and concurrency conventions

1. **Structured concurrency.** Every long-lived operation belongs to a scope whose owner is named. No `GlobalScope`. No launch from a repository or domain object.
2. **Dispatchers.** Bluetooth callbacks arrive on platform threads and must be funnelled through a single named dispatcher per transport; the main thread never performs Bluetooth, parsing or file work. Blocking client APIs are confined to the transport implementation and exposed as suspending functions.
3. **Cancellation.** All suspending transport/protocol operations honor cancellation and must terminate their native handle cleanup; a cancelled operation reports CANCELLED, not success.
4. **Timeouts.** Every protocol operation declares a timeout in its definition. A timeout is a `Timeout` error, never a silent null.
5. **Flow.** Observables state (adapter state, session set, capability map, battery) is exposed as `StateFlow` for "latest value matters" and as `SharedFlow` for events; cold flows are not used for device state. Every flow declares buffering and overflow behavior.
6. **StateFlow for state, not for commands.** A user command is a function call returning a typed result; it is never modelled as an emitted value into a state flow.
7. **Shared mutable state.** Confined to one owner per area (the session/state engine owns device state; the capability engine owns capability state). Other components read, never write. Mutation sites are auditable by grepping the owner.
8. **Lifecycle-bound work.** UI-scoped collection ends with the lifecycle owner. Background work runs only under a documented foreground/service condition permitted by Android, and the app reports honestly when the platform forbids the desired behavior.
9. **Serialization.** Writes to a single device are serialised per control channel; concurrent writes on one channel are prohibited because response correlation becomes impossible.

---

## 6. Documentation and traceability conventions

1. IDs follow `design.md` §7 grammar; references across documents are by ID.
2. A phase record is created from `docs/templates/`, never from a previous phase's prose.
3. `validation.md` must state, for each requirement: done / partial / not started, plus platform and hardware limitations and deferred items.
4. No document may claim a capability state higher than the evidence tier defined in `testing-governance.md`.
5. Illustrative pseudo-code in documents must be fenced and labelled as illustrative; it is never a file on disk.
