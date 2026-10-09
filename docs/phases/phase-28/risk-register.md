# Phase 28 — Risk Register

## R-28-01: Process death during hardware write
**Likelihood:** Low | **Impact:** High
**Mitigation:** MarkInterrupted; never replay; reconcile honestly.

## R-28-02: Reconnect storm
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** Bounded attempts + backoff; cancellation on
adapter-off/permission-revocation.

## R-28-03: Stale session treated as live
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Invalidate by default; live proof required.

## R-28-04: Resource leaks
**Likelihood:** Medium | **Impact:** Medium
**Mitigation:** Ownership registry; release on owner end.

## R-28-05: Background restriction evasion
**Likelihood:** Low | **Impact:** High
**Mitigation:** No polling, no permanent service; restrictions honored.
