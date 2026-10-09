# Phase 31 — Decisions

## D-31-01: build on Phase 30
**Decision:** Cross-device testing extends testkit, not a new runner.
**Rationale:** One deterministic infrastructure.

## D-31-02: capability tags for selection
**Decision:** `requires-capability:<id>` tags drive applicability.
**Rationale:** Declarative, auditable, deterministic.

## D-31-03: no bare booleans
**Decision:** Verdicts carry scope, results, evidence, limitations.
**Rationale:** Compatibility without context is a lie.

## D-31-04: immutable baselines
**Decision:** Baselines versioned; never auto-updated.
**Rationale:** Silent baseline changes hide regressions.

## D-31-05: synthetic stays synthetic
**Decision:** No promotion without hardware evidence.
**Rationale:** Prevents overstated compatibility claims.
