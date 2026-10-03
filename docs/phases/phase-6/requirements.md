# Phase 6 — Requirements

**Phase:** 6 — Bluetooth Transport Layer · **Scope id:** `OB-P6`
**Document status:** authored against the committed tree at `83740c5` (`feat(core,platform): give the
transport boundaries a lifecycle, an operation surface, and a mechanism`). Acceptance-criteria references
name tests that exist and pass at that commit. `docs/phases/phase-6/decisions.md` holds ADR-P6-001 … 012;
requirements cite them by id and never restate them.

**The condition this phase ships in.** Phases 1/2 built the transport *boundaries* and left every `open`/
`close`/`exchange`/`probeAvailability` unimplemented, naming Phase 6 as their owner. Phase 6 supplies the
lifecycle machine, the deferred per-channel operation surface, the resolver contract, the concurrency and
error discipline, and the Android mechanism behind a seam — **written, unit-tested against scripted
handles, and never run on a radio** (ADR-P6-008). Every capability caps at `IMPLEMENTED`; no transport is
proven against a device (deferred to the end-of-project session, ADR-P3-014), and no channel is claimed
usable.

Priority: `P0` blocks the boundary · `P1` in scope, required · `P2` in scope, not boundary-blocking.

## Transport taxonomy and structure

### OB-P6-REQ-001 — Reuse the Phase 1/2 transport hierarchy; no parallel interface tree, no new area
The lifecycle, resolver and mechanism are added to `com.omnibuds.core.transport` (L1) and the platform
adapters to `com.omnibuds.android.bluetooth.transport`; `TransportContract`/`BluetoothTransport` and the
five sub-interfaces stay the single hierarchy.
**Rationale.** Prompt §2 "identify existing transport work / reuse, do not duplicate." ADR-P6-001.
**Priority.** P0 · **Dependencies.** none · **Verification.** `TransportBoundariesTest` still passes;
`DependencyDirectionTest` unchanged for the area count.

### OB-P6-REQ-002 — The taxonomy keeps physical link, profile, control channel and audio path distinct
`TransportKind` describes control channels only; `AudioTransportKind`/`AudioTransportState` remain the
media vocabulary; A2DP/AVRCP/HFP have no `TransportKind`; a BLE control channel is GATT and `BleTransport`
is the link-availability boundary.
**Rationale.** Prompt §7's "do not treat every item as interchangeable"; PROTO-XPORT-001/-002; ADR-P3-002;
ADR-P6-004.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-001 · **Verification.** `TransportKindPinningTest` (each
boundary pins one kind, none is `UNKNOWN`/`VENDOR_SPECIFIC`).

### OB-P6-REQ-003 — Capability-specific interfaces, never one oversized `DeviceTransport`
GATT mechanics live on `GattTransport` (discover/read/write/subscribe/notifications/mtu); RFCOMM stream
mechanics on `RfcommTransport` (endpoint/read/write); shared lifecycle on the root.
**Rationale.** Prompt §6 "do not create one oversized interface"; ADR-P6-003.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-001 · **Verification.** `AndroidTransportTest`
`.gattExchangeIsRefused...` proves GATT answers only its own questions.

## Lifecycle

### OB-P6-REQ-004 — A transport has one authoritative lifecycle state and never reports connected before confirmation
`TransportState` + `TransportStateTransitions`, exposed as `state: StateFlow<TransportState>`; `open()`
runs `IDLE → CONNECTING → CONNECTED` and reaches `CONNECTED` only on the platform's success.
**Rationale.** Prompt §8; ADR-P6-002; ADR-P4-002's single-authority rule.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-001 · **Verification.** `TransportStateTest` (6),
`AndroidTransportTest.openDrivesTheStateMachineToConnected`, `.aRefusedConnectLandsOnFailedNotConnected`.

### OB-P6-REQ-005 — Valid and invalid transitions, repeated operations and cancellation are defined
Terminal `CLOSED`/`UNAVAILABLE` refuse revival; `FAILED`/`DISCONNECTED` are reachable from any live state;
self-transitions are idempotent; `close()` on a closed channel is a success, not an error.
**Rationale.** Prompt §8's list; ADR-P6-002.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-004 · **Verification.** `TransportStateTest`
`.aClosedChannelDoesNotReopenItself`, `.stayingPutIsAlwaysLegal`, `AndroidTransportTest.repeatedCloseIsIdempotentSuccess`.

## Contracts and operations

### OB-P6-REQ-006 — Core transport interfaces name no Android type and are not implemented in `:core`
Only the platform implements the contracts; `:core` holds interfaces and value types.
**Rationale.** Prompt §6; ADR-P1-013; the layer map (transport L1 may not import a platform type).
**Priority.** P0 · **Dependencies.** OB-P6-REQ-001 · **Verification.** `TransportBoundariesTest`
`.noPhaseTwoTransportCodeOpensOrProbesAChannel` (framework symbols confined).

### OB-P6-REQ-007 — GATT read/write/notify/discover are addressed by caller-supplied identity, never a baked-in UUID
Characteristic/service identifiers are parameters; the transport never holds a device-specific UUID.
**Rationale.** Prompt §9 "no device-specific GATT services/commands"; PROTO-NOMAGIC-002; ADR-P6-003.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-003 · **Verification.** `AndroidTransportTest.writeToANonWritableCharacteristicIsRejected...`,
`.writeExceedingTheNegotiatedMtuIsRefusedNotFragmented`.

### OB-P6-REQ-008 — No transport operation is implied to be safe against a device; writes report delivery only
`TransportResponse.acknowledged` is delivery-level (rung 2); no state claims a value applied.
**Rationale.** PROTO-PERSIST-001; master §24; prompt §8.
**Priority.** P1 · **Dependencies.** OB-P6-REQ-007 · **Verification.** `TransportResponseTest` (Phase 1) +
the write path in `AndroidTransportTest` returning `Unit`, not an applied-value claim.

### OB-P6-REQ-009 — A generic byte-exchange exists for stream channels and is honestly refused where it does not apply
`exchange` is GATT-refused (addressed per characteristic) and real for RFCOMM (write whole, read one run),
with no framing imposed on the stream.
**Rationale.** Prompt §6/§10; PROTO-ABST-006; ADR-P6-003.
**Priority.** P1 · **Dependencies.** OB-P6-REQ-003 · **Verification.** `AndroidTransportTest`.
`gattExchangeIsRefused...`, `.rfcommExchangeWritesWholeThenReadsOneRun`.

## Concurrency, resources, errors

### OB-P6-REQ-010 — One operation in flight per channel, with cancellation-safe and idempotent teardown
`Mutex` serialises operations inside the state check; teardown runs even on cancellation; a closed channel
rejects new work.
**Rationale.** Prompt §14; specs §5.9; ADR-P6-007.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-004 · **Verification.** `AndroidTransportTest.operationOnAnIdleChannelIsRefusedNotQueued`.

### OB-P6-REQ-011 — No leaked socket, GATT client or notification collector, and no auto-reconnect
Handles are released once; a notification collector's end cancels the platform subscription; a dropped
channel is reported, never silently reconnected.
**Rationale.** Prompt §14; ADR-P6-007, ADR-P6-011.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-010 · **Verification.** `AndroidTransportTest.cancellingANotificationSubscriptionReleasesThePlatformRegistration`.

### OB-P6-REQ-012 — Structured transport errors reuse the existing category set with its retry class intact
No new error category; the platform maps raw statuses to existing categories; a timeout never implies the
operation had no effect.
**Rationale.** Prompt §13; ADR-P2-004; ADR-P3-006 precedent; ADR-P6-006.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-006 · **Verification.** `AndroidTransportTest.anUnresolvedConnectIsTimeoutNotSilentSuccess`,
`.rfcommWriteFailureMapsToRfcommCategory`.

### OB-P6-REQ-013 — Timeouts and deadlines flow through the injected clock; `:core` reads no wall clock
Exchange bound is the smaller of caller and request; cancellation maps to `TIMEOUT`.
**Rationale.** Prompt §8/§13; ADR-P6-010; ADR-P3/P4 `TimeProvider` seam.
**Priority.** P1 · **Dependencies.** OB-P6-REQ-012 · **Verification.** `TransportRequestTest` (Phase 1) +
the timeout branches asserted in `AndroidTransportTest`.

## Resolution

### OB-P6-REQ-014 — A transport-resolution contract exists and its only Phase 6 implementation selects nothing
`TransportResolver`/`TransportResolution` compose `TransportNegotiation`; `UndeterminedTransportResolver`
classifies candidates (single/ambiguous/none/no-evidence) and leaves `selected = null`.
**Rationale.** Prompt §12; ADR-P6-005.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-001 · **Verification.** `TransportResolverTest` (6).

### OB-P6-REQ-015 — No manufacturer-specific transport-selection rule and no auto-connect to candidates
The resolver reads gathered availability facts only; it never probes, orders, or connects.
**Rationale.** Prompt §12's prohibitions; ADR-P6-005.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-014 · **Verification.** `TransportResolverTest.exactlyOneUsableCandidateIsReportedButNotSelected`,
`.aResolutionCannotBeBuiltThatSelectsAChannel`.

### OB-P6-REQ-016 — Unknown and ambiguous transport states are safe and distinct
No-evidence, all-refused, single and ambiguous are four different outcomes; absence is never read as
"unsupported."
**Rationale.** Prompt §12/§13; ADR-P0-016; ADR-P6-005.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-014 · **Verification.** `TransportResolverTest.noCandidatesIsNoEvidenceAndCarriesNoRefusal`,
`.allRefusedIsNoCandidateAvailableWithAReason`, `.severalUsableCandidatesStayAmbiguousWithoutAnOrder`.

## Audio / control separation

### OB-P6-REQ-017 — Media audio stays entirely with Android; the control layer never enters it
No `core.transport` file imports `core.audio`/`core.protocol`; no A2DP/AVRCP/HFP `TransportKind`; no codec
activation claim; no audio routing or processing.
**Rationale.** Prompt §11 (mandatory); ADR-P6-009; ADR-P0-002; the layer map makes the edge uncompilable.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-002 · **Verification.** `DependencyDirectionTest` (no
transport→audio edge); `AndroidTransportTest` exercises no audio type.

## Platform mechanism

### OB-P6-REQ-018 — The Android GATT and RFCOMM mechanism is written behind a framework-free handle seam
`SystemGattTransportHandle`/`SystemRfcommTransportHandle` are the only files importing `android.bluetooth.*`;
the lifecycle/mutex/mapping logic sits above the seam and is unit-tested with fakes.
**Rationale.** ADR-P6-008; Phase 2/3 precedent (the user's "Domain + Android mechanism" choice).
**Priority.** P1 · **Dependencies.** OB-P6-REQ-004, OB-P6-REQ-010 · **Verification.** `AndroidTransportTest`
(12) drives scripted handles with no framework; `:platform:android:compileDebugKotlin` compiles the
framework handles.

### OB-P6-REQ-019 — No transport auto-connects, pairs, or scans; constructing a channel opens nothing
The factory allocates a channel object only; `open()` is the explicit act, and no discovery scan is run.
**Rationale.** Prompt §16 (automatic pairing/connect forbidden); ADR-P6-008, ADR-P6-012.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-018 · **Verification.** `DependencyDirectionTest` forbids
`startScan`/`startDiscovery`/`BluetoothLeScanner`; `TransportBoundariesTest` confines the framework.

### OB-P6-REQ-020 — Platform exceptions never cross into `:core`
Raw `IOException`/`SecurityException` are caught at the framework boundary and surfaced as mapped
`OmniBudsError` results.
**Rationale.** Prompt §13 "do not expose raw Android exceptions through core"; ADR-P6-006.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-012 · **Verification.** `System*TransportHandle` return
`Raw*` results, never throw to the caller; `AndroidTransportTest` sees only `OperationOutcome` failures.

## Scope boundary

### OB-P6-REQ-021 — No vendor command, capability, codec, or UI is implemented
ANC/transparency/EQ/gesture/battery/firmware/codec, device-specific UUIDs and packet encoders, production
UI, saved-device persistence — none appear.
**Rationale.** Prompt §16.
**Priority.** P0 · **Dependencies.** all · **Verification.** `DependencyDirectionTest` (no UI/audio-path
tokens), `TransportBoundariesTest` (no discovery/SDP tokens); no new symbols outside the transport
packages.

### OB-P6-REQ-022 — Physical-device verification is deferred and recorded, never claimed
Every transport capability caps at `IMPLEMENTED`; the instrumented suite compiles and is not run.
**Rationale.** ADR-P3-014, TST-HW-003, PROTO-VERIFY-001; standing directive.
**Priority.** P0 · **Dependencies.** OB-P6-REQ-018 · **Verification.** `validation.md` deferred list; no
test asserts a real device answered.
