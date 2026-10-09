# Phase 29 — Design

## Modules

`core/dependency/` (layer 5):

- `RelationshipProvenance` — verification ladder, scope, evidence,
  superseded status.
- `RelationshipGraph` — deterministic validation: duplicates, cycles,
  contradictions.
- `ConflictEvaluator` — pure evaluation → HardConflict / Advisory /
  Unresolved.
- `OperationPlanner` — pure planning, dependency ordering, freshness.
- `DeviceOperationCoordinator` — per-device admission, no global I/O locks.

## Reuse

Phase 9's `FeatureRelation` (Requires, RequiresOneOf, ConflictsWith,
MutuallyExclusive, Implies, VendorException) and
`FeatureDependencyEvaluator` are reused, not duplicated. Phase 22's
evidence/claim model supplies verification vocabulary.

## Data flow

Rules + provenance → graph build → device-scoped active rules →
proposed config + capability snapshot → conflict evaluation →
plan (pure) → admission → existing feature engine executes.

## Lifecycle

Rules versioned; cached graphs invalidated on capability/firmware
change; plans invalidated on state/session/rule change.

## Concurrency

Per-device mutexes guard admission only; devices independent.

## Security boundaries

- Inferred rules never block.
- No automatic prerequisite changes.
- All execution through the existing feature engine + access policy.
