# Phase 36 — Risk Register

## R-36-01: Free-text identifier leakage
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Sink redaction + "do not put it in" contract;
  documented as a known gap.
**Residual:** Low-Medium.

## R-36-02: Diagnostic volume during failure storms
**Severity:** Medium | **Likelihood:** Medium
**Mitigation:** Bounded store; dropped counters; no recursion.
**Residual:** Low.

## R-36-03: Export misinterpreted as hardware proof
**Severity:** Low | **Likelihood:** Low
**Mitigation:** Export carries evidence levels, not claims.
**Residual:** Low.

## R-36-04: No disk persistence
**Severity:** Low | **Likelihood:** Certain
**Mitigation:** Documented; in-memory only by design.
**Residual:** Low.
