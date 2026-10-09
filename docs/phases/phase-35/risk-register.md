# Phase 35 — Risk Register

## R-35-01: Dependency vulnerability (unscanned)
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Pinned versions; minimal deps.
**Residual:** Low-Medium. **Verification:** review only.

## R-35-02: Parser bypass in unhardened parsers
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** InputValidator available; typed failures.
**Residual:** Low.

## R-35-03: Log leakage via new code paths
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** Sink redaction; LogRedactor boundary.
**Residual:** Low.

## R-35-04: Physical-device attacks
**Severity:** High | **Likelihood:** Unknown
**Mitigation:** None in this phase — deferred to hardware testing.
**Residual:** High. **Verification:** deferred.

## R-35-05: OEM/component behavior differences
**Severity:** Low | **Likelihood:** Medium
**Mitigation:** Documented in Phase 32.
**Residual:** Low.
