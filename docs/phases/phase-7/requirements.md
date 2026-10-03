# Phase 7 — Requirements

**Phase:** 7 — Protocol Abstraction Engine · **Scope id:** `OB-P7`
**Document status:** authored against the committed tree at `d1fe1a0` (`feat(core): give the protocol engine
its lifecycle, resolver and runtime contracts`). Every acceptance reference names a test that exists and
passes at that commit. `docs/phases/phase-7/decisions.md` holds ADR-P7-001 … 010; requirements cite them by
id and never restate them.

**The condition this phase ships in.** Phases 1/2 built the protocol *knowledge model*
(`ProtocolDefinition`, `ProtocolRegistry` shipping empty, `CommandDefinition`/`ResponseDefinition`/
`EffectClass`/`ParsedResponse`, capability interfaces, `VendorExtension`) and left it with no records and no
runtime. Phase 7 adds the *engine* — lifecycle, session contract, resolver, runtime command/response, events,
transport-adapter boundary — and ships **no vendor protocol and no production session implementation**
(ADR-P7-010). Every capability caps at `IMPLEMENTED`; a known device is not a supported device, a resolved
protocol is not a verified one, and a sent command is not an applied change (prompt §21's closing principle).

Priority: `P0` blocks the boundary · `P1` in scope, required · `P2` in scope, not boundary-blocking.

## Reuse and boundary

### OB-P7-REQ-001 — Reuse the Phase 1/2 protocol contracts; no duplicate descriptor, registry or manager
`ProtocolDefinition` is §7's descriptor and `ProtocolRegistry` is §9's registry; Phase 7 adds no parallel
type and no registry interface over the concrete class.
**Rationale.** Prompt §2 "do not introduce duplicate protocol managers"; ADR-P7-001.
**Priority.** P0 · **Dependencies.** none · **Verification.** `ProtocolRegistryTest` + `PhaseSevenScopeTest`
still pass; no second registry/descriptor type exists.

### OB-P7-REQ-002 — The protocol layer is platform-independent and depends only on transport abstractions
No `core.protocol` file imports `android.*`; a session reaches a channel only through `ProtocolTransportAdapter`
over Phase 6's `GattTransport`/`RfcommTransport`.
**Rationale.** Prompt §6/§16; ADR-P7-007; the layer map (L4 imports down).
**Priority.** P0 · **Dependencies.** OB-P7-REQ-001 · **Verification.** `PhaseSevenScopeTest.theProtocolPackageImportsNoAndroidClass`.

### OB-P7-REQ-003 — No production source implements a session or adapter; the engine is specified, not faked
A `ProtocolSession`/`ProtocolTransportAdapter` in `src/main` would be a channel pretending to speak a
protocol with no radio and no verified facts.
**Rationale.** ADR-P7-010; ADR-P1-013; prompt §16 ("no production fake hardware behavior").
**Priority.** P0 · **Dependencies.** OB-P7-REQ-002 · **Verification.** `PhaseSevenScopeTest.noProductionSourceImplementsTheRuntimeProtocolInterfaces`.

## Descriptor and verification

### OB-P7-REQ-004 — A protocol descriptor carries stable identity and refuses unknown transport
`ProtocolDefinition` keys on `protocolId` (never `displayName`), its `vendor` is null for a standard protocol
(refusing blank), and `transport` must be a determined `TransportKind`.
**Rationale.** Prompt §7 "use stable identifiers / do not use display names as keys"; PROTO-XPORT-001.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-001 · **Verification.** `ProtocolDefinitionTest` (Phase 1) +
`ProtocolResolverTest` builds descriptors through those guards.

### OB-P7-REQ-005 — Verification levels are the master ladder; §8's names are aliases, not a second enum
`RESEARCHED`→`VerificationLevel.INFERRED`, `AUTOMATED_TESTED`→`LAB_TESTED`; no parallel protocol-verification
enum; a unit test never promotes an implementation to `HARDWARE_VERIFIED`.
**Rationale.** Prompt §8; ADR-P7-003; ADR-P0-014; TST-HW-003.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-001 · **Verification.** documented in `specs.md`; enforced by
`CommandDefinition.canBeExposedAsControl`/`ProtocolRegistry` confidence gates (Phase 1) exercised in the
resolver tests.

## Lifecycle

### OB-P7-REQ-006 — A protocol session has an explicit lifecycle distinct from link and channel state
`ProtocolState` + `ProtocolStateTransitions`, held as `state: StateFlow<ProtocolState>`, is a third axis never
merged with `ConnectionState` or `TransportState`.
**Rationale.** Prompt §12; ADR-P7-005; ADR-P3-002 (distinct axes).
**Priority.** P0 · **Dependencies.** OB-P7-REQ-001 · **Verification.** `ProtocolStateTest` (6).

### OB-P7-REQ-007 — READY is reached only after initialization succeeds
There is no `CREATED → READY` or `RESOLVED → READY` edge; `READY` is reachable only from `INITIALIZING`, and
`DEGRADED` recovers only by re-initializing.
**Rationale.** Prompt §12 "do not mark a protocol ready before its initialization contract succeeds".
**Priority.** P0 · **Dependencies.** OB-P7-REQ-006 · **Verification.**
`ProtocolStateTest.readyIsReachableOnlyThroughInitializing`, `ProtocolSessionTest.aSessionIsReadyOnlyAfterInitializeSucceeds`,
`.aFailedInitializeLeavesTheSessionNotReady`.

### OB-P7-REQ-008 — A closed session is terminal; operations before READY are refused; close is idempotent
`execute` outside `READY` fails `INVALID_STATE`; `CLOSED` accepts no revival; a repeated `close()` succeeds.
**Rationale.** Prompt §12/§14; ADR-P7-005.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-006 · **Verification.**
`ProtocolSessionTest.executeBeforeReadyIsRefusedAndSendsNothing`, `.closeIsIdempotent`,
`ProtocolStateTest.closedIsTerminalAndNotResurrectedByALateCallback`.

## Resolution

### OB-P7-REQ-009 — Protocol resolution is deterministic and preserves ambiguity
`RegistryProtocolResolver` reads candidates via the registry and yields exactly one of six outcomes; several
compatible candidates produce `AMBIGUOUS` with `selected = null`, ordered deterministically.
**Rationale.** Prompt §10; ADR-P7-006; ADR-P5-008 discipline.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-001 · **Verification.**
`ProtocolResolverTest.severalCompatibleCandidatesStayAmbiguousAndSelectNone`,
`.resolutionOrderIsDeterministicRegardlessOfRegistryInsertionOrder`.

### OB-P7-REQ-010 — A brand name never selects a protocol and a manufacturer match is never an exact protocol
Resolution matches exact `protocolCandidates` and identity evidence only; it does not fuzzy-match a display
name and does not promote a manufacturer-only identification to a protocol.
**Rationale.** Prompt §10; PROTO-ID-001/-003; ADR-P5-005.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-009 · **Verification.**
`ProtocolResolverTest.aSingleCompatibleCandidateResolvesToIt` (candidate must be named in evidence), and the
empty-registry guard `.theShippedEmptyRegistryResolvesNothingToUnknown`.

### OB-P7-REQ-011 — Unknown, insufficient, unsupported and incompatible-version are distinct, safe outcomes
No candidate + adequate evidence → `UNKNOWN`; thin evidence → `INSUFFICIENT_EVIDENCE`; candidate whose
transport is unavailable → `UNSUPPORTED`; version-failing → `INCOMPATIBLE_VERSION`; none selects or connects.
**Rationale.** Prompt §10/§13; ADR-P0-016; PROTO-DB-002.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-009 · **Verification.**
`ProtocolResolverTest.thinEvidenceYieldsInsufficientNotUnknown`, `.aCandidateWhoseTransportIsUnavailableIsUnsupportedNotChosenElsewhere`,
`.aVersionIncompatibleCandidateIsReportedAsIncompatible`.

### OB-P7-REQ-012 — Resolution never connects, probes or executes
The resolver is a pure function over supplied evidence, transports and registry; it opens no channel and
issues no command.
**Rationale.** Prompt §10 "do not initiate a transport connection automatically / execute commands during resolution".
**Priority.** P0 · **Dependencies.** OB-P7-REQ-009 · **Verification.**
`PhaseSevenScopeTest.resolutionAndRegistryCodeNeverOpenOrExchangeAChannel`.

## Commands, responses, safety

### OB-P7-REQ-013 — Commands carry required correlation, bounded payloads and opaque bytes
`ProtocolCommand` requires a non-blank `correlationId`, bounds payload to `MAX_PAYLOAD_BYTES`, and carries
opaque bytes (encoding is the encoder's job); byte-content equality governs identity.
**Rationale.** Prompt §11; PROTO-ABST-006; prompt §15 payload limits.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-002 · **Verification.** `ProtocolCommandTest` (7).

### OB-P7-REQ-014 — A command is sent at most once; a timeout is never re-sent and never means "no effect"
A timed-out or failed side-effecting command is not auto-retried; only `EffectClass.READ` has a bounded retry
budget (inherited from `CommandDefinition`).
**Rationale.** Prompt §11; ADR-P7-004; specs §4; PROTO-ERR-002.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-013 · **Verification.**
`ProtocolSessionTest.aCommandIsSentExactlyOnceEvenWhenTheReplyFails`.

### OB-P7-REQ-015 — Malformed responses become structured failures; a reply is not an applied change
`ProtocolResponse.acknowledged` is delivery-level; a null payload differs from an empty one; a malformed
reply is a typed failure, never a crash or a fabricated value.
**Rationale.** Prompt §11/§15; ADR-P0-016; PROTO-ERR-001.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-013 · **Verification.**
`ProtocolCommandTest.responseDistinguishesAbsentFromEmptyPayload` + the interface contract.

## Events and observation

### OB-P7-REQ-016 — Protocol events are a bounded, cancellation-safe stream kept separate from state
`events: Flow<ProtocolEvent>` is `replay = 0` with a bounded buffer; the `StateFlow<ProtocolState>` is the
truth, events are edges; nothing is stored.
**Rationale.** Prompt §13; specs §5.5/5.6; Phase 4 event precedent.
**Priority.** P1 · **Dependencies.** OB-P7-REQ-006 · **Verification.**
`ProtocolSessionTest.eventsReachACollectorAttachedBeforeTheyAreEmitted`.

### OB-P7-REQ-017 — Requested state is never reported as device-confirmed state
A command's requested value lives in the command/response; a `ProtocolEvent.StateChanged` carries only what
the device reported; the two are distinct types.
**Rationale.** Prompt §13; the closing principle "requested ≠ device-confirmed".
**Priority.** P0 · **Dependencies.** OB-P7-REQ-016 · **Verification.**
`ProtocolEvent` sealed design (no requested-value in any confirmed-state event); asserted by `PhaseSevenScopeTest` type set.

## Vendor extensions

### OB-P7-REQ-018 — Vendor extensions are namespaced, typed and unknown-resolved, reusing Phase 1's model
A vendor feature is addressed by a namespaced `FeatureId`/`VendorExtension` that cannot collide with a common
feature; an unrecognised extension resolves to unknown, never a default; no vendor feature is implemented here.
**Rationale.** Prompt §14; ARCH-PROTO-001; ADR-P1-008; ADR-P7-009.
**Priority.** P1 · **Dependencies.** OB-P7-REQ-001 · **Verification.** reuse of `core.capability.VendorExtension`
(no new extension type; `FeatureId` namespacing tests from Phase 1 hold).

## Scope boundary

### OB-P7-REQ-019 — The registry ships empty and the resolver answers UNKNOWN for every device
No invented `ProtocolDefinition`, opcode, device UUID or packet layout exists in `src/main`; the framework is
proven with a test-only scripted session.
**Rationale.** Prompt §16/§17; ADR-P1-013; ADR-P5-006 precedent; ADR-P7-010.
**Priority.** P0 · **Dependencies.** OB-P7-REQ-003 · **Verification.**
`PhaseSevenScopeTest.theShippedRegistryHoldsNoProtocol`, `ProtocolResolverTest.theShippedEmptyRegistryResolvesNothingToUnknown`.

### OB-P7-REQ-020 — No forbidden capability is implemented and physical verification is deferred
No real vendor command, ANC/EQ/transparency/gesture/battery/firmware/codec, production protocol DB, UI,
persistence, auto-pair/auto-connect or capability discovery (Phase 8) is introduced; device verification is
`NOT RUN`, never skipped.
**Rationale.** Prompt §17/§21; TST-REC-005; ADR-P3-014.
**Priority.** P0 · **Dependencies.** all · **Verification.** `PhaseSevenScopeTest` (no Android, no fake impl,
no connect); `DependencyDirectionTest`; `validation.md` deferred list.
