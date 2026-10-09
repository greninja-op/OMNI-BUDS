# Phase 27 — Risk Register

## R-27-01: Stale widget action hits wrong device
**Likelihood:** Low | **Impact:** High
**Mitigation:** Per-instance binding revalidated; mismatch → reject;
no redirection.

## R-27-02: Cross-instance target leakage
**Likelihood:** Low | **Impact:** High
**Mitigation:** Per-instance request codes; binding map; isolation tests.

## R-27-03: Widget implies unverified capability
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Actions only for verified+authorized+fresh states.

## R-27-04: Refresh staleness misread as live
**Likelihood:** Medium | **Impact:** Medium
**Mitigation:** No real-time claim; updatePeriod=0 documented.

## R-27-05: Launcher differences
**Likelihood:** Medium | **Impact:** Low
**Mitigation:** Framework views only; readable at minimum size; no hacks.
