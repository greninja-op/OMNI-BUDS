# Phase 37 — Decisions

## D-37-01: reuse the lab parser contract
**Decision:** Runner executes LabParser, not a new parser API.
**Rationale:** Test real contracts, not adapters.

## D-37-02: declarative cases only
**Decision:** No executable content in test definitions.
**Rationale:** The runner is not an attack surface.

## D-37-03: fixture IDs are plain identifiers
**Decision:** No paths in fixture references.
**Rationale:** Prevents directory escape.

## D-37-04: sequential execution
**Decision:** No concurrency in the runner.
**Rationale:** Determinism and isolation without locks.

## D-37-05: evidence never auto-upgrades
**Decision:** Test results cannot promote knowledge evidence.
**Rationale:** Simulated success ≠ hardware verification.
