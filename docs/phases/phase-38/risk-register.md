# Phase 38 — Risk Register

## R-38-01: Safety policy bypassed at a future call site
**Severity:** High | **Likelihood:** Low
**Mitigation:** Defaults deny; invariants in init; gate tests.
**Residual:** Low.

## R-38-02: Simulated result mistaken for hardware verification
**Severity:** High | **Likelihood:** Low
**Mitigation:** Environment recorded; physical checks deferred.
**Residual:** Low.

## R-38-03: Fake rig diverges from real rig behavior
**Severity:** Medium | **Likelihood:** Medium
**Mitigation:** Rig interface minimal; Phase 52 will validate.
**Residual:** Medium.

## R-38-04: Cleanup failures hidden
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Cleanup stage outcome preserved in report.
**Residual:** Low.
