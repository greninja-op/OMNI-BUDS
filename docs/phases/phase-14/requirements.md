# Phase 14 — Audio Path Validation: Requirements

**Status:** Authoritative for Phase 14 execution.
**Scope:** Validation layer determining whether observed audio transport,
device, routing, codec, and runtime-state information is internally
consistent and evidence-backed. No signal-path claims. No UI. No physical
device. No media interception.
**Requirement ID scheme:** `OB-P14-REQ-001` … `OB-P14-REQ-030`.

---

## OB-P14-REQ-001 — Validation result model

- **Description:** Structured result with: validation id, target
  device/session, category, status (VALID/INVALID/INCONCLUSIVE/
  NOT_OBSERVABLE/STALE/CONFLICT/UNSUPPORTED), evidence references,
  observation timestamps, reason, limitations, suggested next diagnostic.
- **Rationale:** Results must be explainable, not just boolean.
- **Priority:** Must
- **Acceptance criteria:** Sealed/enum status; all fields present.
- **Verification:** `ValidationResultTest`.

## OB-P14-REQ-002 — Status semantics

- **Description:** INCONCLUSIVE ≠ INVALID. NOT_OBSERVABLE ≠ UNSUPPORTED.
  VALID ≠ proof of audible output.
- **Rationale:** Precision prevents false confidence.
- **Priority:** Must
- **Acceptance criteria:** Documented + tested semantics.
- **Verification:** `ValidationSemanticsTest`.

## OB-P14-REQ-003 — Severity model

- **Description:** INFO/WARNING/ERROR/CRITICAL, separate from status.
  Warnings don't invalidate; missing optional params aren't critical.
- **Rationale:** Severity ≠ status.
- **Priority:** Must
- **Acceptance criteria:** Documented severity policy.
- **Verification:** `SeverityPolicyTest`.

## OB-P14-REQ-004 — Device association validation

- **Description:** Audio observations bound to correct DeviceIdentity;
  session ids match; disconnected devices don't retain current routes;
  cross-device state isolation; reconnect refreshes association.
- **Rationale:** Wrong-device attribution is a hard error.
- **Priority:** Must
- **Acceptance criteria:** Mismatch → ERROR/CRITICAL.
- **Verification:** `DeviceAssociationTest`.

## OB-P14-REQ-005 — Transport validation

- **Description:** Consistency among Classic/A2DP/HFP/HSP/LE Audio,
  audio-device observations, route observations. Each as separate evidence.
- **Rationale:** Transport ≠ profile ≠ device type ≠ route.
- **Priority:** Must
- **Acceptance criteria:** Collapsed fields rejected.
- **Verification:** `TransportValidationTest`.

## OB-P14-REQ-006 — Codec validation

- **Description:** Codec identity vs transport, profile, capability snapshot,
  runtime state, negotiation state, session, timestamp. LC3+LE Audio valid;
  LC3-as-A2DP rejected; database-only codec never "active"; stale never
  overrides fresh.
- **Rationale:** Codec claims need evidence chains.
- **Priority:** Must
- **Acceptance criteria:** All examples tested.
- **Verification:** `CodecValidationTest`.

## OB-P14-REQ-007 — Route validation

- **Description:** Available devices, selected route, routing state, device
  events, lifecycle, media vs communication. Only adapter-exposed info.
- **Rationale:** Route ≠ audible media.
- **Priority:** Must
- **Acceptance criteria:** Unselected ≠ inactive; route changes tracked.
- **Verification:** `RouteValidationTest`.

## OB-P14-REQ-008 — Parameter validation

- **Description:** Sample rate, bit depth, bitrate, channel, quality mode,
  adaptive mode — domain constraints only. No defaults for missing; no
  rejection of uncommon-but-legal values.
- **Rationale:** Parameters are facts, not guesses.
- **Priority:** Must
- **Acceptance criteria:** Null stays null; legal uncommon values pass.
- **Verification:** `ParameterValidationTest`.

## OB-P14-REQ-009 — Freshness validation

- **Description:** Reuse Phase 13 freshness (CURRENT/STALE/UNKNOWN). No
  competing system.
- **Rationale:** One freshness vocabulary.
- **Priority:** Must
- **Acceptance criteria:** Phase 13 types reused.
- **Verification:** `FreshnessValidationTest`.

## OB-P14-REQ-010 — Lifecycle validation

- **Description:** Connection → availability → route → codec → negotiation →
  disconnection → reconnection → stale invalidation. Late/out-of-order events
  cannot restore obsolete active state.
- **Rationale:** Time matters.
- **Priority:** Must
- **Acceptance criteria:** Obsolete events rejected.
- **Verification:** `LifecycleValidationTest`.

## OB-P14-REQ-011 — Deterministic pipeline

- **Description:** Observations → Normalization → Evidence Collection → Rule
  Evaluation → Results → Diagnostic Summary. Pure where possible.
- **Rationale:** Reproducible validation.
- **Priority:** Must
- **Acceptance criteria:** Same input → same output.
- **Verification:** `ValidationPipelineTest`.

## OB-P14-REQ-012 — Evidence provenance

- **Description:** Every result cites observations + rules. Reuse Phase 11/13
  evidence/confidence. Never upgrade INFERRED → OBSERVED.
- **Rationale:** Provenance is the honesty mechanism.
- **Priority:** Must
- **Acceptance criteria:** Evidence on all meaningful results.
- **Verification:** `EvidenceProvenanceTest`.

## OB-P14-REQ-013 — Rule architecture

- **Description:** Statically defined Kotlin rules, each with: stable id,
  description, applicable transport/profile, required evidence,
  preconditions, logic, severity, failure explanation, tests, doc reference.
  Hard contradictions vs warnings vs missing info separated.
- **Rationale:** Maintainable, testable.
- **Priority:** Must
- **Acceptance criteria:** All rules documented + tested.
- **Verification:** `ValidationRuleTest`.

## OB-P14-REQ-014 — Immutable snapshots

- **Description:** `AudioPathValidationSnapshot`: id, device/session,
  timestamp, transport, device/route association, codec/negotiation summary,
  freshness summary, results, overall status, evidence, limitations. One
  coherent evaluation; no cross-device mixing.
- **Rationale:** Snapshots are the unit of truth.
- **Priority:** Must
- **Acceptance criteria:** Immutable; coherent.
- **Verification:** `ValidationSnapshotTest`.

## OB-P14-REQ-015 — Aggregation policy

- **Description:** Deterministic: hard violation → never VALID; missing
  optional → never INVALID; valid+unknown → uncertainty retained; conflict
  visible; stale ≠ current; unevaluatable rules listed.
- **Rationale:** The summary must not hide failures.
- **Priority:** Must
- **Acceptance criteria:** Documented + tested.
- **Verification:** `AggregationPolicyTest`.

## OB-P14-REQ-016 — Cross-source consistency

- **Description:** Compare sources without assuming equal capability.
  Timestamps + session generations decide; unresolvable → CONFLICT/
  INCONCLUSIVE, never arbitrary winner.
- **Rationale:** Fresh truth wins; ties are honest.
- **Priority:** Must
- **Acceptance criteria:** Precedence applied; conflicts flagged.
- **Verification:** `CrossSourceTest`.

## OB-P14-REQ-017 — Event ordering

- **Description:** Duplicates, delays, out-of-order, disconnect-during-
  validation, route-change-during-evaluation handled. Session generations
  gate obsolete events. No arbitrary delays.
- **Rationale:** Async reality.
- **Priority:** Must
- **Acceptance criteria:** Obsolete events cannot restore state.
- **Verification:** `EventOrderingTest`.

## OB-P14-REQ-018 — Validation triggers

- **Description:** Event-driven: lifecycle, device add/remove, route change,
  codec change, negotiation change, disconnect, reconnect, explicit refresh.
  No polling; no duplicate observers.
- **Rationale:** Reactive, not busy.
- **Priority:** Must
- **Acceptance criteria:** Triggers wired; no polling (scope test).
- **Verification:** `ValidationTriggerTest`.

## OB-P14-REQ-019 — StateFlow publication

- **Description:** Immutable snapshots via StateFlow; meaningful-change
  detection; duplicate suppression; cancellation-safe; bounded; no orphans;
  no device leakage; sane post-disconnect behavior.
- **Rationale:** Reactive consumers.
- **Priority:** Must
- **Acceptance criteria:** Dedup tested; lifecycle-safe.
- **Verification:** `ValidationFlowTest`.

## OB-P14-REQ-020 — Signal-path honesty

- **Description:** VALID means "observations consistent", never "audible
  output confirmed". Signal-path claims (audible output, waveform, quality,
  packet loss, latency) are NOT_OBSERVABLE without a real measurement
  mechanism. No capture/loopback workarounds.
- **Rationale:** The phase's core honesty boundary.
- **Priority:** Must
- **Acceptance criteria:** Scope test bans signal claims.
- **Verification:** `SignalPathHonestyTest`.

## OB-P14-REQ-021 — Media/communication separation

- **Description:** A2DP ≠ HFP/HSP; LE Audio routing ≠ Classic routing.
  Validate each path's transport/profile.
- **Rationale:** Paths are distinct.
- **Priority:** Must
- **Acceptance criteria:** Separation enforced.
- **Verification:** `MediaCommunicationTest`.

## OB-P14-REQ-022 — Multi-device isolation

- **Description:** Independent snapshots per device. A→LDAC/B→AAC; A
  disconnects/B unaffected; route A→B; stale event for A after route change
  cannot corrupt B.
- **Rationale:** No global validation state.
- **Priority:** Must
- **Acceptance criteria:** Isolation tested.
- **Verification:** `MultiDeviceValidationTest`.

## OB-P14-REQ-023 — Error model

- **Description:** Reuse structured errors. Inconclusive ≠ exception.
- **Rationale:** Missing info is a result, not a crash.
- **Priority:** Must
- **Acceptance criteria:** New categories only if unmappable.
- **Verification:** Review.

## OB-P14-REQ-024 — Performance

- **Description:** No polling, unbounded history, redundant recomputation,
  blocking, sleeps, unbounded retries, or main-thread work.
- **Rationale:** Lightweight validator.
- **Priority:** Must
- **Acceptance criteria:** Scope test; review.
- **Verification:** `ValidationScopeTest`.

## OB-P14-REQ-025 — Logging

- **Description:** Log violations, conflicts, stale detection, mismatches,
  invalid transitions, observation failures, recovery. No addresses; no
  secrets.
- **Rationale:** Observability of the validator.
- **Priority:** Should
- **Acceptance criteria:** No PII in logs.
- **Verification:** Review.

## OB-P14-REQ-026 — Android integration

- **Description:** Public APIs only; respect min SDK; no hidden APIs/
  reflection/root/shell; no mic permission for validation.
- **Rationale:** Legitimate observation only.
- **Priority:** Must
- **Acceptance criteria:** Adapter reuses Phase 10/13 sources.
- **Verification:** `AndroidValidationTest`.

## OB-P14-REQ-027 — Architecture

- **Description:** Domain validation engine Android-independent; Android
  classes inside the Android boundary.
- **Rationale:** Testable core.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P14-REQ-028 — Documentation

- **Description:** Eight mandatory records covering architecture, rules,
  statuses/severity, provenance, aggregation, precedence, ordering,
  generations, freshness, consistency, multi-device, Android limits,
  signal-path boundaries, tests, risks.
- **Rationale:** The validator must be understandable.
- **Priority:** Must
- **Acceptance criteria:** All files present and accurate.
- **Verification:** Review.

## OB-P14-REQ-029 — Regression

- **Description:** All Phase 0–13 tests pass; no UI; no media path changes.
- **Rationale:** Phase boundary.
- **Priority:** Must
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.

## OB-P14-REQ-030 — Stop condition

- **Description:** Phase 15 not started; no UI; no physical device; no
  capture/loopback.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Final audit.
- **Verification:** Scope tests.
