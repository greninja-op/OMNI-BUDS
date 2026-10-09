# Phase 29 — Specifications

## Verification ladder

INFERRED → IMPLEMENTED → LAB_TESTED → HARDWARE_VERIFIED →
PERSISTENCE_VERIFIED. Only the last two may produce hard conflicts.

## Rule identity

Stable `ruleId`; rule-set version; scope (manufacturer/model/firmware);
evidence ids; superseded flag (inert when set).

## Graph validation

- Duplicate rule ids rejected.
- Requires-cycle detection (deterministic DFS); cycles → invalid graph.
- Requires+ConflictsWith on the same pair → contradiction.
- Sorted deterministic order.

## Conflict evaluation

Pure; per proposed configuration; availability states:
UNKNOWN/UNSUPPORTED/UNAVAILABLE/AVAILABLE/ENABLED/PENDING/STALE.
Unknown/stale prerequisites → Unresolved. Unsupported → hard (if
verified) or advisory (if inferred).

## Operation plans

`planId`, device, session, ordered steps, stateVersion, ruleSetVersion,
createdAtMillis. Freshness checked on state/session/rule version.
Stale plans invalidated, never executed.

## Concurrency

Per-device admission; overlapping feature sets rejected; disjoint
coexist; devices independent.
