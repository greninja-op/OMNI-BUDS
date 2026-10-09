# Phase 29 — Risk Register

## R-29-01: Invented restrictions
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Verification ladder; inferred rules advisory only.

## R-29-02: Stale plan executes
**Likelihood:** Low | **Impact:** High
**Mitigation:** Freshness revalidation on state/session/rule version.

## R-29-03: Conflicting concurrent operations
**Likelihood:** Medium | **Impact:** Medium
**Mitigation:** Per-device admission; overlap rejection.

## R-29-04: Rule leaks across devices
**Likelihood:** Low | **Impact:** High
**Mitigation:** Device/firmware scoping; fail-closed on unevaluable scope.

## R-29-05: Unsafe compensation
**Likelihood:** Low | **Impact:** High
**Mitigation:** Compensation only with verified semantics; honest
partial-failure reporting.
