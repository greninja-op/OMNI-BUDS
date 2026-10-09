# Phase 20 — Risk Register

## R-20-01: Analysis mistaken for validation
**Likelihood:** Medium | **Impact:** High
A passing parser test may be read as protocol correctness.
**Mitigation:** Docs and workflow explicitly separate implementation
testing from protocol validation.

## R-20-02: Hypothesis creep
**Likelihood:** Medium | **Impact:** Medium
Engineers may treat hypotheses as confirmed.
**Mitigation:** Hypothesis status tracked separately; never auto-promoted.

## R-20-03: Malicious trace files
**Likelihood:** Low | **Impact:** Medium
Crafted inputs could exploit parser bugs.
**Mitigation:** Bounded inputs, typed outcomes, no script execution,
no network, resource limits.

## R-20-04: Redaction failures
**Likelihood:** Low | **Impact:** High
Sensitive data could leak through insufficient redaction.
**Mitigation:** Typed redaction metadata; structural changes recorded;
tests for leakage.
