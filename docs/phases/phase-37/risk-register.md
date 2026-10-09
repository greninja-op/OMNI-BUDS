# Phase 37 — Risk Register

## R-37-01: Fixture mislabeled as hardware evidence
**Severity:** High | **Likelihood:** Low
**Mitigation:** FixtureOrigin required; evidence policy forbids
promotion.
**Residual:** Low.

## R-37-02: Runner becomes an attack surface
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Declarative cases; validated input; no code loading.
**Residual:** Low.

## R-37-03: Test pollution between cases
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Sequential execution; stateless parsers; exception
isolation.
**Residual:** Low.

## R-37-04: Campaign scope overstatement
**Severity:** Low | **Likelihood:** Low
**Mitigation:** No "full coverage" labels; documented scope.
**Residual:** Low.
