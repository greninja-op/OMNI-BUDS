# Phase 7 — Design

**Phase:** 7 · **Owner:** orchestrator
**Document status:** describes the tree at `d1fe1a0`. ADRs cited from `decisions.md` (ADR-P7-001 … 010),
requirements from `requirements.md` (`OB-P7-REQ-001 … 020`).

## 1. What the phase is, honestly

Phase 7 is the protocol *engine* laid over a knowledge model Phases 1/2 already built. It adds a lifecycle
machine, a session contract, a resolver, runtime command/response contracts, an event stream and a
transport-adapter boundary — and it ships **no protocol**. The registry is empty, nothing in `src/main`
implements a session, and the whole framework is proven with a test-only scripted session (ADR-P7-010). A
resolved protocol is a candidate, never a verdict; a sent command is delivery, never an applied change.

## 2. The architecture, mapped onto the existing tree

```
DeviceSession (Phase 4)
     │  reported DeviceIdentity + Phase 5 IdentificationResult   (kept separate from conclusions)
     ▼
ProtocolResolver ── resolve(fingerprint, identification, registry, availableTransports) ──▶ ProtocolResolution
     │                    (reads; never connects, probes or executes — ADR-P7-006)
     ▼
ProtocolRegistry (Phase 1, ships empty)  ── candidatesFor(fingerprint) ──▶ ProtocolDefinition (Phase 1 = the descriptor)
     │
     ▼
ProtocolSession  ── state: StateFlow<ProtocolState>, initialize(), execute(ProtocolCommand), close(), events: Flow<ProtocolEvent>
     │              (a NEW runtime type; EarbudProtocol stays the stateless family contract — ADR-P7-002)
     ▼
ProtocolTransportAdapter ── requiredTransport, open/exchange/close over…
     ▼
core.transport (Phase 6): GattTransport / RfcommTransport / TransportContract   (no android.* anywhere in protocol — REQ-002)
```

Everything Phase 7 adds lives in `com.omnibuds.core.protocol` (L4); it imports downward into `transport`
(L1), `device`/`capability` (L2), `state`/`common` (L0). No layer-map change was needed.

## 3. Reuse, not rebuild (the phase's defining constraint)

Prompt §2's "no duplicate protocol managers" governs everything. Concretely: `ProtocolDefinition` *is*
§7's descriptor (id, name, nullable vendor, `transport`, version, commands, responses, capability mappings,
`confidence: VerificationLevel`, all validated in `init`); `ProtocolRegistry` *is* §9's registry
(`find`/`candidatesFor`, duplicate-rejecting `register`, immutable, `empty()`); the capability-specific
interfaces (`FeatureReadSupport`/`FeatureWriteSupport`/battery/firmware/audio) already realise §6's "no
oversized interface". Phase 7 references these; it does not shadow them. `EarbudProtocol` is left untouched —
its `discoverCapabilities` is Phase 8 and is deliberately not pulled into Phase 7's session.

## 4. Lifecycle (ADR-P7-005, REQ-006…008)

`ProtocolState{UNRESOLVED,RESOLVED,CREATED,INITIALIZING,READY,DEGRADED,FAILED,CLOSING,CLOSED}` with
`ProtocolStateTransitions` modelled on `ConnectionStateTransitions`/`TransportStateTransitions`: a forward
map, the failure/closing sinks, idempotent self-transitions. Two invariants are the point — **`READY` is
reachable only from `INITIALIZING`** (no `CREATED/RESOLVED/DEGRADED → READY` shortcut, so a session cannot
claim readiness before `initialize()` succeeds), and **`CLOSED` is terminal** (a late callback cannot
resurrect it). `canExecute` is `READY` only; `canReadState` is `READY|DEGRADED`. This is a *third* axis: a
channel can be `TransportState.CONNECTED` while its protocol is `CREATED`, and the types keep those
unconflatable, exactly as ADR-P3-002 keeps a profile from being a transport.

## 5. Session and transport boundary (ADR-P7-002/007)

`ProtocolSession` is the runtime instance: it owns `state`, exposes `initialize()/execute(command)/close()`
and a `Flow<ProtocolEvent>`, and takes its `requiredTransport` from the descriptor so a caller cannot choose
the channel. `execute` is refused (`INVALID_STATE`) unless `READY` and, per prompt §11/ADR-P7-004, sends the
command **once** — a timeout returns `TIMEOUT` and is never re-sent. `ProtocolTransportAdapter` is how a
session reaches Phase 6's transports: `requiredTransport`, `open`, `exchange(commandId, payload, timeout)`,
`close` — byte-in, byte-out, no GATT/socket/callback leaks upward, no `android.*`. An unavailable required
transport fails explicitly (`PROTO-XPORT-007`), never falls through to a substitute channel.

## 6. Commands and responses (ADR-P7-004, REQ-013…015)

`ProtocolCommand(commandId, payload, correlationId, timeoutMillis)` — correlation required and non-blank,
payload bounded to `MAX_PAYLOAD_BYTES`, opaque bytes (encode/parse belong to `ProtocolEncoder`/`ProtocolParser`),
hand-written array equality, `toString` prints length not bytes (SEC-LOG-004). `ProtocolResponse` keeps the
correlation token, distinguishes a null payload (nothing arrived) from an empty one (a zero-length answer),
and caps `acknowledged` at delivery — the applied value must be *read back*, never inferred from an ack.
Results use the existing `OperationOutcome`, not a parallel `ProtocolResult`. "Unknown side effect" (§11) is
handled structurally — a command whose effect is unestablished is not a sendable `CommandDefinition`, rather
than adding a fourth `EffectClass` (ADR-P7-004).

## 7. Resolution (ADR-P7-006, REQ-009…012)

`RegistryProtocolResolver.resolve(fingerprint, identification, registry, availableTransports, versionCompatible)`
is a pure function: candidates from the registry; empty + adequate evidence → `UNKNOWN`; empty + thin Phase 5
evidence → `INSUFFICIENT_EVIDENCE`; a usable candidate whose transport is unavailable → `UNSUPPORTED`; a
version-failing candidate → `INCOMPATIBLE_VERSION`; one compatible → `RESOLVED` (a candidate, not a verdict);
several → `AMBIGUOUS` with `selected = null`. It never opens a transport, never runs `identify` on a device,
and never picks by a brand name. With the empty registry the shipped answer for every device is `UNKNOWN`,
which is stated as the phase's condition, not a gap.

## 8. Events and vendor extensions (ADR-P7-008/009, REQ-016…018)

`ProtocolEvent` is a bounded, cancellation-safe edge stream (`replay = 0`; the `StateFlow` is the truth). No
event carries a *requested* value — `StateChanged` reports only what the device said, so requested and
confirmed state cannot be conflated (the phase's closing principle). Vendor extensions reuse
`core.capability.VendorExtension`/namespaced `FeatureId`; an unrecognised extension resolves to unknown, not
a default or a nearest-guessed-common-feature; no vendor feature is implemented.

## 9. What this design refuses to be

Not a device-support detector (a known device ≠ a supported device). Not a verified protocol (a registered
protocol ≠ a `HARDWARE_VERIFIED` one). Not a control path that auto-connects, auto-retries a write, or reads
a brand name as identity. Not a UI, a store, or a Phase 8 capability discovery. It is the framework, proven to
behave correctly while knowing nothing — which is the honest shape of a protocol engine before any protocol
exists.
