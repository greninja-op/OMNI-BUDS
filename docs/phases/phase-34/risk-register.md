# Phase 34 — Risk Register

## R-34-01: Blind replay of hardware writes
**Severity:** High | **Likelihood:** Low
**Mitigation:** mayHaveExecuted → RECONCILE; NEVER_RETRY classes.
**Residual:** Low.

## R-34-02: Unbounded retries
**Severity:** High | **Likelihood:** Low
**Mitigation:** Explicit budgets; exhaustion → user intervention.
**Residual:** Low.

## R-34-03: Stale session mutation
**Severity:** High | **Likelihood:** Low
**Mitigation:** Supersession → abort; guarded transitions.
**Residual:** Low.

## R-34-04: Diagnostic data leakage
**Severity:** Medium | **Likelihood:** Low
**Mitigation:** No payload/secret fields; bounded sink.
**Residual:** Low.

## R-34-05: Recovery bypassing authorization
**Severity:** High | **Likelihood:** Low
**Mitigation:** authorizationValid gate; decisions are advisory.
**Residual:** Low.
