# Phase 7 — Architecture Audit

**Phase:** 7 — Protocol Abstraction Engine · **Owner:** orchestrator (Agent A role)
**Document status:** the pre-execution inspection that shaped ADR-P7-001 … 0nn, read out of the committed
tree at the Phase 7 start (`f031317`, Phase 6's head). Findings verified by opening each file, not quoted
from a prior document.

## 1. The headline: Phase 1/2 already built the protocol *knowledge model*; Phase 7 adds the *engine*

`core/src/main/kotlin/com/omnibuds/core/protocol/` already holds sixteen files that constitute almost all of
prompt §4 Agent B's "design these" list, and prompt §2 is explicit — *"do not introduce duplicate protocol
managers"*. Concretely, already present:

| Prompt §4/§… asks for | Already exists as | Phase 7 stance |
|---|---|---|
| ProtocolDescriptor (§7) | `ProtocolDefinition` (id, displayName, vendor?, transport, version?, commands, responses, capabilityMappings, `confidence: VerificationLevel`) with heavy `init` validation | **Reuse.** It *is* the descriptor; do not add a second record type |
| ProtocolRegistry (§9) | `ProtocolRegistry` — immutable, `find(id)`, `candidatesFor(fingerprint)`, `register` with duplicate-id rejection, `empty()`, ships empty with `ProtocolRegistryTest` | **Reuse.** Prompt §9's `findById`/`findCandidates` are `find`/`candidatesFor`; no new interface over it |
| ProtocolIdentity/Identification (§5/§10) | `ProtocolIdentification` + `MatchEvidence` ladder (incl. `FALLBACK_UNKNOWN`), `EarbudProtocol.identify(fingerprint)` | **Reuse + resolve around it** |
| ProtocolCommand/Response (§11) | `CommandDefinition` (effectClass, timeout, `maxReadAttempts` enforced by construction: required for READ, forbidden for writes, `permitsAutomaticRetry`, `canBeExposedAsControl`), `ResponseDefinition`, `ParsedResponse(commandId, fields)`, `ProtocolEncoder`/`ProtocolParser`, `EffectClass{READ, SIDE_EFFECTING_WRITE, IRREVERSIBLE_WRITE}` | **Reuse the definitions; add a *runtime* command/result with correlation id** |
| ProtocolCapability (§3/§6) | `FeatureReadSupport`, `FeatureWriteSupport`, `BatteryReportingSupport`, `FirmwareReportingSupport`, `AudioStateReportingSupport`, `CapabilityMapping` — capability-specific interfaces precisely so `EarbudProtocol` stays unbloated | **Reuse**; this already implements §6's "no oversized interface" |
| Vendor extensions (§14) | `core.capability.VendorExtension`, `VendorFeatureMetadata`, `CoreFeature`, `FeatureCategory`, `FeatureId` | **Reuse**; add unknown-extension resolution, no new extension type |
| `EarbudProtocol` (§6) | exists with `protocolId`, `identify`, `discoverCapabilities`, `readState` | **Keep**; the *session* (state/initialize/close/execute) is a new, separate type — not bolted onto this family contract |

So Phase 7 is not a greenfield protocol layer. Its honest deliverable is the parts that genuinely do not
exist yet, plus reconciliations where the prompt's vocabulary differs from the tree's.

## 2. What is actually missing (grep-verified), and is Phase 7's real work

| Needed | Evidence it is absent | Phase 7 deliverable |
|---|---|---|
| **Protocol lifecycle state machine** (§12) | no `ProtocolState`; only `ConnectionState` (L0) and `TransportState` (L1) exist | `ProtocolState` + `ProtocolStateTransitions` in `core.protocol` (L4), a *third* axis kept distinct from the other two |
| **Protocol session** runtime contract (§6 example: `state`, `initialize`, `close`, `execute`) | `EarbudProtocol` has none of these (by design — it is the stateless family) | `ProtocolSession` interface: bound to a transport, owns `StateFlow<ProtocolState>`, `initialize/close/execute`; capability-specific |
| **Protocol resolver** over identity evidence (§10) | `EarbudProtocol.identify` exists on one implementation; there is no *registry-driven resolver over `DeviceIdentity`/`DeviceFingerprint`/Phase-5 `IdentificationResult` producing RESOLVED/AMBIGUOUS/UNKNOWN/UNSUPPORTED/INSUFFICIENT_EVIDENCE/INCOMPATIBLE_VERSION* | `ProtocolResolver` + `ProtocolResolution`; parallel in spirit to Phase 5's `IdentificationResult`, kept a distinct type |
| **Runtime command/result** with correlation (§11) | `CommandDefinition` is static; `TransportRequest/Response` are transport-level | `ProtocolCommand` (correlationId, timeout, effectClass view) + `ProtocolResult`, mapping onto existing types |
| **Protocol event model** (§13) | none; Phase 6 added transport notifications but no protocol-level events | a bounded, cancellation-safe `ProtocolEvent` flow; requested ≠ device-confirmed |
| **Transport-adapter boundary** (§4 Agent C) | protocol does not yet consume `GattTransport`/`RfcommTransport` | `ProtocolTransportAdapter` contract over Phase 6 interfaces; declares required transport; unsupported combos fail explicitly; **no Android types** |

## 3. Two vocabulary discrepancies the prompt forces me to surface, not silently obey

**(a) §8's verification statuses are not the master ladder.** Prompt §8 suggests
`RESEARCHED/INFERRED/IMPLEMENTED/AUTOMATED_TESTED/HARDWARE_VERIFIED/PERSISTENCE_VERIFIED`. The project owns
a *fixed* five-rung `VerificationLevel` — `INFERRED, IMPLEMENTED, LAB_TESTED, HARDWARE_VERIFIED,
PERSISTENCE_VERIFIED` — governed by ADR-P0-014 and master §52/§53, and every existing protocol type
(`ProtocolDefinition.confidence`, `CommandDefinition.confidence`, `ProtocolIdentification.confidence`) is
already expressed in it. Inventing a parallel ladder with `RESEARCHED`/`AUTOMATED_TESTED` would create two
evidence vocabularies that drift — the exact defect class this project keeps correcting (RISK-039 lineage).
**Resolution (ADR-P7-xxx):** reuse `VerificationLevel`; `AUTOMATED_TESTED` *is* `LAB_TESTED` (an automated
JVM test), and "RESEARCHED" data with no code behind it *is* `INFERRED`. No new enum; the §8 names are
documented as aliases, never as a second ladder.

**(b) §11's `UNKNOWN_SIDE_EFFECT` is not an `EffectClass`.** `EffectClass` has three members and its whole
point is the read/write retry asymmetry; an operation whose effect class is genuinely unknown must not be a
sendable `CommandDefinition` at all — "unknown effect" is represented by the *absence* of a definition (or
`confidence = INFERRED`, which already forbids sending), not by a fourth enum member that would let an
unknown effect slip into a command path. **Resolution (ADR-P7-xxx):** reuse `EffectClass`; cover
"unknown side effect" structurally (no auto-retry, not sendable below `LAB_TESTED`) rather than by editing a
Phase 1 enum. This is the same discipline ADR-P3-006 applied to error categories.

## 4. Layer arithmetic and dependency direction

`protocol` is L4 (top): `common/state` L0 → `transport/platform` L1 → `device/capability/audio/config/
diagnostics` L2 → `session/persistence` L3 → `protocol` L4. Phase 7's new types are all in `core.protocol`
(L4) and may import downward into `transport` (L1, to consume `GattTransport`/`RfcommTransport`/
`TransportState`), `device`/`capability` (L2, to consume `DeviceIdentity`/`DeviceFingerprint`/
`IdentificationResult`/`VendorExtension`/`FeatureId`), `state`/`common` (L0). Every direction is strictly
downward, so no layer-map relaxation is needed — `DependencyDirectionTest` must stay green unchanged.
Protocols depend on **transport abstractions, never on `android.bluetooth`** (prompt §16 architecture): the
adapter boundary is a `:core` interface; the framework-backed binding, if any, is platform-side and Phase 7
ships none (no vendor protocol exists to bind).

## 5. Boundary refusals that must survive as tests, not prose (prompt §5, closing principle)

Five separations the phase is trusted to make *unrepresentable*, matching how `TransportBoundary` and
`DeviceFingerprint` encode their rules in `init`:
- identity ⇒ ≠ protocol-compatible (a resolution is a candidate, never a verdict — PROTO-ID-003);
- resolution ⇒ ≠ communication-succeeded (resolution executes nothing, connects nothing);
- a registered protocol ⇒ ≠ all features supported (capability discovery is Phase 8, §21 forbids it here);
- transport-available ⇒ ≠ protocol-ready (`ProtocolState.READY` only after `initialize` succeeds);
- protocol-ready ⇒ ≠ hardware-verified (`VerificationLevel` ceiling; `IMPLEMENTED` at best this phase).

## 6. Reuse decisions locked before any code

1. Do not add a `ProtocolDescriptor` type; `ProtocolDefinition` is it (ADR-P7-001).
2. Do not add a registry interface or a second registry; `ProtocolRegistry` is the one (prompt §2).
3. Keep `EarbudProtocol` as the stateless family contract; the lifecycle/session is a **new** type (ADR on
   §6), so `discoverCapabilities` (Phase 8) is not pulled into Phase 7's session.
4. Add the missing engine types in `core.protocol`; consume Phase 5's identification result and Phase 6's
   transports downward.
5. Reuse `VerificationLevel` and `EffectClass`; reconcile §8/§11 naming in ADRs rather than fork the ladders.
6. Reuse `core.capability.VendorExtension`; add unknown-extension handling, not a new extension model.
7. Registry ships empty (ADR-P1-013, ADR-P5-006 precedent): no invented vendor protocol, and a test that the
   default resolves nothing.
