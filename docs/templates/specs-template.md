<!--
TEMPLATE: specs.md — copy to docs/phases/phase-<N>/specs.md
DERIVES: docs/MASTER-CONTEXT.md §43 (implementation-level spec list, "do not leave important behaviour implicit"), §27 (protocol database + confidence),
         §28 (protocol DB vs device DB), §30 (error engine, read/write retry asymmetry), §52 (no magic protocol values);
         docs/phases/phase-0/execution-prompt.md §9.1 naming, §9.2 state, §9.3 errors, §9.4 coroutines, §9.5 retry asymmetry, §15 protocol research rules.
COMPLETION RULES:
  R1 Every section is mandatory: fill it or write "NONE — <reason>". Deleting a heading is a review failure.
  R2 Each spec traces to at least one REQ-<SCOPE>-<NNN>; each numeric limit, default, and enum value is stated explicitly.
  R3 Canonical enums are copied verbatim; no local synonyms (no "enabled=yes", no "supported=true", no bare null as a state).
  R4 Undiscovered behaviour is specified as UNKNOWN pending evidence, never as a guessed default.
-->

# Phase `<N>` — Specs

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Owner agent:** `<agent>`

## 1. Naming conventions (Phase 0 §9.1)

Kotlin classes / interfaces / enums / files / packages / tests / protocol names / capability names. Record deviations here, not in code comments.

| Kind | Rule | Example placeholder |
|---|---|---|
| Capability | `<UPPER_SNAKE or PascalCase>` | `ANC`, `TRANSPARENCY` |
| Protocol | `<vendor>-<family>-v<version>` | `<vendor-protocol-vN>` |
| Test | `TEST-<SCOPE>-<NNN>` + `<ClassTest>` | `TEST-BT-005 AdapterStateTest` |

## 2. State conventions (Phase 0 §9.2)

`CapabilityState`: `UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED`
`CodecState`: `SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE`
`VerificationLevel` / `ProtocolConfidence`: `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED`
Where relevant also enumerate: `UNAVAILABLE`, `DISCONNECTED`. `null` is never a substitute for a state; unknown information stays `UNKNOWN` (MASTER-CONTEXT §53).

## 3. API contracts

Per operation: name, inputs, output, effect type (`read` / `write`), idempotency, required CapabilityState, errors, and the contract's UNKNOWN-outcome behaviour.

## 4. Data models and enums

Fields, types, optionality, allowed values, defaults (defaults may only be `UNKNOWN`/absent, never fabricated), and which model owns them. Device fingerprint fields per MASTER-CONTEXT §7; audio transport state per §16 — unknown values stay unknown, never fake numbers.

## 5. State machines

States, events, guards, transitions, illegal transitions and their error, initial state, terminal state. Persistence may only reach `PERSISTENCE_VERIFIED` through the §12 read-back/reconnect sequence.

## 6. Interfaces

Interface-level contracts (e.g. transport and protocol abstractions per MASTER-CONTEXT §8, §9): method intent, suspend-ness, failure surface, what an implementation must NOT assume (never assume every device uses BLE/GATT).

## 7. Persistence schema

Records, keys, ownership (user device DB vs global protocol DB stay separate, MASTER-CONTEXT §28), retention of identifiers, migration rules, and what is never persisted.

## 8. Transport contracts

Per transport: connect, read, write, notify, teardown, timeout, backpressure, and the error category raised when the transport is unavailable (`TransportUnavailable`, `GattFailure`, `RfcommFailure`).

## 9. Protocol contracts

Structural representation only — `ProtocolDefinition`, `CommandDefinition`, `ResponseDefinition`, `Parser`, `Encoder`, `CapabilityMapping` (MASTER-CONTEXT §52). Per protocol record the §27 database fields: manufacturer, model, fingerprint rules, transport, protocol, protocol version, firmware compatibility, service UUIDs, characteristics, commands, responses, parsers, encoders, capabilities, persistence behavior, known limitations, test status, and `ProtocolConfidence` (`INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED`). No purpose assumed for a UUID or byte without evidence.

## 10. Error contracts

Categories (exact set, MASTER-CONTEXT §30): `BluetoothDisabled`, `PermissionDenied`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `ProtocolMismatch`, `UnsupportedFeature`, `WriteRejected`, `VerificationFailed`, `Timeout`, `FirmwareMismatch`, `CodecUnavailable`. Payload fields, mapping to user-visible state, logging rules.

## 11. Threading, coroutine and Flow behaviour

Dispatcher per operation, structured-concurrency scope, cancellation propagation, timeout ownership, shared-state protection, Flow/StateFlow type, replay/conflation, completion and error semantics, lifecycle-bound work. Blocking Bluetooth calls off the main thread.

## 12. Timeouts, retries and validation rules

| Operation class | Timeout | Retry policy | Retry budget | Read-back required | Validation rule |
|---|---|---|---|---|---|
| Read (idempotent) | `<ms>` | controlled retry allowed | `<n>` | n/a | `<rule>` |
| Write (side-effecting) | `<ms>` | **no blind resend** | `0` | yes | `<rule>` |

**Read-versus-write asymmetry (mandatory sub-field):** a side-effecting write that times out must never be resent blindly. Prescribed recovery (MASTER-CONTEXT §30; Phase 0 §9.5): `SET <feature>` → `Timeout` → do NOT resend → `READ <feature>` → determine actual state → report `WriteRejected` / `VerificationFailed` / success.
**Persistence verification sequence (mandatory where persistence is claimed):** `READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY` (MASTER-CONTEXT §24).

## 13. Do-not-leave-implicit checklist

- [ ] Every enum state has a stated meaning and a stated UI/telemetry consequence.
- [ ] Every timeout, interval, budget and default is numeric and owned by a section here.
- [ ] Every error category has a defined trigger and propagation path.
- [ ] Every write has a defined read-back and a defined failure behaviour.
- [ ] Every unknown value has a defined representation (`UNKNOWN`), never `false` / `0` / `"unsupported"`.
- [ ] Each spec maps to a REQ id and a TEST id; orphans are fixed before implementation starts.

## 14. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §43 specs contents list, §27 protocol database, §28 device vs protocol DB, §30 error engine and retry asymmetry, §24 persistence verification, §52 no magic protocol values, §53 unknown remains unknown.
- `docs/phases/phase-0/execution-prompt.md` — §9.1–§9.5 naming/state/error/coroutine/retry conventions, §15 protocol research rules, §16 hardware verification levels.
- `docs/phases/phase-0/specs.md` — Phase 0 instance of this template.
- `docs/templates/design-template.md`, `docs/templates/test-plan-template.md` — companion documents.
