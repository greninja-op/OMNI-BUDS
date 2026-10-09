# Phase 29 — Feature Relationship Model

## Relationship types (from Phase 9, reused)

| Type | Meaning |
|---|---|
| Requires | Feature needs a prerequisite established |
| RequiresOneOf | One of several alternatives must be established |
| ConflictsWith | Two features cannot both be enabled |
| MutuallyExclusive | A set with at most one active member |
| Implies | Informational; never auto-enables |
| VendorException | Relaxes a rule for a scoped device |

## Provenance (Phase 29)

Each rule adds: stable ID, verification ladder, device/firmware scope,
evidence references, rule-set version, superseded status, limitations.

## Conditional dependencies

Expressed as scope + verification, not executable scripts. A rule whose
firmware range cannot be evaluated fails closed (not applied).

## Ordering constraints

Requires edges also drive plan ordering (prerequisites first).
Separate from persistent dependencies only in that ordering is
recomputed per plan from the validated graph.
