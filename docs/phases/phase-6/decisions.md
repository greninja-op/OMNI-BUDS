# Phase 6 — Architecture Decision Records

Phase: 6 — Bluetooth Transport Layer.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0–5 ADRs, then this file.

**What this phase inherits, stated before any decision.** Phases 1 and 2 did not leave the transport
layer unbuilt — they built `TransportContract`, `BluetoothTransport`, five per-`TransportKind`
sub-interfaces, `TransportAvailability`, `TransportBoundary`, `TransportNegotiation`, `TransportRequest`
and `TransportResponse`, and then deliberately gave every `open`/`close`/`exchange`/`probeAvailability`
**no body**, because no `:core` type may implement them (ADR-P1-013) and no phase before 6 was authorised
to open a channel. `docs/phases/phase-2/transport-boundaries.md` §7 names Phase 6 as the owner of exactly
those bodies and of the `BleTransport`/`GattTransport` question, and its cross-cutting list defers the
notification channel, the transport factory, the selection policy and the error mapping to this phase.
Phase 6 therefore *fills* the existing hierarchy and adds the lifecycle machine, the resolver contract and
the Android mechanism; it does not raise a second, competing transport tree (ADR-P6-001).

**One inherited contradiction is settled here rather than left to rot.** `TransportKind` has a `BLE`
member (added in Phase 2), yet `BleTransport.kt` and Phase 2's boundaries doc both still assert "
`TransportKind` has no `BLE` constant" while `BleTransport` returns `TransportKind.BLE`. Data that
contradicts the code is this project's most-recurring defect (RISK-039 lineage); ADR-P6-004 answers the
substantive question (a BLE control channel *is* GATT) and corrects the stale sentences in the open.

---

### ADR-P6-001 — Phase 6 fills the Phase 1/2 transport hierarchy; no parallel interface tree, no new source area
**Status.** accepted — prompt §2 ("reuse valid existing abstractions / do not duplicate"), `transport-boundaries.md` §7
**Context.** The prompt opens with "Create or refine platform-independent interfaces" and sketches a
`DeviceTransport { state; connect(); disconnect(); close() }` plus a `TransportManager` and a resolver. The
tree already contains a richer version of most of this: `TransportContract` with `open/close/exchange` and
a documented timeout contract, `BluetoothTransport` adding `probeAvailability`, and `TransportNegotiation`
that already models offer-vs-selection with no silent fallthrough.
**Decision.** Phase 6 extends the existing package in place: it gives the deferred members real homes, adds
a `TransportState` machine and a `TransportResolver`, and implements the mechanism behind
`:platform:android`. No `com.omnibuds.transport2`, no second root contract, no re-`TransportKind`. The
prompt's `DeviceTransport.connect()` maps onto the inherited `open()`, and its `state: StateFlow<…>` is
added to `BluetoothTransport` (the one place every Bluetooth channel already converges) rather than to a
new interface.
**Alternatives considered.** Rebuild to the prompt's literal sketch — rejected: it would strand 12 working
value types behind a `@Deprecated`, and the value types encode the safety invariants (a boundary cannot be
`available` below `LAB_TESTED`; a `selected` kind must be an offered, available candidate) that are the
whole point of the abstraction. Silently *not* filling the deferred bodies — rejected: prompt §3 and the
Phase 2 doc both name Phase 6 as their owner; an interface with no implementation and no implementing
phase is the "unfinished work" §2 forbids removing or pretending is done.
**Consequences.** `TransportBoundariesTest` (Phase 2's) stays meaningful and gains siblings; the resolver
composes existing types instead of minting a parallel candidate model; the diff is additive to
`core.transport`, which keeps the dependency map unchanged (transport is still L1).

### ADR-P6-002 — A transport has an authoritative lifecycle state, and `CONNECTED` is only ever the platform's own confirmation
**Status.** accepted — prompt §8, ADR-P4-002's single-authority rule, PROTO-VERIFY-001
**Context.** Today a transport exposes only `isOpen: Boolean`. Prompt §8 wants a documented machine —
unavailable/idle/connecting/connected/closing/closed and failure/unknown arms — with valid and invalid
transitions, repeated-operation and cancellation behaviour, and the rule "do not report a transport as
connected before the underlying transport confirms it."
**Decision.** Add `TransportState` (enum) and `TransportStateTransitions`, shaped exactly like
`ConnectionStateTransitions`: a forward map, an unavoidable failure sink that any live state may fall
into, and idempotent self-transitions. `BluetoothTransport` gains `val state: StateFlow<TransportState>` as
the single authority; `isOpen` becomes a read-through to it (`state == CONNECTED`), not a second field.
`open()` moves `IDLE → CONNECTING → CONNECTED` only on the platform's success callback; a failure anywhere
lands on `FAILED`/`DISCONNECTED`, never on a fabricated `CONNECTED`. Phase 6 keeps this ceiling honest:
no state above `CONNECTING` is reachable in JVM tests except through a scripted double, and the double's
verdict is `IMPLEMENTED` evidence, not a claim about a radio.
**Alternatives considered.** Keep `isOpen` and document the states in prose — rejected: prompt §8's
transitions are the safety property (a stale callback must not resurrect a closed channel), and prose does
not stop a caller from writing the illegal move. Reuse `ConnectionState` — rejected: it models the *device
link* (Phase 3/4's axis) and includes `IDENTIFYING`/`READY`/`CONTROL_SESSION`, none of which a single
channel owns; conflating the two is the same category error as treating A2DP as a control channel.
**Consequences.** Repeated `open()` on a live channel and `exchange()` on a closed one become refused,
classified operations; the machine is exhaustively testable; the `state` flow is the reactive authority
prompt §3's "authoritative reactive state" asks for, at channel granularity beneath the session.

### ADR-P6-003 — Capability-specific transport interfaces, never one oversized `DeviceTransport`
**Status.** accepted — prompt §6 ("do not create one oversized interface"), PROTO-ABST-006
**Context.** GATT has services, characteristics, descriptors, notifications and an MTU; RFCOMM is an
unframed bidirectional byte stream with a service UUID and channel. A single interface covering both would
force RFCOMM to expose characteristic handles it does not have and GATT to expose socket streams it does
not have.
**Decision.** Put each channel's mechanics on its own interface: `GattTransport` gains discover/read/write/
subscribe/unsubscribe members and an MTU value, all keyed by **caller-supplied** service/characteristic
identifiers represented as data (never a device UUID baked in); `RfcommTransport` gains a connect-to-service,
read and write of a byte array, with **no** framing or message-boundary member. Both keep `open`/`close`/
`exchange`/`probeAvailability`/`state` from the shared root. Nothing in either interface expresses protocol
semantics — PROTO-ABST-006 still binds: a characteristic write is a transport primitive, and *which* bytes
go in it is the protocol layer's, decided later against a research ladder.
**Alternatives considered.** One `DeviceTransport` with every method — rejected outright by prompt §6 and by
the fact that it would make "is this writable?" unanswerable per channel. Method-objects for every
operation — rejected as over-abstraction for ~5 operations per channel; `suspend` functions returning
`OperationOutcome` are the established idiom here.
**Consequences.** A session can hold a `GattTransport` and a `RfcommTransport` for one device
simultaneously (PROTO-XPORT-005) and ask each only its own questions; the notification channel (Phase 2's
open item 1) gets a Flow-based home on `GattTransport` only, where it is real.

### ADR-P6-004 — A control channel over BLE *is* GATT; `BleTransport` is a link-layer availability boundary, and the stale "no BLE constant" docs are corrected
**Status.** accepted — resolves the RISK-039-class contradiction the audit found; prompt §7 (taxonomy)
**Context.** `TransportKind.BLE` exists (Phase 2 added it so `BleTransport` had a kind), but
`BleTransport.kt` and `transport-boundaries.md` §2.1 both still say it does not, while `BleTransport`
returns `TransportKind.BLE`. Phase 6 must decide whether BLE is a control channel in its own right.
**Decision.** It is not, as a *control channel*: a vendor control service reachable over BLE is a GATT
service, so opening and exchanging happen through `GattTransport`. `BleTransport` is retained as the
boundary that answers link-layer *availability* — whether the platform can observe BLE at all, and whether
BLE availability differs from availability of the GATT service riding on it — and is not given independent
`open/exchange` control semantics. The contradicting sentences in `BleTransport.kt` and the Phase 2 doc are
amended (dated, text kept per the index's supersede rule), not deleted.
**Alternatives considered.** Fold `BleTransport` into `GattTransport` — rejected: prompt §5/§7 list them as
distinct, and the link-vs-attribute distinction is real for *availability* even though it collapses for
*control*. Give `BleTransport` its own `open/exchange` — rejected: that manufactures a second identity for
one physical link, the exact "assume GATT" defect (PROTO-XPORT-001) arriving sideways.
**Consequences.** The taxonomy is now self-consistent with the enum; a caller asks BLE the availability
question and GATT the control question; the doc/code contradiction that has survived two phases is closed
in the open.

### ADR-P6-005 — `TransportResolver` returns a safe-unknown `TransportResolution` composed of existing boundaries; it holds no selection policy and no manufacturer rule
**Status.** accepted — prompt §12, ADR-P0-003, PROTO-XPORT-007
**Context.** Prompt §12 asks for a future-compatible `resolve(device, fingerprint): TransportResolution`
and is emphatic that Phase 6 must *not* implement manufacturer-specific selection, must not auto-connect to
every candidate, and must make the safe-unknown behaviour real.
**Decision.** Declare `TransportResolver` over the existing `DeviceIdentity`/`DeviceFingerprint` (Phase 1/5)
returning a `TransportResolution` that wraps a `TransportNegotiation` (candidates + selected) plus a
`ResolutionEvidence`. The only default Phase 6 ships returns `isUndetermined` — every candidate a
`TransportBoundary.refused(...)` at `INFERRED` or a candidate-not-probed row, `selected = null`. It reads
*availability facts already gathered* (Phase 3's profile observations, the platform capability record); it
probes nothing, opens nothing, and never orders GATT before RFCOMM on its own — `preferring(order)` stays
the caller's policy input, and a caller that supplies no order gets `null`, not a guess.
**Alternatives considered.** A resolver that scores candidates or infers a channel from a fingerprint —
rejected: prompt §12 forbids manufacturer rules and PROTO-XPORT-007 forbids fallthrough; and Phase 5's
registry is empty, so there is no evidence base to rank on anyway. Auto-probing on resolve — rejected:
that is "automatically connect to every candidate," §12's explicit prohibition.
**Consequences.** Selection correctness is deferred to the phases that own policy (session, capability,
protocol) without a placeholder that pretends to decide; the resolver is testable purely as an
unknown-preserving function; ambiguity (two viable channels) is representable and not collapsed.

### ADR-P6-006 — No new error category; platform failures map onto the existing categories and carry their retry class unchanged
**Status.** accepted — prompt §13, ADR-P3-006 precedent, ADR-P2-004
**Context.** Prompt §13 lists ~13 transport errors. Inspection shows `OmniBudsErrorCategory` already holds
`TRANSPORT_UNAVAILABLE`, `GATT_FAILURE`, `RFCOMM_FAILURE`, `TIMEOUT`, `DEVICE_DISCONNECTED`,
`BLUETOOTH_DISABLED`, `PERMISSION_DENIED`, `INVALID_STATE`, `UNSUPPORTED_OPERATION`, `CONNECTION_UNAVAILABLE`,
`RESOURCE_UNAVAILABLE`, `READ_FAILED`, `WRITE_REJECTED`, `UNKNOWN_FAILURE`, and — the part prompt §13 cares
about — each carries `RETRY_AFTER_REREAD` / `SAFE_TO_RETRY` / `NEVER_RETRY`.
**Decision.** The domain exposes only `OperationOutcome<OmniBudsError>`-shaped results; a raw Android
exception never crosses into `:core`. The platform adapter owns the mapping (GATT status → `GATT_FAILURE`,
socket IOException → `RFCOMM_FAILURE`, timeout → `TIMEOUT`, security → `PERMISSION_DENIED`, adapter off →
`BLUETOOTH_DISABLED`, closed handle → `RESOURCE_UNAVAILABLE`), and preserves the category's existing retry
class rather than re-deciding it. Timeout is recorded as `RETRY_AFTER_REREAD`: a timed-out write's effect
is *unknown*, so the caller re-reads state and never blindly re-sends (prompt §13's last rule).
**Alternatives considered.** Add a `TRANSPORT_*` category per §13 line — rejected: they duplicate existing
categories with the same meaning and would force every `when` on the category to grow; ADR-P3-006 set the
"reuse before adding" precedent. Let the transport auto-retry a timed-out command — rejected: retry of a
side-effecting write belongs to `EffectClass` on a command definition, not to the channel
(`TransportContract.exchange` already refuses it).
**Consequences.** Error handling stays one taxonomy for the whole app; the retry/no-effect distinction is
inherited, not re-invented; Phase 6 adds no enum member to the shared vocabulary.

### ADR-P6-007 — One operation in flight per channel, cancellation-safe teardown, and a closed transport that refuses new work
**Status.** accepted — prompt §14, Phase 0 `specs.md` rules 5.2/5.9, Phase 3/4 concurrency precedent
**Context.** GATT and RFCOMM both reject concurrent in-flight operations (response correlation is impossible
otherwise; PROTO/specs §5.9). Prompt §14 wants explicit ownership, no leaked GATT/sockets/collectors, no
infinite reconnect, deterministic shutdown, bounded queues.
**Decision.** Each channel serialises `open`/`exchange`/`close` under a `Mutex` and enforces the state
machine inside the lock, so a caller cannot interleave a write with a teardown. A channel that reaches
`CLOSED`/`FAILED` refuses further operations with `RESOURCE_UNAVAILABLE` rather than queueing them, and no
operation is queued unboundedly (there is no queue — the mutex admits one at a time). Cleanup runs under
`NonCancellable` so a cancelled caller still releases the platform handle. There is no auto-reconnect loop:
a dropped channel becomes `DISCONNECTED` and reopening is an explicit `open()` by the owner. The engine owns
no coroutine scope and picks no dispatcher; it runs in the caller's, as Phases 3/4 do.
**Alternatives considered.** An actor/queue per channel — rejected: unbounded work and no evidence the
platform needs it; the mutex is the platform's own constraint expressed once. Reconnect-on-drop — rejected:
"automatic Bluetooth connection" and "infinite reconnect loop" are forbidden (§16, §14), and a session
reconnecting silently hides a device walking away.
**Consequences.** Leaks are structurally bounded (one handle, one in-flight op, guaranteed cleanup);
concurrency is testable with scripted doubles and `runTest`; the closed-rejects-work rule gives prompt §14's
"a failed transport exposes a consistent state" a concrete meaning.

### ADR-P6-008 — The Android transport mechanism is written and seam-tested but never run on a handset; ceiling `IMPLEMENTED`
**Status.** accepted — user's explicit "Domain + Android mechanism" choice for this phase; Phase 2/3 precedent
**Context.** The user directed that the phone not be connected to, yet chose the option that builds the real
Android mechanism now. Phases 2 and 3 resolve this exact tension: they wrote genuine `BluetoothAdapter`/
`getProfileProxy`/`registerReceiver` code and tested it against a scripted source seam, never against a radio.
**Decision.** `:platform:android` gains a `BluetoothGatt`-backed GATT transport and a `BluetoothSocket`-backed
RFCOMM transport behind a narrow handle seam, implementing the `:core` interfaces: connect, discover,
read/write, subscribe/unsubscribe, MTU, socket stream read/write, reliable close, exception→category mapping,
`Mutex` serialisation and `NonCancellable` cleanup. `BluetoothOperation.TRANSPORT_GATT_OPEN` and
`TRANSPORT_RFCOMM_OPEN` (already `authorizedInPhase = 6`) are the operations these code paths would perform,
and they are wired at `di/OmniBudsBluetooth.kt`. **All of it is compiled and tested with fakes; none of it
executes against a device**, so every claim caps at `IMPLEMENTED` and the first device-verified use remains
the Phase 19 vendor hardware (`GattTransport`'s own KDoc; PROTO-VERIFY-001, TST-HW-003).
**Alternatives considered.** Domain-only, defer the adapters (the other offered option) — the user declined
it; a transport architecture with no mechanism on the platform side would under-deliver prompt §3. Write the
adapters and *also* connect to a real device to prove them — refused by the standing directive and by
TST-HW-003; an executed GATT exchange is the deferred device session's to produce, not this phase's to fake.
**Consequences.** Later protocol phases get a real, lifecycle-safe channel to build on without a
rewrite; the seam keeps the Android types out of `:core`; and the honest ceiling (mechanism exists, radio
unproven) is stated rather than implied.

### ADR-P6-009 — Audio/control separation is enforced by the layer map, and Phase 6 keeps the forbidden edge absent rather than policing it at runtime
**Status.** accepted — prompt §11, ADR-P2-001/ADR-P1-003, ADR-P3-002
**Context.** Prompt §11 makes "media audio stays under Android's stack" a mandatory constraint. Phase 1/2
already split the vocabularies: control channels are `TransportKind`, media is `AudioTransportKind`/
`AudioTransportState` in the `audio` (L2) area.
**Decision.** No `core.transport` file imports `core.audio`, `core.protocol` or a platform type; the
dependency-direction test already fails any such edge (transport L1 may not import audio L2). Phase 6
therefore builds no media path, no codec member, no A2DP/AVRCP/HFP `TransportKind`, and adds a
`PhaseSixScopeTest` guard that the edge stays absent and that no control transport claims to carry audio.
LE Audio's dual presence (a control boundary *and* `AudioTransportKind.LE_AUDIO`) is kept distinct exactly
as the Phase 2 doc and master §20 require; opening one is never read as the other.
**Alternatives considered.** A runtime "am I touching audio?" check in the transport — rejected: an
uncompilable edge needs no runtime guard, and a guard implies the edge is otherwise possible. A shared
audio/control kind — rejected: it is the collapse every rule here forbids.
**Consequences.** The separation is architectural and cheap to verify; the media stack is left entirely to
Android; the phase ships no false "codec active" or "A2DP is my control channel" claim.

### ADR-P6-010 — Timeouts and deadlines flow through the existing `TimeProvider` seam; the exchange bound is the smaller of caller and request, and no clock is read in `:core`
**Status.** accepted — prompt §8/§13, ADR-P4-011/ADR-P3 time-provider precedent
**Context.** Prompt §8 wants connection timeouts and §13 operation timeouts with cancellation. Phase 4
already established `TimeProvider` as the injected clock seam so `:core` reads no wall clock.
**Decision.** Timeout duration is a caller-supplied value (`TransportContract.exchange`'s `timeoutMillis`
and `TransportRequest.timeoutMillis`), already contractually the *smaller* of the two; cancellation is
coroutine cancellation, which `withTimeout`/`TimeoutCancellationException` map to `TIMEOUT`. The Android
adapter, not `:core`, holds the real deadline; `:core` never calls `System.currentTimeMillis()`. A timeout
is reported as `TIMEOUT` with the write-effect-unknown rule from ADR-P6-006.
**Alternatives considered.** A core-side timer/scheduler per channel — rejected: unmanaged resources and a
hidden clock; the caller's coroutine scope owns timing, matching how Phases 3/4 confined time to a seam.
**Consequences.** Deterministic tests use `runTest`'s virtual clock; there is one clock seam to fake; a
timeout never masquerades as "the device did not get it."

### ADR-P6-011 — The notification/indication channel gets a Flow home on `GattTransport` only, subscription-leak-safe and callback-confined
**Status.** accepted — closes Phase 2's deferred item 1; prompt §9
**Context.** Phase 2 listed "no notification or event member" as an open gap, deferred because adding it to
the released root would be an unverified write path. Phase 6 owns GATT mechanics and the async answer.
**Decision.** GATT indications arrive as a cold `Flow` (or an explicit subscribe/unsubscribe pair feeding one)
declared on `GattTransport`, never on the root contract, because RFCOMM has no such concept at this layer.
A collector that stops must unsubscribe (no leaked registration); the platform callback pushes into the flow
inside the adapter and no callback shape enters `:core`. Notification *state* is data on the transport so a
caller can tell "subscribed" from "listening."
**Alternatives considered.** Put a `Flow` on `TransportContract` — rejected: forces RFCOMM/classic to answer
a question they do not have (ADR-P6-003's oversized-interface prohibition). A shared mutable "last
notification" field — rejected: drops events and hides the subscription lifecycle.
**Consequences.** A later vendor protocol can subscribe to device-initiated reports; the leak-prone edge
(callback → flow → collector) is in one tested place; the channel stays GATT-specific where that is true.

### ADR-P6-012 — Phase 6 authorises transport opens and keeps the scan deferred: a transport targets a device already known, discovered by no scanner it runs
**Status.** accepted — prompt §12/§16, ADR-P5-012
**Context.** Phase 5 set `DEVICE_DISCOVERY_SCAN.authorizedInPhase = 6` on the reasoning that Phase 6 is the
first phase that *can* open a scanner. Phase 6's charter, though, is connecting to a device the session
already identified (Phase 3/4/5's chain); scanning for *new* nearby devices is not required by any §3
objective and §12 forbids auto-connecting to candidates the user did not pick.
**Decision.** Phase 6 implements GATT/RFCOMM open against a *given* device and performs **no discovery
scan**. `DEVICE_DISCOVERY_SCAN` stays authorised at 6 (it is now true that a transport phase could reach it)
but is exercised by nothing in this phase, which `PhaseSixScopeTest` asserts. Retaining the tag at 6 rather
than advancing it again is deliberate: 6 is now the first phase that *could* legitimately scan while
carrying transport, and the value records capability, not this phase's choice.
**Alternatives considered.** Add a BLE scan to enumerate characteristics for discovery — rejected: §9/§16
forbid device-specific GATT service enumeration absent evidence, and it would start the fingerprinting the
user deferred. Push the scan tag further out — rejected: unlike Phase 5's correction (where Phase 5 truly
could not scan), Phase 6 is exactly the phase that can, so 6 is now honest.
**Consequences.** No scanner code exists; the transport operates on known devices only; the Phase 5 note
that "Phase 6 is the first that can open a scanner" remains true without this phase becoming a discovery
engine.
