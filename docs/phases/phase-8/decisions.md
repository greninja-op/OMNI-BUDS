# Phase 8 — Architecture Decision Records

Phase: 8 — Capability Discovery Engine.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0–7 ADRs, then this file.

**What this phase inherits, stated before any decision.** Phases 1 and 2 did not leave the capability model
unbuilt — `core.capability` holds `DeviceCapabilities`, `FeatureCapability`, `CapabilityDefinition`,
`CoreFeature`, `FeatureCategory`, `VendorExtension`, `VendorFeatureMetadata`, and `core.state` holds
`CapabilityState` and `VerificationLevel`. `FeatureCapability` already separates *support* (the
`CapabilityState` ladder), *access* (`readable`/`writable`, cross-constrained in `init`) and *verification*
(`verification: VerificationLevel`). Prompt §2 is explicit: *"reuse valid existing models / do not duplicate
capability registries."* So Phase 8 reuses the model and adds only the discovery machinery — evidence
provenance, availability (the one missing dimension), dependency validation, a discovery lifecycle, the
snapshot-with-metadata and the engine — and where prompt §7's suggested enums collide with the existing
model it reconciles rather than forks (the ADR-P7-003/004 precedent).

---

### ADR-P8-001 — Reuse the Phase 1 capability model; add no `CapabilityId`, no second container, no four parallel enums
**Status.** accepted — prompt §2; ADR-P7-001/003 precedent
**Context.** §4/§6/§7 propose `CapabilityId`, `CapabilitySupport`/`CapabilityAccess`/`CapabilityVerification`,
`CapabilityDescriptor`, `CapabilitySet`. The tree has `FeatureId`, `CapabilityState`+`FeatureCapability`
(support/access/verification already separated), `CapabilityDefinition` and `DeviceCapabilities`.
**Decision.** Reuse all of it. `FeatureId` is the capability id; `FeatureCapability` is the descriptor;
`DeviceCapabilities` is the container. §7's support/access/verification are *already modelled*, so no new
enums for them; §13's snapshot wraps a `DeviceCapabilities` in discovery metadata rather than replacing it.
**Alternatives considered.** Mint `CapabilityId`/`CapabilitySupport`/`CapabilityAccess`/`CapabilityVerification`
— rejected: a competing capability model is precisely the failure §2 and §7 forbid, and two vocabularies drift
(RISK-039 lineage). Replace `DeviceCapabilities` with a `CapabilitySet` — rejected: it would orphan Phase 4's
`DeviceState.capabilities` and the `mergedWith` evidence-ladder merge that already enforces deterministic
discovery merge (§10).
**Consequences.** The diff is additive; Phase 1/4 capability tests become Phase 8 regression guards;
§10's "deterministic merge behavior" is already `DeviceCapabilities.mergedWith` and is asserted, not
re-invented.

### ADR-P8-002 — Add exactly one §7 dimension — `CapabilityAvailability` — because the ladder does not encode "now"
**Status.** accepted — prompt §7; §3 of the audit
**Context.** `CapabilityState` conflates support, access and durability; none says "supported but
unavailable at this moment", and `requiresConnection` only hints at it.
**Decision.** Add `CapabilityAvailability{UNKNOWN, AVAILABLE, UNAVAILABLE, TEMPORARILY_UNAVAILABLE}` as a new
orthogonal axis, carried in the snapshot alongside (never written into) `FeatureCapability`. It is
independent: a `SUPPORTED_PERSISTENT` feature may be `UNAVAILABLE`, and an `UNKNOWN` availability is not
`UNAVAILABLE` (unknown ≠ negative, ADR-P0-016). `isAvailableNow()` treats `AVAILABLE` and
`TEMPORARILY_UNAVAILABLE` as reachable (a mid-calibration feature may still surface its control) and
`UNKNOWN`/`UNAVAILABLE` as not.
**Alternatives considered.** Encode availability as a `CapabilityState` rung — rejected: it would break the
evidence ladder's meaning and Phase 4's merge. Add it to `FeatureCapability` — rejected: prompt §2 forbids
silently rewriting an earlier phase's record, and it is a momentary fact, not durable evidence.
**Consequences.** §7's four dimensions become three-reused + one-new, each with a distinct type; no
dimension can be conflated with another because they live in different fields.

### ADR-P8-003 — Reuse `VerificationLevel`; the engine's claims are capped by the evidence tier, never by the parser existing
**Status.** accepted — prompt §7/§8; ADR-P7-003; PROTO-VERIFY-001/-002
**Context.** §7 re-lists the ladder (`RESEARCHED`…`PERSISTENCE_VERIFIED`) and §8 warns "do not treat an
implemented parser as proof of hardware support".
**Decision.** Reuse `VerificationLevel` (no §7 verification enum). Every `FeatureCapability` the engine
emits carries a `verification` that is the ceiling of its evidence, not of the code that parsed it: a read
answered by a scripted/test protocol can be at most `LAB_TESTED`; `HARDWARE_VERIFIED` needs a real device
(deferred); `FeatureCapability.init` already refuses `SUPPORTED_PERSISTENT`/`PERSISTENCE_VERIFIED` below the
matching rung, and the engine inherits that guard.
**Alternatives considered.** A capability-specific ladder — rejected (§7's own "do not create competing
verification systems"). Reporting `HARDWARE_VERIFIED` because a parser round-tripped a fixture — rejected:
that is the fabricated-support defect (ADR-P1-013).
**Consequences.** Phase 8's whole output ceiling is `IMPLEMENTED`/`LAB_TESTED`; nothing it can produce on
the JVM masquerades as device-verified.

### ADR-P8-004 — Structured errors reuse `OmniBudsErrorCategory`; no new category
**Status.** accepted — prompt §15; ADR-P3-006, ADR-P6-006 precedent
**Context.** §15 lists ~11 discovery error names. The existing category set already covers each with a
retry class (`RETRY_AFTER_REREAD`/`SAFE_TO_RETRY`/`NEVER_RETRY`).
**Decision.** Map §15 onto existing categories (audit §2 table); a per-feature failure arrives as an
`OperationOutcome.Failure` carrying an existing `OmniBudsErrorCategory`, and the engine records its
`PartialFailure.reason` as that category verbatim. Concretely: §15 SESSION_UNAVAILABLE → `CONNECTION_UNAVAILABLE`,
PROTOCOL_UNRESOLVED → `PROTOCOL_MISMATCH`, DISCOVERY_TIMEOUT → `TIMEOUT`, TRANSPORT_UNAVAILABLE →
`TRANSPORT_UNAVAILABLE`, PERMISSION_DENIED → `PERMISSION_DENIED`, DISCOVERY_UNSUPPORTED →
`UNSUPPORTED_OPERATION`, UNKNOWN_ERROR → `UNKNOWN_FAILURE`. A malformed response — the engine's own §17 guard
against an untrusted source answering about a feature other than the one asked — is recorded as
`CapabilityDiscoveryEngine.MALFORMED_RESPONSE_CATEGORY`, which *is* `INVALID_STATE`; `CAPABILITY_CONFLICT` is a
snapshot field (`UnresolvedConflict`), not a category, and `DEPENDENCY_INVALID` is a `DependencyStatus`, not one.
`CANCELLED` is `OperationOutcome.Cancelled`; discovery is read-only so no side-effecting retry exists to guard
beyond Phase 6/7's rules.
**Alternatives considered.** A `CapabilityDiscoveryError` category set — rejected: it duplicates meaning and
forces every `when` to grow, exactly what ADR-P3-006/ADR-P6-006 refused.
**Consequences.** One error taxonomy for the app; the unknown-vs-unsupported and retry/no-retry distinctions
are inherited, not re-decided.

### ADR-P8-005 — The engine consumes a handed-in read-only discovery source; it does not import the protocol layer
**Status.** accepted — prompt §9; the layer map (capability L2 cannot import protocol L4); ADR-P6-005 analogue
**Context.** §9 sketches `discover(session: DeviceSession)`. A naive engine would call `EarbudProtocol`/
`ProtocolSession` — but that is an upward L2→L4 edge `DependencyDirectionTest` rejects.
**Decision.** Define `CapabilityDiscoverySource` in `core.capability` (L2): a narrow read-only interface
(`attemptableFeatures(): List<FeatureId>`, `read(feature): OperationOutcome<DiscoveryReading>`) the engine is
*handed*, where `DiscoveryReading` bundles one feature's `FeatureCapability`, its `CapabilityAvailability` and
its `CapabilityEvidence`. The engine orchestrates source → evidence → evaluation → dependency validation →
snapshot. The wiring that binds a real Phase 7 `ProtocolSession` to a `CapabilityDiscoverySource` lives at
L3/L4 (session/DI), where depending downward on L2 is legal; Phase 8 ships no protocol-backed source, because
no protocol exists.
**Alternatives considered.** Import `EarbudProtocol` into the engine — rejected: uncompilable under the layer
map, and it would couple discovery to one protocol family. Put the engine in L4 — rejected: capability state
belongs to the L2 capability area and `DeviceState` (L3) already reads it downward.
**Consequences.** Discovery is deterministic and testable against a scripted source; the protocol↔capability
boundary is an interface, not an import; the engine never connects or writes (§9, §19).

### ADR-P8-006 — Evidence carries provenance, and weak evidence never silently overrides explicit contradiction
**Status.** accepted — prompt §8; ADR-P5-008, PROTO-CAP-001/-003
**Context.** §8 wants provenance (type/source/timestamp/protocol/version/verification/limitations) and rules
against marketing-as-proof and weak-over-strong.
**Decision.** `CapabilityEvidence(kind: EvidenceKind, source, atEpochMillis, protocolId, protocolVersion,
verification, detail, limitation)` records where a claim came from; the `EvidenceKind` is the point (§8):
"the device answered" (`EXPLICIT_DEVICE_RESPONSE`) and "a model resembles this one" (`INFERRED_MODEL`) are
different *kinds*, never averaged into a percentage — and `INFERRED_MODEL`/`UNKNOWN_OR_INCOMPLETE` are
construct-refused above `VerificationLevel.INFERRED`. The engine folds each reading through
`DeviceCapabilities.mergedWith`, so a weaker record cannot silently overwrite a stronger one on the evidence
ladder; when the same feature is reported at genuinely different `CapabilityState`s, the disagreement is kept
and surfaced as an `UnresolvedConflict(feature, kinds)` carrying the evidence kinds that disagreed — not
resolved by dropping either. A model's marketing text or an implemented-but-unrun parser can only ever yield
`INFERRED`/`IMPLEMENTED`, never device support.
**Alternatives considered.** Fold provenance into a bare `FeatureCapability` write — rejected: loses the
audit trail §8 requires and the conflict record. Score evidence numerically — rejected: ADR-P5-004 refuses
uncatalogued numbers.
**Consequences.** Every capability claim is traceable; conflicts survive to a human/later phase; the
unknown/unsupported/inferred distinction survives the engine.

### ADR-P8-007 — Dependencies are protocol/firmware data with cycle detection; a missing prerequisite is explicit and never auto-satisfied
**Status.** accepted — prompt §11; master §53
**Context.** §11 wants `HEAD_TRACKING → SPATIAL_AUDIO`-style edges that are *not* universal, cycle detection,
and no auto-enabling of prerequisites.
**Decision.** `CapabilityDependency(feature, requires, protocolId?, firmwareConstraint?)` lives in discovery
(per-protocol), never on `CapabilityDefinition` (which is device-agnostic by design); a self-edge
(`feature == requires`) is refused at construction. `DependencyValidator.validate(edges, capabilities)` returns
per-edge `DependencyStatus{SATISFIED, MISSING_PREREQUISITE, UNKNOWN_PREREQUISITE, CYCLE}` plus the `cycles` it
found — a cycle is *reported* (every member `CYCLE`, surfaced in `DependencyReport.cycles`), never broken by
silently dropping an edge. The engine maps only `MISSING_PREREQUISITE`/`CYCLE` (`blockedFeatures`) to an
availability of `UNAVAILABLE`; a positively-`UNSUPPORTED` prerequisite yields `MISSING_PREREQUISITE`, while an
*unknown* prerequisite yields `UNKNOWN_PREREQUISITE` and leaves the dependent's availability untouched — the
dependent is not blocked-unsupported on silence. It never infers a prerequisite's support from a dependent,
and never enables one.
**Alternatives considered.** Bake dependencies into `CoreFeature`/`CapabilityDefinition` — rejected: that
would assert a universal rule the prompt disclaims, and pollute the catalogue with device facts. Resolve a
cycle by drop-one — rejected: it hides a modeling error; a cycle is a bug to surface.
**Consequences.** Manufacturer-specific edges are representable without a lowest-common-denominator rule;
cycles fail loudly; the "declaration ≠ command will succeed" principle holds.

### ADR-P8-008 — The snapshot is deterministic, versioned, and wraps `DeviceCapabilities`; partial ≠ complete
**Status.** accepted — prompt §13/§10/§14; ADR-P8-001
**Context.** §13 wants a structured snapshot; §10 partial discovery; §14 a lifecycle where partial ≠ complete.
**Decision.** `CapabilitySnapshot(subjectRef: String?, protocolId, protocolVersion, discoveredAtEpochMillis,
completion: DiscoveryState, capabilities: DeviceCapabilities, availability: Map, evidence: List,
partialFailures, unresolvedConflicts, schemaVersion)` — immutable, deterministically ordered, with
`schemaVersion` for future migration. `subjectRef` is an opaque `String?` rather than a `DeviceIdentity`
because `capability` and `device` are the *same* layer (2) and a cross-area same-layer import is forbidden by
the dependency test — the snapshot references its subject without importing its type. One failed capability's
absence is a `partialFailure` and leaves it `UNKNOWN`; it never becomes `UNSUPPORTED` and never invalidates
unrelated successes. The `completion` rule is three-valued and enforced in the constructor:
`COMPLETE` ⇔ no `partialFailures`, `PARTIALLY_COMPLETE` ⇔ at least one *with* some established, `FAILED` when
there were failures and *nothing* established, `CANCELLED` mid-pass with what was gathered preserved.
`empty()` is a legal `NOT_STARTED` snapshot meaning "examined nothing".
**Alternatives considered.** A mutable capability registry the engine edits in place — rejected: breaks the
single-owner rule (`DeviceState` is the authority) and makes merge sites ungreppable. Persisting snapshots —
rejected: no storage in Phase 8 (§23), and a snapshot is a value.
**Consequences.** UI can be built on a stable, versioned, capability-driven contract (§16) without Phase 8
building any UI; partial truth is representable and honest.

### ADR-P8-009 — Vendor extensions reuse `VendorExtension`; unparseable extensions are preserved, not dropped
**Status.** accepted — prompt §12; ADR-P7-009; ADR-P1-008
**Context.** §12 wants namespaced, versioned vendor capabilities with no command implementation and no
collision with common features.
**Decision.** Reuse `VendorExtension`/`VendorFeatureMetadata` and namespaced `FeatureId` (already
collision-safe). An extension whose payload Phase 8 cannot type is preserved as an unrecognised extension
(its identity and raw metadata kept) rather than discarded or forced into a `CoreFeature` it is not. No
vendor feature is *implemented* (no commands — §12/§19).
**Alternatives considered.** A parallel vendor model — rejected: duplicates Phase 1. Coercing an unknown
vendor feature to the nearest common feature — rejected: that is the lowest-common-denominator collapse and a
false support claim.
**Consequences.** Unique vendor behaviour is preserved for later phases without any Phase 8 command existing;
an unknown stays unknown.

### ADR-P8-010 — The engine ships with no protocol and produces no capability claim it cannot evidence; ceiling `IMPLEMENTED`
**Status.** accepted — ADR-P1-013, ADR-P5-006, ADR-P7-010; the standing "build the product, skip the phone" directive
**Context.** With the empty registry and no device, real discovery reads nothing.
**Decision.** Phase 8 ships the engine, not any discovery result about a real device: driven by the empty
protocol state it yields an all-`UNKNOWN` snapshot, and the shipped ceiling is `IMPLEMENTED`/`LAB_TESTED`
(ADR-P8-003). Scripted `CapabilityDiscoverySource` fixtures live in test source only and are never presented
as device support (§18). Physical-device capability discovery is deferred, `NOT RUN`.
**Alternatives considered.** Seed plausible capability states to make the engine "look used" — rejected by
the product's founding principle and every prior phase's empty-registry discipline. A production scripted
source — rejected: a fake that reports a device supports ANC is the exact prohibited fabrication.
**Consequences.** The framework is proven correct while knowing nothing; Phase 9+ inherits an honest floor
and a deterministic engine to drive once a real protocol exists.
