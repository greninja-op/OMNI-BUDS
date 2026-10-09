# Phase 18 — Risk Register

## R-18-01: Event/caller split confusion
**Likelihood:** Medium | **Impact:** Medium
Callers might misuse events (e.g., manufacture lifecycle boundaries).
**Mitigation:** Documented contract; scope tests; lifecycle events marked
"observed only."

## R-18-02: Evidence history bounded
**Likelihood:** Low | **Impact:** Low
Full evidence not persisted, only counts.
**Mitigation:** Documented; in-memory records retain full history.

## R-18-03: Comparison is exact-only
**Likelihood:** Low | **Impact:** Low
No feature-specific normalization yet.
**Mitigation:** Extension point documented; exact match is the safe default.

## R-18-04: Recovery re-derives plan
**Likelihood:** Low | **Impact:** Low
Codec stores minimal plan; full plan re-derived on recovery.
**Mitigation:** Documented; requested scope preserved.
