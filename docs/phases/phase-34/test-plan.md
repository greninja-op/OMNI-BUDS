# Phase 34 — Test Plan

## New tests (4 classes, 26 tests)
- FailureClassifierTest (6): transient/ambiguous/permanent mapping,
  no false inferences, metadata privacy.
- RecoveryPolicyTest (12): cancellation, supersession, ambiguous
  writes, permission, adapter, budget exhaustion, background,
  unsupported, stale, authorization, determinism.
- RecoveryStateMachineTest (5): happy path, illegal transitions,
  retry loop-back, terminal reset, cancellation.
- RecoveryEventSinkTest (3): bounded eviction, privacy, clear.

## Regression
Full suite: core + android, 0 failures.
