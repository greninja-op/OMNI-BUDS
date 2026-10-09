# Phase 18 — Persistence Verification Framework: Requirements

**Status:** Authoritative for Phase 18 execution.
**Scope:** Protocol-independent verification of whether hardware config was
requested/accepted/applied/read-back/retained across lifecycle boundaries.
Evidence-proven scopes only. No UI, no vendor expansion, no physical hardware.
**Requirement ID scheme:** `OB-P18-REQ-001` … `OB-P18-REQ-028`.

---

## OB-P18-REQ-001 — Verification identity

- **Description:** Stable `VerificationId`; record identifies device
  (identityKey, never raw address), feature, expected value, protocol,
  plan, timestamps, stage, outcome, evidence history, correlation IDs,
  completion reason.
- **Rationale:** Auditability.
- **Priority:** Must
- **Acceptance criteria:** All fields present.
- **Verification:** `VerificationModelTest`.

## OB-P18-REQ-002 — Stages vs outcomes

- **Description:** `VerificationStage` (progress) separate from
  `VerificationOutcome` (terminal). Plans skip inapplicable stages.
- **Rationale:** Progress ≠ result.
- **Priority:** Must
- **Acceptance criteria:** Two enums; transition tests.
- **Verification:** `VerificationStateMachineTest`.

## OB-P18-REQ-003 — Persistence scopes

- **Description:** Ordered scopes: SESSION_ONLY < CONTROL_SESSION <
  CONNECTION < APP_RESTART < DEVICE_REBOOT < FIRMWARE < UNKNOWN.
  Each with explicit, testable meaning.
- **Rationale:** Persistence has degrees.
- **Priority:** Must
- **Acceptance criteria:** Ordering; scope tests.
- **Verification:** `PersistenceScopeTest`.

## OB-P18-REQ-004 — Application status

- **Description:** NOT_ATTEMPTED / REQUESTED / ACKNOWLEDGED /
  READ_BACK_CONFIRMED / REJECTED / UNKNOWN — independent dimension.
- **Rationale:** Application ≠ persistence.
- **Priority:** Must
- **Acceptance criteria:** Six states.
- **Verification:** `VerificationModelTest`.

## OB-P18-REQ-005 — Evidence model

- **Description:** Typed evidence: id, verification, device, feature,
  expected/observed values, type, source, protocol version, timestamps,
  session/generation, freshness, classification, correlation, limitations.
  No raw payloads by default.
- **Rationale:** Evidence is auditable.
- **Priority:** Must
- **Acceptance criteria:** All fields; provenance tests.
- **Verification:** `VerificationEvidenceTest`.

## OB-P18-REQ-006 — Evidence rules

- **Description:** Local write ≠ hardware evidence. Ack ≠ read-back.
  Stale/cross-session evidence cannot establish current state. Conflicts
  preserved. No fabricated read-back.
- **Rationale:** Honesty rules.
- **Priority:** Must
- **Acceptance criteria:** Each rule tested.
- **Verification:** `EvidenceRulesTest`.

## OB-P18-REQ-007 — Deterministic evaluation

- **Description:** `EvidenceEvaluator` computes strongest proven scope
  from evidence. No numeric confidence scores. Pure function.
- **Rationale:** Testable truth.
- **Priority:** Must
- **Acceptance criteria:** Evaluator tests.
- **Verification:** `EvidenceEvaluatorTest`.

## OB-P18-REQ-008 — Verification plans

- **Description:** Declarative plan: target, requested scope, required ops,
  observation methods, boundaries, read-back support, timeouts, retries,
  safety, evidence requirements, completion/inconclusive conditions.
  Derived from actual capabilities.
- **Rationale:** Protocols differ.
- **Priority:** Must
- **Acceptance criteria:** Plan model; capability-derived.
- **Verification:** `VerificationPlanTest`.

## OB-P18-REQ-009 — Eligibility

- **Description:** Check identity, protocol, capability (6 access states),
  constraints, read-back support, scope observability, no conflicts,
  safety. Impossible scope → UNSUPPORTED/INCONCLUSIVE, no writes.
- **Rationale:** Don't attempt the impossible.
- **Priority:** Must
- **Acceptance criteria:** Eligibility tests.
- **Verification:** `VerificationEligibilityTest`.

## OB-P18-REQ-010 — Event-driven workflow

- **Description:** State machine consumes events (acknowledged, read-back,
  timeout, disconnect, reconnect, cancelled). Caller invokes ports;
  framework processes outcomes. No manufactured lifecycle events.
- **Rationale:** Separation of driving vs evaluating.
- **Priority:** Must
- **Acceptance criteria:** Event tests; no port imports.
- **Verification:** `VerificationWorkflowTest`.

## OB-P18-REQ-011 — Read-back comparison

- **Description:** Feature-specific comparison: exact/normalized/mismatch/
  unavailable/malformed. No unrelated-format equivalence.
- **Rationale:** Comparison needs semantics.
- **Priority:** Must
- **Acceptance criteria:** Comparison tests.
- **Verification:** `ReadBackComparisonTest`.

## OB-P18-REQ-012 — Timeout/cancellation

- **Description:** Timeout → ambiguous (never success/failure). Cancellation
  cleans up and records outcome. Bounded retries; no retry after ambiguous
  write unless safe.
- **Rationale:** Ambiguity is honest.
- **Priority:** Must
- **Acceptance criteria:** Timeout/cancel tests.
- **Verification:** `VerificationLifecycleTest`.

## OB-P18-REQ-013 — Concurrency

- **Description:** Conflicting writes serialized/rejected; independent
  devices concurrent; duplicate/out-of-order events handled; no leaked
  observers.
- **Rationale:** Async reality.
- **Priority:** Must
- **Acceptance criteria:** Concurrency tests.
- **Verification:** `VerificationConcurrencyTest`.

## OB-P18-REQ-014 — Multi-device isolation

- **Description:** Per-device verification state; evidence never crosses devices.
- **Rationale:** Devices are independent.
- **Priority:** Must
- **Acceptance criteria:** Isolation tests.
- **Verification:** `VerificationConcurrencyTest`.

## OB-P18-REQ-015 — Repository

- **Description:** Persist verification records + evidence via Phase 17
  mechanisms. Schema versioning, migrations, atomic writes, corruption
  handling, retention, recovery of interrupted verifications.
- **Rationale:** Auditability survives restart.
- **Priority:** Must
- **Acceptance criteria:** Repository tests.
- **Verification:** `VerificationRepositoryTest`.

## OB-P18-REQ-016 — Recovery

- **Description:** Restart recovers interrupted records; determines
  completed stages, valid evidence, observations to repeat. Never marks
  incomplete as complete.
- **Rationale:** Honest recovery.
- **Priority:** Must
- **Acceptance criteria:** Recovery tests.
- **Verification:** `VerificationRepositoryTest`.

## OB-P18-REQ-017 — Failure classification

- **Description:** Structured reasons (20 categories adapted to existing
  types). Every outcome states what was/wasn't established.
- **Rationale:** Failures are informative.
- **Priority:** Must
- **Acceptance criteria:** Classification tests.
- **Verification:** Review.

## OB-P18-REQ-018 — Phase 17 separation

- **Description:** Local preference ≠ hardware evidence. Restore treats
  config as desired until observation establishes hardware state.
- **Rationale:** Core principle.
- **Priority:** Must
- **Acceptance criteria:** Separation tests.
- **Verification:** `PhaseSeparationTest`.

## OB-P18-REQ-019 — No fabrication

- **Description:** Never assume ack=persistence, read-back=reboot-proof,
  local=hardware, restart=verified. Unknown stays unknown.
- **Rationale:** The phase's hard boundary.
- **Priority:** Must
- **Acceptance criteria:** Negative tests per rule; scope tests.
- **Verification:** `NoFabricationTest`.

## OB-P18-REQ-020 — No audio interference

- **Description:** No media capture/decode/intercept. Codec via existing
  interfaces only.
- **Rationale:** Audio architecture preserved.
- **Priority:** Must
- **Acceptance criteria:** Scope test.
- **Verification:** `VerificationScopeTest`.

## OB-P18-REQ-021 — Security/privacy

- **Description:** No address retention, no payload logging, no credentials,
  bounded evidence history, local only.
- **Rationale:** Boundaries.
- **Priority:** Must
- **Acceptance criteria:** Scope tests; review.
- **Verification:** `VerificationScopeTest`.

## OB-P18-REQ-022 — Architecture

- **Description:** `core.verification` at layer 5; no `feature` imports;
  event-driven (caller drives ports).
- **Rationale:** Boundaries.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P18-REQ-023 — Documentation

- **Description:** Eight mandatory records, accurate to implementation.
- **Rationale:** Understandability.
- **Priority:** Must
- **Acceptance criteria:** All present.
- **Verification:** Review.

## OB-P18-REQ-024 — Regression

- **Description:** Phases 7–17 unchanged in behavior.
- **Rationale:** No regressions.
- **Priority:** Must
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.

## OB-P18-REQ-025 — No UI/vendor expansion

- **Description:** No UI, no vendor commands, Phase 19 not started.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Scope tests.
- **Verification:** `VerificationScopeTest`.

## OB-P18-REQ-026 — Deterministic tests

- **Description:** All tests use fakes/mocks; no physical hardware.
- **Rationale:** Reproducibility.
- **Priority:** Must
- **Acceptance criteria:** No hardware in tests.
- **Verification:** Review.

## OB-P18-REQ-027 — Baseline capture

- **Description:** Capture device-observed baseline where supported;
  record limitation when unavailable. Never assume baseline = preference.
- **Rationale:** Change needs a reference.
- **Priority:** Should
- **Acceptance criteria:** Baseline tests.
- **Verification:** `VerificationWorkflowTest`.

## OB-P18-REQ-028 — Terminal immutability

- **Description:** Terminal outcomes cannot transition back to active.
  Restart creates new attempt.
- **Rationale:** History is immutable.
- **Priority:** Must
- **Acceptance criteria:** Transition tests.
- **Verification:** `VerificationStateMachineTest`.
