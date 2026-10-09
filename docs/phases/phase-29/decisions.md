# Phase 29 — Decisions

## D-29-01: reuse Phase 9 relations
**Decision:** Build on `FeatureRelation`/`FeatureDependencyEvaluator`;
add provenance, conflict severity, planning, concurrency.
**Rationale:** No duplication; established semantics preserved.

## D-29-02: inferred never blocks
**Decision:** Only HARDWARE_VERIFIED/PERSISTENCE_VERIFIED produce hard
conflicts.
**Rationale:** Prevents invented restrictions from blocking real use.

## D-29-03: no automatic prerequisite changes
**Decision:** Unsatisfied relations reject with explanation.
**Rationale:** Silent hardware changes are unsafe.

## D-29-04: pure planning
**Decision:** Planning never sends commands; execution revalidates.
**Rationale:** Stale plans must not execute blindly.

## D-29-05: per-device admission
**Decision:** Coordinator guards admission, not execution; devices
independent.
**Rationale:** No global locks across Bluetooth I/O.
