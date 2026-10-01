<!--
TEMPLATE: design.md — copy to docs/phases/phase-<N>/design.md
DERIVES: docs/MASTER-CONTEXT.md §42 (mandatory contents list), §40, §49 (design precedes implementation), §51 (quality rules / avoided patterns);
         docs/phases/phase-0/execution-prompt.md §8.1 architecture hierarchy, §8.2 dependency direction, §8.3 Android isolation, §8.4 future KMP compatibility.
COMPLETION RULES:
  R1 Every section below is mandatory; write "NONE — <reason>" instead of deleting a heading.
  R2 Each design decision names the MASTER-CONTEXT / Phase 0 section it preserves, or an ADR id if it changes one.
  R3 Diagrams are required where a table or prose cannot show flow, state, or dependency clearly.
  R4 Anything not yet proven is labelled UNKNOWN; design must not depend on an assumed protocol, codec, or platform behaviour.
  R5 Contradictions with the master architecture are escalated to decisions.md, never resolved silently (MASTER-CONTEXT §38, §58).
-->

# Phase `<N>` — Design

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Owner agent:** `<agent>`
**Requirements covered:** `REQ-<SCOPE>-<NNN>` … · **Specs:** `specs.md` · **Decisions:** `decisions.md`

## 1. Architecture

Where this phase sits in the project hierarchy (Phase 0 §8.1) and why.

```text
Product → Architecture → Core abstractions → Platform implementations → Vendor protocols → Device implementations
```

`<diagram required: current phase highlighted, plus the layers it may touch>`

## 2. Modules

| Module | Layer | Purpose | Owned by agent | May depend on | Must NOT depend on |
|---|---|---|---|---|---|
| `<module>` | `<core / platform / protocol / ui>` | `<single responsibility>` | `<agent>` | `<modules>` | `<modules, incl. reason>` |

## 3. Responsibilities

Per module: what it owns, what it explicitly does not own. No giant manager classes, no UI-driven business logic, no duplicated transport logic (MASTER-CONTEXT §51).

## 4. Data flow

`<diagram required>` from device/platform source → transport → protocol → capability/state model → consumers. Mark which values may be `UNKNOWN` at each hop and how unknown is preserved rather than collapsed to `false`, `0`, or `UNSUPPORTED`.

## 5. State transitions

State machine(s) for this phase, with the canonical enums used unchanged:
- `CapabilityState`: `UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED`
- `CodecState`: `SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE`
- Verification: `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED`

Table every edge: from-state, trigger, guard, to-state, side effects, who may not take the edge (e.g. nothing reaches `PERSISTENCE_VERIFIED` without a reconnect read-back).

## 6. Interfaces

Public contracts this phase exposes and consumes; signature-level intent lives in `specs.md`.

## 7. Dependencies

Dependency direction (Phase 0 §8.2): `UI → Application/State → Core Domain → Protocol/Capability abstractions → Platform transport implementations`. Vendor protocol code must not own global application state. Third-party/library dependencies: `<list or NONE>`.

## 8. Error handling

Use the project error categories unchanged: `BluetoothDisabled`, `PermissionDenied`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `ProtocolMismatch`, `UnsupportedFeature`, `WriteRejected`, `VerificationFailed`, `Timeout`, `FirmwareMismatch`, `CodecUnavailable`. State propagation vs suppression, and the read/write retry asymmetry (see `specs.md`).

## 9. Lifecycle

Session start → discovery → ready → disconnect → restore; background/foreground, app closed, adapter off/on, reboot, case open, reconnect (MASTER-CONTEXT §35) within Android's permitted behaviour.

## 10. Concurrency

Coroutine scopes, dispatchers, cancellation and timeout ownership, shared mutable state, Flow/StateFlow surfaces, and the rule that no blocking Bluetooth work runs on the main thread.

## 11. Security considerations

Permissions, device identifiers and retention, arbitrary writes to unknown characteristics, packet logging and diagnostics content, authorised protocol research only (MASTER-CONTEXT §25, §26).

## 12. Platform limitations

Android API levels, OEM Bluetooth stack variance, background execution limits, Quick Settings/notification constraints, codecs the OS will not let us configure. Never promise functionality the platform does not permit (MASTER-CONTEXT §4).

## 13. Future extensibility

Android isolation (Phase 0 §8.3) and KMP boundary (§8.4): shared candidates `DeviceIdentity`, `DeviceFingerprint`, `Capability`, `DeviceState`, `AudioState`, protocol definitions, packet parsing, state machines, persistence rules, validation logic, diagnostics models; platform-specific: Bluetooth transport, Android permissions, Quick Settings, notifications, desktop Bluetooth. New transports/vendors/codec states must be addable without touching core.

## 14. Decision-to-master traceability

| Design point | Preserves (master § / Phase 0 §) | ADR id | Alternatives rejected |
|---|---|---|---|
| `<point>` | `<§n>` | `ADR-<SCOPE>-<NNN>` | `<why>` |

## 15. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §42 design contents list, §40 phase structure, §51 quality rules and avoided patterns, §8 transport abstraction, §10 capability engine, §15 codec states, §30 error engine, §35 background behaviour, §38 sub-agent rules, §58 conflict handling.
- `docs/phases/phase-0/execution-prompt.md` — §8.1–§8.4 hierarchy, dependency direction, Android isolation, KMP compatibility; §21 review checklist.
- `docs/phases/phase-0/design.md` — Phase 0 instance of this template.
- `docs/templates/specs-template.md`, `docs/templates/decisions-template.md` — companion documents.
