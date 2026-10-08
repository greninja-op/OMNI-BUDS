# Phase 9 — Design

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted

## 1. Architecture

```text
Device Session
      |
Capability Snapshot (handed in per call; never cached — capability truth is not ours)
      |
Hardware Feature Engine (com.omnibuds.core.feature, layer 5)
      |
      +-----------------------+
      |                       |
      v                       v
Feature Validator       Feature State Repository
(10-step gate)          (single owner, StateFlow)
      |
      v
Feature Protocol Port (handed-in seam; a later phase binds it to a protocol)
      |
Transport (unseen from the feature layer)
```

The engine owns the *operation lifecycle* and nothing else. Capability truth
stays with discovery, transport truth with the transport layer, protocol truth
with the protocol session (ARCH-LAYER-003). The feature area is layer 5 in the
architecture test's layer map: it may depend on any lower layer, and nothing
below it may depend on it.

## 2. What was reused vs. what is new

| Need | Decision |
|---|---|
| Feature identity | Reuse `FeatureId` (common), incl. `ofVendor` namespacing |
| Capability truth | Consume handed-in `CapabilitySnapshot` / `DeviceCapabilities` / `FeatureCapability`; never cache |
| Support rungs | Reuse `CapabilityState`; the new control axis is a separate sealed type (ARCH-TERM-003) |
| Value shapes | **Extend** `ConfigurationValue` with `FloatValue`, `RangeValue`, `StructuredValue`, `BitmaskValue`, `CustomValue` (ADR-P9-001) — no forked hierarchy |
| Read/write mechanism | Handed-in `FeatureProtocolPort` seam mirroring `CapabilityDiscoverySource` (ADR-P9-002); later bound to `FeatureReadSupport`/`FeatureWriteSupport` |
| Requires-edges | Reuse `CapabilityDependency` + `DependencyValidator`, including its cycle detector |
| Error taxonomy | `FeatureErrorCode` maps onto existing `OmniBudsErrorCategory` (ADR-P8-004 precedent); retry via `RetryClass` |
| Feature categories | Reuse `FeatureCategory`; vendor features use `VENDOR` |
| Time | `TimeProvider` seam where timing matters |

Genuinely new: `FeatureDefinition`, `FeatureValueType`, `FeatureConstraints`,
`FeatureAccess`, `FeatureState` + `FeatureStateTransitions`,
`FeatureOperation` + `SideEffectClass`, `FeatureValidator`,
`FeatureRelation` (six kinds) + `FeatureDependencyEvaluator`,
`FeatureStateRepository` + `InMemoryFeatureStateRepository`, `FeatureEngine`,
`StandardFeatures` catalogue (+ `EqualizerValues`, `GestureValues`),
`VendorFeatureContract`.

## 3. The state model

`FeatureState` is a sealed interface with six states:

- `Unknown(feature, lastConfirmed?)` — nothing currently established.
- `Available(feature, lastConfirmed?)` — capability established; operable.
- `Pending(feature, requested, lastConfirmed?, operationId)` — a write is in
  flight. `requested` is explicitly **not** the device state.
- `Confirmed(feature, value)` — the device reported `value`; authoritative.
- `Failed(feature, error, lastConfirmed?)` — the last operation did not achieve
  its goal; the error carries the retry behaviour.
- `Unavailable(feature, reason, lastConfirmed?)` — supported but not usable now.

`lastConfirmed` is stale knowledge wherever it appears outside `Confirmed` —
the UI may show it as "last known", never as current. `FeatureStateTransitions`
states the legal moves as a table; the repository refuses anything else. In
particular `Unknown → Pending` is illegal: a write requires an established
capability, which the validator proves before the engine re-asserts
`Available` and goes pending.

This reconciles the prompt's §2 rule 3 vocabulary (requested / device-confirmed
/ failed / unknown) onto the §9 six-state model (ADR-P9-003), and keeps the new
axis separate from `CapabilityState`, `ConnectionState`, `TransportState` and
`ProtocolState`.

## 4. The operation lifecycle

**Write:** validate (10 steps) → `Pending` → port write under timeout →
mandatory read-back:

- read-back equals request → `Confirmed`, success;
- read-back differs → `Confirmed(device value)`, failure
  `STATE_VERIFICATION_FAILED` (the goal failed but the truth is known);
- read-back failed → `Unknown`, failure `DEVICE_STATE_UNKNOWN` (never a guess);
- write timed out → **never re-sent**; one read-back decides: value as
  requested confirms (attribution not claimed); divergent confirms the device
  value and reports `OPERATION_TIMEOUT`; failed read leaves `Unknown` and
  reports the timeout (PROTO-ERR-002).

**Read:** validate → port read under timeout → shape-check the untrusted value
→ `Confirmed`, or `Failed` keeping `lastConfirmed` (a transient failure does
not erase device truth), or `Failed(MALFORMED_RESPONSE)` for uninterpretable
values.

**Validation failures** record `Failed` and never touch the port.
**Cancellation** restores the pre-attempt state (unless a device report arrived
meanwhile — device truth wins) and rethrows; cancellation is
`OperationOutcome.Cancelled`, never a failure (ADR-P1-004).
**Session invalidation** advances an epoch: in-flight completions report
`DEVICE_DISCONNECTED` without touching state, and every tracked feature moves
to `Unknown` keeping `lastConfirmed`.

**Staleness:** every operation captures the feature's generation;
`onDeviceReported` bumps it. A completion with a superseded generation leaves
the newer device-reported state alone.

**Concurrency:** one operation per feature at a time (per-feature mutex); a
second write waits, then validates against fresh state. Different features run
concurrently.

## 5. Validation pipeline (10 steps)

1. Definition exists → else `FEATURE_UNKNOWN`.
2. Capability not `UNSUPPORTED` (→ `FEATURE_UNSUPPORTED`) and not `UNKNOWN`
   (→ `FEATURE_UNKNOWN`).
3. Derived access permits the operation type (→ `FEATURE_READ_ONLY` /
   `FEATURE_WRITE_UNSUPPORTED` / `FEATURE_UNAVAILABLE`).
4. Value shape + constraints (→ `INVALID_VALUE`; never clamped).
5. Dependencies satisfied for the operation type (→ `DEPENDENCY_NOT_SATISFIED`).
6. No declared conflict blocks (→ `FEATURE_CONFLICT`).
7. Port implements the operation (→ `PROTOCOL_UNAVAILABLE`;
   subscribe/unsubscribe/reset → `OPERATION_NOT_IMPLEMENTED`, declared but
   Phase-9-unimplemented).
8. Transport established for the feature (→ `TRANSPORT_UNAVAILABLE`).
9. Operation construction (`FeatureOperation` factory).
10. Execution (the engine; never the validator).

## 6. Dependencies and conflicts

Relations are definition-level facts, explicitly established by declaring them
— never universal hardware truths. `Requires` edges are evaluated through
`DependencyValidator` (shared semantics with discovery: missing is explicit,
unknown is not missing, cycles are reported loudly). `RequiresOneOf`,
`ConflictsWith`, `MutuallyExclusive`, `Implies` and `VendorException` are
evaluated here against the snapshot plus live control state. Cycle detection
sees *all* definitions' relations, because a cycle needs an edge into the
feature that lives on another definition.

Conflict activity follows the documented `isActiveValue` convention: boolean
`true`, a mode whose technical name is not `"off"`, a non-zero number are
"engaged"; anything else confirmed is conservatively engaged. Definitions use
the technical name `"off"` for the inactive mode (`INACTIVE_MODE_NAME`).

The evaluator never enables a prerequisite and never disables a conflicting
feature — a blocked operation is refused with its explanation.

## 7. Vendor extensions

`VendorFeatureContract.define` creates definitions under
`vendor.<vendor>.<feature>` in the `VENDOR` category, refused otherwise.
Vendor features run through the identical engine/validator/state machinery;
shared code never branches on the vendor segment (ADR-P0-007). Opaque
manufacturer payloads ride as `CUSTOM` values — length-bounded, never parsed
by the engine. No vendor commands, opcodes, UUIDs or packet layouts exist in
Phase 9.

## 8. The standard catalogue

Nineteen definitions: six noise-control (switch, mode, level, adaptive, wind,
environment), four transparency (switch, level, auto, voice pass-through), one
structured equalizer (preset/graphic/parametric/tone via the `form` field —
bands are values, not identities, per the `CoreFeature` precedent), one
structured gesture contract (documented, unenforced action vocabulary), wear
detection, multipoint, spatial audio, head tracking, gaming mode, voice
prompts, sidetone. Numeric bounds are stated only where universal (safety
limits); device-specific bounds are left to discovery. Relations are declared
only where safe (`anc-level` requires `anc`; `anc` ↔ `anc-mode` conflict).

## 9. UI contract (no UI)

`observe(feature)` exposes per-feature state as a `Flow`; the repository's
`StateFlow` map exposes everything. A future UI can answer: is it supported
(snapshot), is it writable (access), what is confirmed, is a change pending,
was the request rejected, is the state unknown — with no UI logic in the
engine. UI arrives at Phase 49.

## 10. What Phase 9 deliberately does not do

No vendor protocol implementation, no vendor commands, no phone-side simulated
effects, no audio-path code, no persistence of device state, no production UI,
no firmware updates, no hardware contact of any kind. The registries and ports
ship empty/scripted-only; the honest ceiling is IMPLEMENTED.
