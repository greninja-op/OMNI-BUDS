# Phase 16 — Risk Register

## R-16-01: No platform battery API
**Likelihood:** Certain | **Impact:** Medium
Android exposes no public Bluetooth battery API; the adapter is
honestly inert until one lands.
**Mitigation:** Explicit UNSUPPORTED; API-level guard as the enablement point.

## R-16-02: Empty protocol registry
**Likelihood:** Certain | **Impact:** Low
Vendor battery arrives only via verified protocols; none exist yet.
**Mitigation:** `BatteryReportingSupport` contract ready; no invented commands.

## R-16-03: Stale data mistaken for current
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** Freshness policy, disconnect invalidation, session generations.

## R-16-04: Inference creep
**Likelihood:** Low | **Impact:** High
Future contributors might derive charging from percentage.
**Mitigation:** Scope tests ban inference vocabulary; negative tests per rule.
