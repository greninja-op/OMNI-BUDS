# Phase 25 — Risk Register

## R-25-01: Tile implies capability that doesn't exist
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Clickable only when verified + supported + authorized;
honest UNKNOWN states.

## R-25-02: Command to wrong device
**Likelihood:** Low | **Impact:** High
**Mitigation:** Target bound at dispatch; ambiguous → refuse; no
redirection.

## R-25-03: Stale state acted upon
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Freshness checks; unknown/stale → refuse.

## R-25-04: Access-policy bypass via tile
**Likelihood:** Low | **Impact:** High
**Mitigation:** Policy checked at every dispatch; tested.

## R-25-05: Tile update spam / battery drain
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** Conflated StateFlow; updates only while listening.
