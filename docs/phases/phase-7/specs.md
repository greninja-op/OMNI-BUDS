# Phase 7 — Specs

**Phase:** 7 · **Owner:** orchestrator
**Document status:** normative contracts for the tree at `d1fe1a0`. Enforcement (constructor, layer map,
test) is named wherever it exists. Cites `decisions.md` (ADR-P7-001 … 010).

## 1. Descriptor (`ProtocolDefinition`, reused)

`protocolId` (stable identity; never `displayName`), `displayName` (presentation), `vendor: String?` (null =
standard; blank refused), `transport: TransportKind` (**never `UNKNOWN`** — a protocol must know its own
channel, PROTO-XPORT-001), `version: String?`, `commands`/`responses`/`capabilityMappings` (copied on
construction; cross-references validated in `init`: a response or mapping may not name an undefined command),
`confidence: VerificationLevel`. **§7 satisfied by this type; no new descriptor exists.**

## 2. Verification status (ADR-P7-003) — the master ladder only

| §8 name | maps to `VerificationLevel` | meaning |
|---|---|---|
| RESEARCHED | `INFERRED` | documented/derived, no code path proven |
| IMPLEMENTED | `IMPLEMENTED` | a code path exists, never run on hardware |
| AUTOMATED_TESTED | `LAB_TESTED` | an automated JVM test exercised it |
| (hardware) | `HARDWARE_VERIFIED` | a real device answered — unreachable in Phase 7 |
| (persistence) | `PERSISTENCE_VERIFIED` | survives reconnect on a real device — unreachable |

No parallel protocol-verification enum. Gates already in `VerificationLevel` hold: a command at `INFERRED` may
be parsed but `canBeExposedAsControl` is false and it may not be sent (SEC-RES-004); a write floor is
`LAB_TESTED`; a support claim needs `HARDWARE_VERIFIED`.

## 3. Lifecycle (`ProtocolState` / `ProtocolStateTransitions`)

| From | Legal successors (+ universal `FAILED` and self) |
|---|---|
| UNRESOLVED | RESOLVED |
| RESOLVED | CREATED, UNRESOLVED |
| CREATED | INITIALIZING, CLOSING |
| INITIALIZING | READY, DEGRADED, CLOSING |
| READY | DEGRADED, CLOSING |
| DEGRADED | INITIALIZING, CLOSING (never READY directly) |
| FAILED | CLOSING |
| CLOSING | CLOSED |
| CLOSED | ∅ (terminal) |

`canExecute(s) = s == READY`; `canReadState(s) = s ∈ {READY, DEGRADED}`; `isTerminal = CLOSED`. The
**READY-only-from-INITIALIZING** rule is global: no edge reaches READY except through the init contract
(prompt §12).

## 4. Session contract (`ProtocolSession`) — reused result type

```
val descriptor: ProtocolDefinition
val requiredTransport: TransportKind get() = descriptor.transport   // caller cannot choose it
val state: StateFlow<ProtocolState>                                  // the single authority
val events: Flow<ProtocolEvent>                                      // bounded edges, replay = 0
suspend fun initialize(): OperationOutcome<Unit>                     // CREATED→INITIALIZING→READY / FAILED
suspend fun execute(command: ProtocolCommand): OperationOutcome<ProtocolResponse>  // refused unless READY; sent once
suspend fun close(): OperationOutcome<Unit>                          // idempotent, terminal
```
Results are `OperationOutcome`, not a new `ProtocolResult`. `execute` on a non-ready session is
`INVALID_STATE`; a timed-out command returns `TIMEOUT` and is never re-issued.

## 5. Transport boundary (`ProtocolTransportAdapter`) — `:core` only, no `android.*`

```
val requiredTransport: TransportKind
val isConnected: Boolean
suspend fun open(): OperationOutcome<Unit>
suspend fun exchange(commandId, payload: ByteArray, timeoutMillis): OperationOutcome<ByteArray?>
suspend fun close(): OperationOutcome<Unit>
```
A session whose `requiredTransport` is unavailable fails explicitly (no fallthrough to another channel —
PROTO-XPORT-007). Phase 7 ships no framework binding (no protocol to bind).

## 6. Command / response (`ProtocolCommand` / `ProtocolResponse`)

`ProtocolCommand(commandId, payload, correlationId, timeoutMillis)`: correlation required and non-blank;
`payload.size ≤ MAX_PAYLOAD_BYTES (512)`; `timeoutMillis` null-or-positive; hand-written array equality;
`toString` prints length, never bytes. `ProtocolResponse(commandId, correlationId, payload: ByteArray?,
acknowledged)`: null payload ≠ empty payload (ADR-P0-016); `acknowledged` is delivery-level only. Side-effect
class comes from `CommandDefinition.effectClass` (`READ` is the only auto-retryable class; a malformed reply
is a parser failure, never a fabricated response). "Unknown side effect" = absence of a definition, not a
fourth enum member.

## 7. Resolution (`ProtocolResolver` / `ProtocolResolution`)

```
resolve(fingerprint, identification, registry, availableTransports, versionCompatible = { true }): ProtocolResolution
```
Outcome rules (deterministic; `selected != null` only for `RESOLVED`):
- candidates empty → `INSUFFICIENT_EVIDENCE` if identification is `InsufficientEvidence`, else `UNKNOWN`;
- candidates present, none's `transport` available → `UNSUPPORTED` (a candidate recorded, none selected);
- a usable candidate failing `versionCompatible` → `INCOMPATIBLE_VERSION`;
- exactly one compatible → `RESOLVED`;
- more than one compatible → `AMBIGUOUS` (sorted by `protocolId`, no winner chosen).
`ProtocolResolution.init` makes an inconsistent (outcome, selected, candidates) triple unconstructable. The
resolver opens nothing and executes nothing.

## 8. Events (`ProtocolEvent`)

`StateChanged(feature, observed, at)` · `CommandCompleted(commandId, correlationId, acknowledged, at)` ·
`TransportDisconnected(reason, at)` · `ProtocolError(OmniBudsError, at)` · `InitializationCompleted(succeeded, at)` ·
`CapabilityUpdate(feature, supported, at)`. Delivered on a bounded `replay = 0` stream; never a history; the
`StateFlow<ProtocolState>` remains the truth. No event carries a caller's requested value — requested ≠
device-confirmed is a type-level fact.

## 9. Vendor extensions (reused)

`core.capability.VendorExtension` + namespaced `FeatureId` (a vendor id cannot collide with a common feature
id); explicit payload type; an unrecognised extension resolves to **unknown**, never a default. No vendor
feature is implemented in Phase 7.

## 10. Security and reliability (prompt §15)

Device-arrived bytes are untrusted: parse to `ParsedResponse`/failure, never crash; payloads bounded
(§6); no raw packet bytes in `toString`/logs (SEC-LOG-004); structured `OmniBudsError` only, never a raw
Android exception (there are none in `:core`); cancellation propagates through `suspend`; timeouts are
explicit and never re-send a side-effecting write; no unbounded retry (read budget 1..5 on the definition,
writes have none); one in-flight operation per session (deterministic ownership); `CLOSED` releases the
adapter. Existing rules are not weakened to pass tests.

## 11. Known limitations

- The registry is empty by policy; resolution answers `UNKNOWN` for every device until a reviewed
  `ProtocolDefinition` is registered (Phase 22's database, via the research ladder — never invented here).
- No production `ProtocolSession` ships, so the lifecycle/retry/one-send guarantees are proven by a test-only
  scripted session and by the interface + `ProtocolStateTransitions`; a concrete session must honour them, and
  nothing here pretends a channel was exercised.
- `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` and any real command/response exchange are unreachable and
  `NOT RUN`, deferred to the end-of-project device session (ADR-P3-014); ceiling `IMPLEMENTED`.
