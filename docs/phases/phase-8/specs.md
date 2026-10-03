# Phase 8 — Specs

**Phase:** 8 — Capability Discovery Engine · **Tree:** `968adb4`
This file is the normative statement of what Phase 8's types mean. Where it names a rule, a test enforces it;
where a rule is deferred, it is marked `DEFERRED`. ADR ids carry the *why*; this carries the *what*.

## 1. Capability identity (reused)

- A capability's identity is `com.omnibuds.core.common.FeatureId` (`qualifiedName`, namespace + local name,
  `[a-z][a-z0-9]*(-[a-z0-9]+)*` segments; vendor extensions via `FeatureId.ofVendor(vendor, feature)`).
- `FeatureCategory`, `CapabilityDefinition` and `CoreFeature` are the device-agnostic vocabulary. **A
  definition existing in a catalogue is never evidence that any device implements it**, and a feature with no
  record is `UNKNOWN`, not absent-and-therefore-unsupported.
- Phase 8 introduces no `CapabilityId`. (§6 reconciled onto `FeatureId` — ADR-P8-001.)

## 2. The four dimensions, and where each lives

| dimension | type | lives in | §7 name it satisfies |
|---|---|---|---|
| support / durability | `CapabilityState` ladder | `FeatureCapability.state` | `CapabilitySupport` (reused) |
| access (read/write) | `readable`/`writable`, cross-constrained in `FeatureCapability.init` | `FeatureCapability` | `CapabilityAccess` (reused) |
| verification | `VerificationLevel` ladder | `FeatureCapability.verification`, `CapabilityEvidence.verification` | §7 verification (reused — ADR-P8-003) |
| availability (now) | `CapabilityAvailability` | `CapabilitySnapshot.availability`, `DiscoveryReading.availability` | `CapabilityAvailability` (**the one addition** — ADR-P8-002) |

`CapabilityAvailability = { UNKNOWN, AVAILABLE, UNAVAILABLE, TEMPORARILY_UNAVAILABLE }`
- `UNKNOWN` — no availability reading; distinct from `UNAVAILABLE` (unknown ≠ negative).
- `isAvailableNow()` is true for `AVAILABLE` and `TEMPORARILY_UNAVAILABLE`; false for `UNKNOWN` and `UNAVAILABLE`.
- Availability is carried **beside** `FeatureCapability`, never inside it; a supported feature may be
  `UNAVAILABLE`, and that is never a claim of `UNSUPPORTED`.

## 3. Evidence and the verification ceiling (ADR-P8-003/006)

`EvidenceKind = { EXPLICIT_DEVICE_RESPONSE, VERIFIED_PROTOCOL_DESCRIPTOR, DEVICE_FEATURE_FLAG,
MODEL_PROTOCOL_METADATA, FIRMWARE_COMPATIBILITY, TRANSPORT_AVAILABILITY, INFERRED_MODEL,
UNKNOWN_OR_INCOMPLETE }`.

`CapabilityEvidence(kind, source, atEpochMillis, protocolId, protocolVersion, verification, detail, limitation)`:
- `source` non-blank; `protocolId`/`protocolVersion` null-or-non-blank (a blank and an absent binding are not
  two spellings of the same thing).
- **Ceiling:** a `kind` of `INFERRED_MODEL` or `UNKNOWN_OR_INCOMPLETE` must carry `verification == INFERRED`.
  Reasoning and absence cannot claim a higher rung. An implemented-but-unrun parser or a marketing
  description can therefore never be recorded as `HARDWARE_VERIFIED` (prompt §8).
- `unknown(source)` produces the honest "nothing conclusive was found" record: `UNKNOWN_OR_INCOMPLETE` at
  `INFERRED`, `isEstablishing == false`.

## 4. Discovery fold, conflict and the no-downgrade rule (ADR-P8-006)

- Each successful read is folded with `DeviceCapabilities.mergedWith(single)`, so the Phase 1 evidence ladder
  decides survival: **a weaker later record never overwrites a stronger established one**
  (`UNKNOWN < UNSUPPORTED < READ_ONLY < SUPPORTED_VOLATILE < SUPPORTED_PERSISTENT < PERSISTENCE_VERIFIED`;
  ties keep the receiver).
- The engine records per-feature `(state, kind)` observations. `UnresolvedConflict(feature, kinds)` is created
  **only** when a feature was observed at ≥ 2 different `CapabilityState`s, carrying the kinds that disagreed
  (sorted by `EvidenceKind.ordinal`). A single read never yields a conflict.
- Conflicts are surfaced in the snapshot, not resolved by dropping evidence and not scored numerically
  (ADR-P5-004 refuses uncatalogued numbers).

## 5. The read-only source contract (ADR-P8-005)

```
interface CapabilityDiscoverySource {
    suspend fun attemptableFeatures(): List<FeatureId>
    suspend fun read(feature: FeatureId): OperationOutcome<DiscoveryReading>
}
```
- Both operations are reads. There is **no** write, open, connect, command or scan on the seam; the engine
  issues none (prompt §9/§19 — verified by `PhaseEightScopeTest`).
- `DiscoveryReading(capability: FeatureCapability, availability: CapabilityAvailability, evidence: CapabilityEvidence)`.
- A per-feature `Failure` is isolated: that feature becomes a `PartialFailure` and stays `UNKNOWN`; it does
  **not** become `UNSUPPORTED` and does not invalidate unrelated successes (prompt §10).
- `Cancelled` from a read stops the pass immediately, returning a `CANCELLED` snapshot with everything
  gathered so far preserved and nothing after invented.
- **Binding to a real protocol is `DEFERRED`** to an L3/L4 adapter (`EarbudProtocol.discoverCapabilities`,
  `core/protocol/EarbudProtocol.kt:80`); Phase 8 ships no protocol-backed source (ADR-P8-010).

## 6. Error categories (§15 mapped onto `OmniBudsErrorCategory` — ADR-P8-004)

| prompt §15 name | shipped representation |
|---|---|
| `SESSION_UNAVAILABLE` | `CONNECTION_UNAVAILABLE` (a failed read's category); a pass of only these is `FAILED` |
| `PROTOCOL_UNRESOLVED` | `PROTOCOL_MISMATCH`; a pass of only these is `FAILED` |
| `DISCOVERY_UNSUPPORTED` | `UNSUPPORTED_OPERATION` |
| `DISCOVERY_TIMEOUT` | `TIMEOUT` |
| `MALFORMED_CAPABILITY_RESPONSE` | `INVALID_STATE` (engine `MALFORMED_RESPONSE_CATEGORY`) |
| `TRANSPORT_UNAVAILABLE` | `TRANSPORT_UNAVAILABLE` |
| `PERMISSION_DENIED` | `PERMISSION_DENIED` |
| `CANCELLED` | `OperationOutcome.Cancelled` (not an error category) |
| `UNKNOWN_ERROR` | `UNKNOWN_FAILURE` |
| `CAPABILITY_CONFLICT` | **not a category** — the snapshot field `UnresolvedConflict` |
| `DEPENDENCY_INVALID` | **not a category** — a `DependencyStatus` (`CYCLE`/`MISSING_PREREQUISITE`) |

No new category is created. `retryClass` and `invalidatesSession` are inherited per category, not re-decided.

## 7. Lifecycle (ADR-P8-008)

`DiscoveryState = { NOT_STARTED, INITIALIZING, DISCOVERING, PARTIALLY_COMPLETE, COMPLETE, FAILED, CANCELLED }`.
Transition rules (`DiscoveryStateTransitions`):
- `NOT_STARTED → INITIALIZING → DISCOVERING`; completion (`COMPLETE`/`PARTIALLY_COMPLETE`) is reachable **only**
  from `DISCOVERING`.
- `FAILED` and `CANCELLED` are reachable from every in-flight state; terminal states
  (`COMPLETE`/`PARTIALLY_COMPLETE`/`FAILED`/`CANCELLED`) have no forward edge — a re-run is a **new pass**, not
  a revival.
- `producedEvidence` is true only for `COMPLETE`/`PARTIALLY_COMPLETE`; a `CANCELLED` or `FAILED` pass owes no
  mergeable evidence.

Completion derivation (engine):
- `COMPLETE` ⇔ no `partialFailures` (this includes an empty attemptable set — a finished pass that established
  nothing).
- `PARTIALLY_COMPLETE` ⇔ at least one failure **and** at least one established feature.
- `FAILED` ⇔ at least one failure **and** nothing established.
- `CANCELLED` ⇔ a read returned `Cancelled`.

`CapabilitySnapshot.init` refuses `COMPLETE`+failures and `PARTIALLY_COMPLETE`+no-failures, so the value cannot
contradict the lifecycle.

## 8. The snapshot schema (ADR-P8-008)

`CapabilitySnapshot(subjectRef: String?, protocolId: String?, protocolVersion: String?,
discoveredAtEpochMillis: Long?, completion: DiscoveryState, capabilities: DeviceCapabilities,
availability: Map<FeatureId, CapabilityAvailability>, evidence: List<CapabilityEvidence>,
partialFailures: List<PartialFailure>, unresolvedConflicts: List<UnresolvedConflict>, schemaVersion: Int)`.
- `schemaVersion == 1` (`SCHEMA_VERSION`), positive, bumped-never-reused on a shape change.
- `subjectRef` is an opaque `String?`, **not** a `DeviceIdentity`: `capability` and `device` are the same layer
  and a cross-area same-layer import is forbidden, so the snapshot references its subject without importing
  its type.
- `availabilityOf(f)` defaults to `UNKNOWN`; `failedFeatures` is the identity set of the partial failures.
- `empty()` is a `NOT_STARTED` snapshot that establishes nothing.
- Ordering: `evidence` by (source, kind), `partialFailures`/`unresolvedConflicts` by feature `qualifiedName`.
- **No persistence** — a snapshot is a value a caller holds (§13 forbids a permanent device history).

## 9. Dependencies (ADR-P8-007)

`CapabilityDependency(feature, requires, protocolId?, firmwareConstraint?)` — a per-protocol/firmware edge;
`feature == requires` is refused. `DependencyValidator.validate(edges, capabilities)` returns
`DependencyReport(statuses, cycles)` where each edge maps to `DependencyStatus`:
- prerequisite established & not `UNSUPPORTED` → `SATISFIED`;
- prerequisite `UNSUPPORTED` → `MISSING_PREREQUISITE`;
- prerequisite `UNKNOWN` (or absent) → `UNKNOWN_PREREQUISITE` (dependent **not** blocked — unknown ≠ negative);
- edge inside a cycle → `CYCLE`.
`blockedFeatures` = features with a `MISSING_PREREQUISITE` or `CYCLE` edge; the engine sets only their
availability to `UNAVAILABLE` and never changes their support. Cycles are found by a sorted-order DFS
(deterministic) and reported, never broken by dropping an edge. No prerequisite is ever inferred from a
dependent or enabled.

## 10. Vendor extensions (ADR-P8-009)

Namespaced `FeatureId`s (collision-safe against common features) and `VendorExtension`/`VendorFeatureMetadata`
are reused. An extension whose payload Phase 8 cannot type is preserved as unrecognised (identity and raw
metadata kept), never coerced into a `CoreFeature`. Phase 8 implements **no** vendor command/encoder.

## 11. Standing prohibitions (prompt §19)

None of the following exists in Phase 8, and `PhaseEightScopeTest`/`DependencyDirectionTest` enforce the
mechanical subset: actual ANC/transparency/EQ/gesture/battery/firmware control, vendor packet encoders, codec
negotiation, audio processing, automatic connections, hardware-changing operations, production UI, a
production `CapabilityDiscoverySource`, or any capability claim above `IMPLEMENTED`/`LAB_TESTED`.

## 12. What is explicitly deferred (`NOT RUN`, not `SKIPPED`)

Discovery against a real `ProtocolSession`; `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` capability rungs;
malformed *byte-level* responses (no parser ships); and timeout-driven re-read policy. All remain open until a
protocol and a device exist.
