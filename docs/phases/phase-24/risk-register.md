# Phase 24 — Risk Register

## R-24-01: Stale state acted upon
**Likelihood:** Medium | **Impact:** High
A consumer treats a stale observation as current.
**Mitigation:** Per-observation freshness; `isUsable`; validator flags
stale-as-current.

## R-24-02: Cross-device contamination
**Likelihood:** Low | **Impact:** High
**Mitigation:** Per-device aggregators; device-id check on ingest;
multi-device tests.

## R-24-03: Desired/observed conflation
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Separate maps; no auto-write; separation tests.

## R-24-04: Event-ordering inversion
**Likelihood:** Medium | **Impact:** Medium
**Mitigation:** Sequences; stale rejection; old-session rejection.

## R-24-05: Unbounded growth
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** Bounded diagnostics (100); conflated StateFlow; no
event log retained.
