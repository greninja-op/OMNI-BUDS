# Phase 40 — Test Plan

## New tests (4 classes, 13 tests)
- VendorIntegrationContractTest (3): version constant, blank id,
  bad version.
- FirmwareCompatibilityEvaluatorTest (4): verified, incompatible,
  firmware-agnostic, missing version.
- VendorResolverTest (3): exact match, unknown device, ambiguity.
- VendorIsolationTest (4): independent registration, no protocol
  leak, failure isolation, removal.

## Regression
Full suite: core + android, 0 failures.
