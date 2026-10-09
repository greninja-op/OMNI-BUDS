# Phase 21 — Risk Register

## R-21-01: Policy bypass via direct feature-engine calls
**Likelihood:** Medium | **Impact:** High
Callers could invoke feature engines without consulting the policy.
**Mitigation:** Document the boundary; architecture test scope guards.
**Residual:** Integration point for Phase 22.

## R-21-02: Stale freshness inputs
**Likelihood:** Medium | **Impact:** Medium
The policy trusts caller-supplied `evidenceFresh`.
**Mitigation:** Session layer owns freshness policy; lifecycle manager
downgrades on staleness events.

## R-21-03: Observation inference creep
**Likelihood:** Low | **Impact:** Medium
Future observers may fill unknown with defaults.
**Mitigation:** `recordUnknown` pattern + tests asserting null.

## R-21-04: Cancellation honoring
**Likelihood:** Medium | **Impact:** Medium
`cancelInFlight` is a signal; callers must honor it.
**Mitigation:** Documented contract; tests cover the signal.
