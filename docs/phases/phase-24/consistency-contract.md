# Phase 24 — Consistency Contract

## Consistent snapshot

A snapshot is consistent when:
- Every applied event passed validation, ordering, and session checks.
- Related fields from one event were applied atomically.
- No half-applied updates are exposed.

## Temporarily unavailable fields

Null or `Unknown` states are valid. The snapshot may be partial at any
time (e.g. battery unknown while protocol resolves).

## Partial updates

Submodels update independently. A snapshot with resolved protocol but
undiscovered capabilities is normal, not inconsistent.

## Cross-subsystem atomicity

The engine guarantees per-event atomicity (one event → one coherent
snapshot). It does not claim globally atomic cross-subsystem snapshots;
each submodel carries its own provenance and freshness.

## Corrections

The consistency validator reports violations with codes. It applies no
silent corrections. Documented deterministic rules only.
