# Phase 7 — Architecture Decision Records

Phase: 7 — Protocol Abstraction Engine.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0–6 ADRs, then this file.

**What this phase inherits, stated before any decision.** Phases 1 and 2 did not leave the protocol layer
empty — they built `EarbudProtocol`, `ProtocolDefinition`, `ProtocolRegistry` (shipping **empty** with a test
asserting it, ADR-P1-013), `CommandDefinition`, `ResponseDefinition`, `EffectClass`, `ParsedResponse`,
`ProtocolEncoder`/`ProtocolParser`, `ProtocolIdentification`, `CapabilityMapping`, five capability-support
interfaces, and (in `core.capability`) `VendorExtension`. Prompt §2 is explicit: *"do not introduce duplicate
protocol managers."* So Phase 7 *reuses the knowledge model* and adds the **engine** Phases 1/2 deferred — the
session lifecycle, the resolver, the runtime command/result, the transport-adapter boundary and the event
model — and surfaces the two places where the prompt's vocabulary differs from the tree's (§8 verification
statuses, §11 `UNKNOWN_SIDE_EFFECT`) rather than forking the master-owned ladders.

---

### ADR-P7-001 — Reuse the Phase 1/2 protocol knowledge model; no duplicate descriptor, registry or manager
**Status.** accepted — prompt §2 ("reuse … do not introduce duplicate protocol managers")
**Context.** Prompt §4/§7/§9 name a `ProtocolDescriptor` and an `interface ProtocolRegistry { findById;
findCandidates }`. The tree already has `ProtocolDefinition` (a full descriptor: id, name, nullable vendor,
transport, version, commands, responses, capability mappings, `confidence: VerificationLevel`, validated in
`init`) and `ProtocolRegistry` (immutable, `find`, `candidatesFor`, duplicate-rejecting `register`, ships
empty).
**Decision.** Phase 7 uses those types as-is. `ProtocolDefinition` *is* §7's descriptor; `findById`/
`findCandidates` map onto the existing `find`/`candidatesFor`. No new descriptor record, no registry
interface wrapped over the concrete class, no second registry. New Phase 7 types reference them, never
shadow them.
**Alternatives considered.** Introduce a `ProtocolDescriptor` value mirroring `ProtocolDefinition` —
rejected: two records describing one protocol is the duplicate-manager defect §2 forbids, and they would
drift. Replace `ProtocolRegistry` with an interface — rejected: it would break `ProtocolRegistryTest` and
Phase 1's empty-registry guarantee for no behavioural gain.
**Consequences.** The diff is additive; `ProtocolRegistryTest` and the Phase 1 boundary tests stay meaningful
and become Phase 7's regression guard; the empty-registry discipline (ADR-P1-013) carries straight through.

### ADR-P7-002 — A `ProtocolSession` is a separate runtime type; `EarbudProtocol` stays the stateless family contract
**Status.** accepted — prompt §6, master §9, PROTO-ABST-001/002, §21 (no capability discovery this phase)
**Context.** Prompt §6 sketches `EarbudProtocol { state; initialize(); close(); execute(command) }`. The tree's
`EarbudProtocol` deliberately carries no lifecycle and no feature ops — those live in capability interfaces —
and it has `discoverCapabilities`, which is Phase 8's (§21 forbids capability discovery here).
**Decision.** Keep `EarbudProtocol` unchanged. Add `ProtocolSession`: an instance bound to one opened channel
+ one `ProtocolDefinition`, owning `val state: StateFlow<ProtocolState>` and `suspend initialize()/close()/
execute(command)`. Capability-specific behaviour stays in the existing support interfaces a session may also
implement, so no session is forced to support operations it cannot (§6's "no oversized interface").
`execute` and `initialize` are the phase's contribution; `discoverCapabilities` is *not* on the session.
**Alternatives considered.** Retrofit `state/initialize/close/execute` onto `EarbudProtocol` — rejected: it
would make lifecycle mandatory on a contract meant to be stateless, and pull Phase 8's discovery into scope.
Fold the session into a transport — rejected: that erases the protocol/transport boundary §5 depends on.
**Consequences.** `EarbudProtocol` is untouched (no Phase 1 contract silently changed, prompt §2); the
lifecycle machine has a single owner; a device with no established protocol simply has no session.

### ADR-P7-003 — Reuse the master `VerificationLevel` ladder; §8's `RESEARCHED`/`AUTOMATED_TESTED` are aliases, not a second enum
**Status.** accepted — ADR-P0-014, master §52/§53; prompt §8
**Context.** §8 proposes six statuses including `RESEARCHED` and `AUTOMATED_TESTED`. The project owns a fixed
five-rung `VerificationLevel` (`INFERRED, IMPLEMENTED, LAB_TESTED, HARDWARE_VERIFIED, PERSISTENCE_VERIFIED`)
that every existing protocol type already speaks.
**Decision.** Do not add a protocol-specific ladder. `AUTOMATED_TESTED` is `LAB_TESTED` (an automated JVM
test is exactly that rung); "RESEARCHED but unimplemented" data is `INFERRED`. Phase 7 documents the
§8→`VerificationLevel` aliasing here and in `specs.md`, and gates behaviour on `VerificationLevel` as
`CommandDefinition`/`ProtocolDefinition` already do (`canBeExposedAsControl`, the `LAB_TESTED` write floor).
**Alternatives considered.** A parallel `ProtocolVerification` enum — rejected: two evidence vocabularies
that drift is this project's most-recurring defect; the ladder is master-owned and ADR-gated. Silently
promote a passing unit test to `HARDWARE_VERIFIED` — rejected outright by §8 and TST-HW-003.
**Consequences.** One ladder for the whole app; a protocol's ceiling stays honest; the deferral of hardware
verification is expressed in the same vocabulary everywhere.

### ADR-P7-004 — Reuse `EffectClass`; "unknown side effect" is structural absence, not a fourth member
**Status.** accepted — Phase 0 `specs.md` §4; prompt §11; PROTO-ERR-002
**Context.** §11 lists `READ_ONLY/STATE_MUTATION/IRREVERSIBLE/UNKNOWN_SIDE_EFFECT`. The tree has
`EffectClass{READ, SIDE_EFFECTING_WRITE, IRREVERSIBLE_WRITE}` with the retry asymmetry already enforced
(`CommandDefinition` requires a read budget and forbids a write budget; only `READ` has
`permitsAutomaticRetry`).
**Decision.** Map `READ_ONLY→READ`, `STATE_MUTATION→SIDE_EFFECTING_WRITE`, `IRREVERSIBLE→IRREVERSIBLE_WRITE`.
An operation whose effect is genuinely unknown must not be a sendable `CommandDefinition` at all; "unknown"
is the absence of a definition or a `confidence` below the send floor, not a fourth enum value that would let
an unclassified effect reach a command path. The safety property §11 wants ("unknown effects never
blind-retried") is already structural.
**Alternatives considered.** Add `UNKNOWN` to `EffectClass` — rejected: it would make an unclassified
operation representable as a command, which is the opposite of the guarantee. Re-decide retry at call sites
— rejected: §4 fixes it per definition, not per caller.
**Consequences.** No Phase 1 enum edit; the read/write retry asymmetry holds; a timeout never means "no
effect" because the only safe response to a failed write is re-read, which is `RETRY_AFTER_REREAD`'s meaning.

### ADR-P7-005 — `ProtocolState` is a third, distinct lifecycle axis; `READY` only after `initialize()` succeeds
**Status.** accepted — prompt §12; ADR-P4-002 (single authority); mirrors `TransportState`
**Context.** §12 wants a protocol lifecycle. The tree has `ConnectionState` (device link, L0) and
`TransportState` (channel, L1) but nothing for protocol readiness.
**Decision.** Add `ProtocolState{UNRESOLVED, RESOLVED, CREATED, INITIALIZING, READY, DEGRADED, FAILED,
CLOSING, CLOSED}` and `ProtocolStateTransitions`, kept *separate* from the other two axes (conflating them is
the same category error as profile-vs-transport, ADR-P3-002). `execute` is refused outside `READY`/`DEGRADED`;
`READY` is reached only from `INITIALIZING` on the init contract's success; a transport drop moves the
session to `DEGRADED`/`FAILED`, and there is no automatic re-init of an unsafe command. Terminal `CLOSED`
does not resurrect.
**Alternatives considered.** Reuse `ConnectionState.READY`/`CONTROL_SESSION` — rejected: those are device-session
states Phase 4 pinned unreachable; a protocol owning them would blur the axes. Let a session be `READY` on
construction — rejected: §12's central rule; init is where a protocol proves itself against a live channel.
**Consequences.** Protocol readiness is auditable and never implied by transport availability; the machine is
exhaustively testable with a scripted session; "transport-ready ≠ protocol-ready" and "protocol-ready ≠
hardware-verified" are structural, not reminders.

### ADR-P7-006 — `ProtocolResolver` returns six outcomes over the existing registry and Phase 5 evidence; it connects and executes nothing
**Status.** accepted — prompt §10; PROTO-ID-003; ADR-P5-006/008; master §53
**Context.** §10 wants deterministic resolution with RESOLVED/AMBIGUOUS/UNKNOWN/UNSUPPORTED/
INSUFFICIENT_EVIDENCE/INCOMPATIBLE_VERSION, forbidding brand-name matching, auto-connect and command
execution during resolution.
**Decision.** `ProtocolResolver.resolve(identity: DeviceIdentity, fingerprint: DeviceFingerprint, registry):
ProtocolResolution` composes `registry.candidatesFor(fingerprint)` with the Phase 5 `IdentificationResult`:
- no candidate & sufficient evidence → `UNKNOWN`; no candidate because evidence is thin → `INSUFFICIENT_EVIDENCE`;
- one compatible candidate → `RESOLVED` (a *candidate*, never a verdict; PROTO-ID-003);
- several candidates for the same purpose → `AMBIGUOUS` (no arbitrary winner — ADR-P5-008 discipline);
- a candidate whose firmware/version constraints exclude it → `INCOMPATIBLE_VERSION`;
- a known protocol with no implementation on this platform → `UNSUPPORTED`.
Resolution reads gathered evidence only. It never opens a transport, never calls `identify` on a device,
never sends a command, and — the load-bearing rule — never selects a protocol because a display name contains
a brand (PROTO-ID-001). With the empty registry it returns `UNKNOWN` for every device, which is the shipped
truth.
**Alternatives considered.** A resolver that scores candidates or matches names — rejected by §10/PROTO-ID-001.
A resolver that probes transports to disambiguate — rejected: §10 forbids auto-connect, and Phase 6's
`UndeterminedTransportResolver` already owns the safe-unknown transport answer.
**Consequences.** Ambiguity survives to a human/later phase; a manufacturer match is a *weak* signal, not a
protocol; the empty registry means Phase 7 proves the mechanism, not a population of supported devices.

### ADR-P7-007 — The transport-adapter boundary is a `:core` interface over Phase 6 transports; no Android types, and Phase 7 ships no framework binding
**Status.** accepted — prompt §4 Agent C, §16 architecture; ADR-P0-003/008; the layer map
**Context.** §5's chain puts a "Transport Adapter" between Protocol Session and Bluetooth Transport;
prompt §16 requires "protocols depend on transport abstractions, not Android Bluetooth classes."
**Decision.** Define `ProtocolTransportAdapter` in `core.protocol` over the Phase 6 `GattTransport`/
`RfcommTransport`/`TransportContract` interfaces: it exposes framed byte exchange and (for GATT) per-
characteristic operations to a session, and declares the `TransportKind` a protocol requires. A session whose
required transport is unavailable fails **explicitly** (a typed refusal, not a fallthrough to a different
channel — PROTO-XPORT-007). Phase 7 ships the *contract*; because no vendor protocol exists, there is no
concrete adapter binding and no `android.*` import in the protocol layer.
**Alternatives considered.** Let a protocol hold a `BluetoothGatt` — rejected: forbidden, and it would weld
protocol to platform. A protocol picking any available transport — rejected: PROTO-XPORT-001/-007.
**Consequences.** Protocol↔transport stays a tested boundary; §16's architecture test ("protocols depend on
transport abstractions, not Android classes") is satisfiable because there are no Android classes to depend on.

### ADR-P7-008 — Protocol events are a bounded, cancellation-safe `Flow`; requested state is never device-confirmed state
**Status.** accepted — prompt §13; specs §5.5/5.6; Phase 4/6 event precedent
**Context.** §13 wants an async device-state contract with bounded delivery, cancellation safety, documented
ordering, no unbounded history, no requested/confirmed conflation, and UI kept separate.
**Decision.** A session exposes `events: Flow<ProtocolEvent>` (state-changed / command-completed /
transport-disconnected / protocol-error / initialization-completed / device-capability-update), declared like
Phase 4's session events: `replay = 0` (the `StateFlow<ProtocolState>` is the truth, events are edges), a
bounded buffer with `DROP_OLDEST`, a named overflow. A `ProtocolEvent` records what the *device* reported;
the *requested* value of a command lives in the command/result, never merged into the event, so
"set ANC to X" and "device says ANC is X" cannot be conflated. No event is stored.
**Alternatives considered.** A `SharedFlow` with replay as the state source — rejected: two owners of one
truth (Phase 1 §24 defect). An unbounded event list — rejected: a history buffer. Carrying the requested value
as if confirmed — rejected by §13 outright.
**Consequences.** Collectors leak nothing; the requested/confirmed distinction is in the types; future UI
subscribes to derived state, not to the raw event stream.

### ADR-P7-009 — Vendor extensions reuse `core.capability.VendorExtension`; namespaced, non-colliding, unknown-resolved
**Status.** accepted — prompt §14; ARCH-PROTO-001; ADR-P1-008 (no brand conditionals)
**Context.** §14 wants vendor-specific features preserved without a generic lowest-common-denominator, with
namespaced ids, explicit types, version compatibility, unknown handling and no collision with common features.
Phase 1 already built `VendorExtension`, `VendorFeatureMetadata` and the namespaced `FeatureId`.
**Decision.** Reuse them. A vendor extension is addressed by a namespaced `FeatureId` that cannot collide
with a common feature id (already so by `FeatureId`'s structure); its payload carries explicit type info; an
extension the app does not recognise resolves to *unknown*, never to a default or a guessed feature. Phase 7
defines the *mechanism* for a protocol to declare extensions; it implements no vendor feature (§14's own
"do not implement actual vendor features here").
**Alternatives considered.** A parallel vendor-feature model — rejected: duplicates Phase 1. Collapsing a
vendor mode into the nearest common feature to make it "fit a generic interface" — rejected: that is exactly
the lowest-common-denominator §14 forbids and ADR-P1-008 refuses.
**Consequences.** Unique vendor behaviour is reachable without polluting the common surface; an unknown
extension stays unknown rather than mislabelled.

### ADR-P7-010 — The protocol set ships empty; ceiling `IMPLEMENTED`; scripted protocols are test-only
**Status.** accepted — ADR-P1-013; prompt §16/§17; the user's standing "build the product, skip the phone" directive
**Context.** §17 forbids inventing vendor commands, opcodes, device UUIDs and production protocol databases.
**Decision.** No `ProtocolDefinition` with real vendor facts exists in `src/main`; the registry stays empty
and a Phase 7 test asserts it resolves nothing. Every Phase 7 capability caps at `IMPLEMENTED`; there is no
device-verified protocol because there is no protocol and no radio. Test-only scripted `ProtocolSession`
implementations live in test source sets only and are never presented as support (prompt §16). Physical-device
protocol verification is deferred (`NOT RUN`) with the rest of the hardware list.
**Alternatives considered.** Seed a plausible vendor protocol to make the engine "look used" — rejected: the
core product principle and ADR-P1-013. A test double in `src/main` — rejected by the same rule.
**Consequences.** The framework is real and tested; the absence of protocols is stated, not papered over;
Phase 8+ and the deferred device session inherit an honest floor.
