# Phase 32 — Risk Register

## R-32-01: OEM behavior differences
**Severity:** High | **Likelihood:** High
**Mitigation:** Documented as UNVERIFIED; hardware phase later.
**Residual:** High — real devices untested.

## R-32-02: Emulator gaps
**Severity:** Medium | **Likelihood:** Certain
**Mitigation:** JVM tests cover decision logic; gaps marked NOT_RUN.
**Residual:** Medium.

## R-32-03: Permission model changes
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** ApiLevelPolicy is centralized and tested.
**Residual:** Low.

## R-32-04: False compatibility claims
**Severity:** High | **Likelihood:** Low
**Mitigation:** Evidence mapping; claim discipline in docs.
**Residual:** Low.
