# Phase 41 — Test Plan

## New tests (2 classes, 12 tests)
- VendorEvidenceTest (6): valid, blank rejected, hardware needs
  date, empty inferred, max level, hardware-verified check.
- NullVendorAdapterContractTest (6 via harness): stable ID,
  unobserved never matches, deterministic, protocol ID, evidence
  consistency, empty evidence inferred.

## Regression
Full suite: core + android, 0 failures.
