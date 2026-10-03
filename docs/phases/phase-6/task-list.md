# Phase 6 — Task List

**Phase:** 6 · **Owner:** orchestrator
**Document status:** the tasks the phase ran, mapped to the code commit (`83740c5`) and the test that proves
each. A task is `done` only where a `src/test` case passes at that commit.

| id | task | deliverable | requirement | status |
|---|---|---|---|---|
| P6-T-001 | Pre-execution audit of the Phase 1/2 transport hierarchy, the layer map, error categories, and the `BleTransport` contradiction | `architecture-audit.md` | REQ-001, REQ-002 | done |
| P6-T-002 | Settle ADR-P6-001 … ADR-P6-012 | `decisions.md` | all | done |
| P6-T-003 | Transport lifecycle state machine | `TransportState.kt` | REQ-004, REQ-005 | done — `TransportStateTest` (6) |
| P6-T-004 | Authoritative `state` on `BluetoothTransport`; `isOpen` as read-through | `BluetoothTransport.kt` | REQ-004 | done — `AndroidTransportTest`, inherited `TransportKindPinningTest` |
| P6-T-005 | GATT value types + capability members | `GattAttributes.kt`, `GattTransport.kt` | REQ-003, REQ-007…009 | done — `AndroidTransportTest` |
| P6-T-006 | RFCOMM endpoint type + stream members | `RfcommEndpoint.kt`, `RfcommTransport.kt` | REQ-003, REQ-009 | done — `AndroidTransportTest` |
| P6-T-007 | Correct the `BleTransport` link-vs-control role and its stale doc | `BleTransport.kt` | REQ-002 | done (ADR-P6-004) |
| P6-T-008 | Resolver contract + safe-unknown-only implementation | `TransportResolver.kt` | REQ-014…016 | done — `TransportResolverTest` (6) |
| P6-T-009 | Platform handle seams (framework-free) | `GattTransportHandle.kt`, `RfcommTransportHandle` (in `AndroidRfcommTransport.kt`) | REQ-018 | done |
| P6-T-010 | Android transport logic (state, mutex, guard, mapping, notifications) | `AndroidGattTransport.kt`, `AndroidRfcommTransport.kt` | REQ-010…012, REQ-017 | done — `AndroidTransportTest` (12) |
| P6-T-011 | Framework-only `System*Handle` (BluetoothGatt / BluetoothSocket) | `SystemGattTransportHandle.kt`, `SystemRfcommTransportHandle.kt` | REQ-018…020 | done — compiled; instrumented-only, not run |
| P6-T-012 | Error mapping table | `TransportErrorMapping.kt` | REQ-012 | done — `AndroidTransportTest` timeout/write-failure cases |
| P6-T-013 | Transport factory (constructs, never opens) | `BluetoothTransportFactory.kt` | REQ-019 | done |
| P6-T-014 | Amend the two inherited guards that Phase 6 legitimately supersedes | `TransportBoundariesTest.kt`, `DependencyDirectionTest.kt` | REQ-006, REQ-017, REQ-019, REQ-021 | done (dated, in the open) |
| P6-T-015 | Full gate green (core JVM, platform unit, androidTest compile, lint) | `validation.md` | acceptance | done — 549 core + 102 android unit, lint clean |

## Explicitly not run (out of authorized scope)

- A **discovery scan** to populate candidate channels — deferred (ADR-P6-012; ADR-P5-012 moved the scan tag
  to 6 and this phase authorises it but exercises nothing).
- **Vendor protocol** over any channel: no framing, no opcodes, no device UUIDs, no ANC/EQ/battery/firmware/
  codec (prompt §16). The transport carries opaque bytes; a later phase decides what they mean.
- **Automatic connect / pair / reconnect** (prompt §16, §14) — the factory opens nothing; `open()` is explicit
  and there is no reconnect loop.
- **Production UI, saved-device persistence** — untouched.
- **Physical-device transport verification** — deferred to the end-of-project session (ADR-P3-014); the
  `System*Handle` instrumented tests are compiled and **`NOT RUN`**, never skipped.
