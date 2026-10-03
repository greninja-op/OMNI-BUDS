# Phase 8 — Design

**Phase:** 8 — Capability Discovery Engine · **Owner:** orchestrator (Agent B/C/G roles)
**Tree this describes:** `968adb4`. Decisions live in `decisions.md` (ADR-P8-001 … 010); this file says how
the pieces fit. All new types sit in `com.omnibuds.core.capability` (layer 2) unless noted.

## 1. The shape, adapted to the real repository

The prompt's §5 diagram is kept in spirit, but the box that would sit *above* the engine is a handed-in
interface, not an import, because capability (L2) may not import protocol (L4):

```text
   (L3/L4 wiring, deferred)          (L2, this phase)
  ProtocolSession ──adapts──▶  CapabilityDiscoverySource
                                       │  read-only
                                       ▼
                              CapabilityDiscoveryEngine
                     attemptableFeatures() · read(feature)
                                       │
              ┌────────────────────────┼───────────────────────────┐
              ▼                        ▼                            ▼
     DeviceCapabilities        DependencyValidator          evidence ladder fold
     (Phase 1, wrapped)        (per-protocol edges, cycles)  (mergedWith, no downgrade)
              └────────────────────────┼───────────────────────────┘
                                       ▼
                              CapabilitySnapshot ── DiscoveryState (lifecycle)
```

The engine never reaches a radio, a socket, a protocol or a device object. It is handed a source, runs its
reads, and returns a value. Binding `CapabilityDiscoverySource` to a real `EarbudProtocol.discoverCapabilities`
(`core/protocol/EarbudProtocol.kt:80`) is an L3/L4 adapter that Phase 8 deliberately does not write.

## 2. The five new files and what each is for

| file | public surface | one-line job |
|---|---|---|
| `CapabilityEvidence.kt` | `CapabilityAvailability` (+`isAvailableNow`), `EvidenceKind`, `CapabilityEvidence` | the "right now" axis and the provenance record behind every claim |
| `DependencyValidator.kt` | `CapabilityDependency`, `DependencyStatus`, `DependencyReport`, `DependencyValidator` | resolve per-protocol prerequisite edges and find cycles, enabling nothing |
| `DiscoveryState.kt` | `DiscoveryState`, `DiscoveryStateTransitions` | the pass's own lifecycle, the fourth state machine |
| `CapabilitySnapshot.kt` | `CapabilitySnapshot`, `PartialFailure`, `UnresolvedConflict` | an immutable, versioned discovery result wrapping `DeviceCapabilities` |
| `CapabilityDiscoveryEngine.kt` | `DiscoveryReading`, `CapabilityDiscoverySource`, `CapabilityDiscoveryEngine` | the deterministic, read-only coordinator |

Line anchors: `CapabilityAvailability` (CapabilityEvidence.kt:19), `isAvailableNow` (34), `EvidenceKind` (46),
`CapabilityEvidence` (80), `unknown` (109); `CapabilityDependency` (DependencyValidator.kt:15),
`DependencyStatus` (29), `DependencyReport` (44), `DependencyValidator.validate` (75), `findCycles` (101);
`DiscoveryState` (DiscoveryState.kt:18), `DiscoveryStateTransitions` (50), `allowedNext` (67),
`canTransition` (72), `isTerminal` (75), `producedEvidence` (82); `CapabilitySnapshot`
(CapabilitySnapshot.kt:23), `availabilityOf` (54), `SCHEMA_VERSION` (63), `empty` (66), `PartialFailure` (87),
`UnresolvedConflict` (94); `DiscoveryReading` (CapabilityDiscoveryEngine.kt:13),
`CapabilityDiscoverySource` (33), `CapabilityDiscoveryEngine` (67), `discover` (72), `conflictsFrom` (136),
`MALFORMED_RESPONSE_CATEGORY` (179).

## 3. What is reused, and why nothing was forked (ADR-P8-001/002/003/009)

`FeatureId`, `FeatureCategory`, `CapabilityDefinition`, `CoreFeature`, `DeviceCapabilities`,
`FeatureCapability`, `CapabilityState`, `VerificationLevel`, `VendorExtension` and `OmniBudsErrorCategory`
are all kept. Prompt §7's `SUPPORT`/`ACCESS`/`VERIFICATION` dimensions are *already* modelled by
`FeatureCapability` (its `init` cross-constrains state↔affordances and floors durability claims); §6's
`CapabilityId` is `FeatureId`; §13's `CapabilitySet` is `DeviceCapabilities`. The single genuinely-new
dimension is momentary availability, so exactly one enum (`CapabilityAvailability`) is added, carried in the
snapshot beside — never written into — `FeatureCapability`.

## 4. The read-only seam and the layer rule (ADR-P8-005)

`DependencyDirectionTest` forbids an L2→L4 edge, so the engine cannot call a protocol. `CapabilityDiscoverySource`
is declared *inside* `core.capability` (L2) and the engine depends only on that interface. Its two methods are
reads: `attemptableFeatures(): List<FeatureId>` and `read(feature): OperationOutcome<DiscoveryReading>`. A real
protocol-backed implementation is supplied from a layer that may depend downward on L2 (session/DI at L3/L4);
Phase 8 ships none, and the scope guard asserts none exists in `src/main`.

## 5. Evidence folding and conflict surfacing (ADR-P8-006)

For each successful read the engine does `capabilities.mergedWith(empty().with(feature, capability))`, so the
Phase 1 evidence ladder decides which record survives — a weaker later read can never quietly erase a stronger
established one. In parallel the engine records, per feature, every `(CapabilityState, EvidenceKind)` it was
given; `conflictsFrom` marks a feature as an `UnresolvedConflict` only when it saw two genuinely different
states there, carrying the kinds that disagreed. A feature read once cannot conflict with itself, so no
conflict is ever fabricated.

`DiscoveryReading` keeps the three claims distinct: a `FeatureCapability` (support/access/durability/
verification), a `CapabilityAvailability` (now), and a `CapabilityEvidence` (provenance). Availability is a
moment, evidence is a record, support is a rung — three fields, three types, no conflation.

## 6. Lifecycle and the three-way completion (ADR-P8-008)

`DiscoveryState` is a fourth axis beside `ConnectionState`, `TransportState` and `ProtocolState`, with the
same discipline: a forward map, universal failure/cancel sinks, terminal states that do not revive. The engine
maps its run onto it: `COMPLETE` when nothing failed, `PARTIALLY_COMPLETE` when some features concluded and
some did not, `FAILED` when there were failures and nothing was established (a session-gone or
protocol-unresolved pass — surfaced through `CONNECTION_UNAVAILABLE`/`PROTOCOL_MISMATCH` categories, never a
new one), `CANCELLED` mid-pass preserving what it had. `CapabilitySnapshot`'s `init` refuses a `COMPLETE` with
failures and a `PARTIALLY_COMPLETE` without them, so the lifecycle and the value cannot disagree.

## 7. Determinism (prompt §3/§9/§13)

`discover` sorts `attemptableFeatures()` by `qualifiedName` before reading, and orders the snapshot's
`evidence` (by source, then kind), `partialFailures` and `unresolvedConflicts` (by feature identity). `mergedWith`
is deterministic (higher rung wins, ties keep the receiver). The same source, listed in any order, yields an
identical snapshot — asserted, not assumed.

## 8. Dependency resolution stays a report, not a mutation (ADR-P8-007)

`DependencyValidator.validate` is pure: it reads the `DeviceCapabilities` the pass produced and returns a
`DependencyReport`. Blocking is expressed as availability only — the engine sets `UNAVAILABLE` for a
feature whose prerequisite is missing or cyclic, and never touches its `CapabilityState`. A dependent that is
itself established stays established and stays in `controllable`; the gate is "may this be offered right now",
not "is it supported".

## 9. Non-goals, stated so a reader cannot mistake them for omissions

No ANC/transparency/EQ/gesture/battery/firmware behaviour; no vendor packets; no write path; no
auto-connect; no UI; no persistence; no production discovery source; no device verification. Each is a later
phase's scope (prompt §19) or is explicitly deferred to the device session at the end of the project
(ADR-P8-010).
