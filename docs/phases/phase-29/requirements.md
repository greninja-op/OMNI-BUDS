# Phase 29 — Feature Dependency & Conflict Engine: Requirements

**Status:** Authoritative for Phase 29 execution.
**Scope:** Provenance/trust layer, conflict evaluator (hard vs advisory),
pure operation planner, device-scoped concurrency. Reuses Phase 9's
`FeatureRelation`/`FeatureDependencyEvaluator` — no duplication.
**Requirement ID scheme:** `OB-P29-REQ-001` … `OB-P29-REQ-024`.

## OB-P29-REQ-001 — Typed relationship provenance
- **Description:** Every rule carries a stable ID, verification status
  (INFERRED → IMPLEMENTED → LAB_TESTED → HARDWARE_VERIFIED →
  PERSISTENCE_VERIFIED), device/firmware scope, evidence references,
  and effective/superseded status.
- **Priority:** Must | **Verification:** `DependencyProvenanceTest`.

## OB-P29-REQ-002 — Inferred is not verified
- **Description:** INFERRED relationships never produce hard conflicts;
  at most advisory or unresolved.
- **Priority:** Must | **Verification:** `ConflictEvaluatorTest`.

## OB-P29-REQ-003 — Hard conflicts block
- **Description:** Sufficiently verified conflicts make the configuration
  invalid and block execution.
- **Priority:** Must | **Verification:** `ConflictEvaluatorTest`.

## OB-P29-REQ-004 — Advisory warnings distinct
- **Description:** Warnings are explainable, never silently upgraded to
  hard restrictions.
- **Priority:** Must | **Verification:** `ConflictEvaluatorTest`.

## OB-P29-REQ-005 — Capability-aware evaluation
- **Description:** Evaluate against the current capability snapshot;
  distinguish unknown/unsupported/unavailable/available/enabled/pending/
  stale. Unknown prerequisites are never treated as satisfied.
- **Priority:** Must | **Verification:** `ConflictEvaluatorTest`.

## OB-P29-REQ-006 — Cycle detection
- **Description:** Dependency cycles detected before planning; no
  executable plan from a cycle.
- **Priority:** Must | **Verification:** `DependencyGraphTest`.

## OB-P29-REQ-007 — Graph validation
- **Description:** Deterministic validation of endpoints, duplicates,
  contradictions, scope, rule versions. Pure — no hardware commands.
- **Priority:** Must | **Verification:** `DependencyGraphTest`.

## OB-P29-REQ-008 — Pure operation planning
- **Description:** Planning inspects state and builds plans; never sends
  commands. Separate from execution.
- **Priority:** Must | **Verification:** `OperationPlannerTest`.

## OB-P29-REQ-009 — Plan invalidation
- **Description:** Capability changes, session invalidation, or state
  changes invalidate affected plans; stale plans rejected or replanned.
- **Priority:** Must | **Verification:** `OperationPlannerTest`.

## OB-P29-REQ-010 — No automatic prerequisite changes
- **Description:** The engine never silently changes another feature to
  satisfy a dependency. Unsatisfied relations refuse with explanation.
- **Priority:** Must | **Verification:** `OperationPlannerTest`.

## OB-P29-REQ-011 — Feature-engine integration
- **Description:** All hardware actions continue through the existing
  feature engine and access policy. No alternate command path.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-012 — Device-scoped concurrency
- **Description:** Per-device coordination prevents incompatible races;
  unrelated devices proceed concurrently; no global locks on Bluetooth I/O.
- **Priority:** Must | **Verification:** `DependencyConcurrencyTest`.

## OB-P29-REQ-013 — Partial failure honesty
- **Description:** Distinguish no-op/rejected/partial/accepted-unconfirmed/
  completed/compensation states. No fabricated rollback.
- **Priority:** Must | **Verification:** `OperationPlannerTest`.

## OB-P29-REQ-014 — Compensation boundaries
- **Description:** Compensation defined only with verified semantics;
  never assume inverse commands restore.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-015 — Rule scope isolation
- **Description:** Device-specific rules never leak across device
  identities; firmware-scope changes trigger revalidation.
- **Priority:** Must | **Verification:** `DependencyProvenanceTest`.

## OB-P29-REQ-016 — Superseded rules
- **Description:** Obsolete rules marked superseded, never silently
  reinterpreted.
- **Priority:** Must | **Verification:** `DependencyProvenanceTest`.

## OB-P29-REQ-017 — Typed outcomes
- **Description:** Explainable outcomes for all §15 error cases; no raw
  exception strings for future UI.
- **Priority:** Must | **Verification:** `DependencyErrorTest`.

## OB-P29-REQ-018 — Deterministic evaluation
- **Description:** Same normalized inputs → same results; deterministic
  order.
- **Priority:** Must | **Verification:** `DependencyGraphTest`.

## OB-P29-REQ-019 — No scope creep
- **Description:** No vendor protocols, UI, presentation features,
  transports, or firmware modification.
- **Priority:** Must | **Verification:** Scope review.

## OB-P29-REQ-020 — Vendor framework integration
- **Description:** Vendor-declared relationships registered with their
  trust level preserved.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-021 — Knowledge-database integration
- **Description:** Scoped relationship loading from the protocol
  knowledge database; invalid persisted rule sets rejected safely.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-022 — Persistence versioning
- **Description:** Relationship schemas and rule sets versioned; cached
  graphs invalidated on capability/firmware change.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-023 — Documentation
- **Description:** 12 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P29-REQ-024 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.
