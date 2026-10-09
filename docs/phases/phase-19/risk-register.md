# Phase 19 — Risk Register

## R-19-01: Second-hand protocol evidence
**Likelihood:** High | **Impact:** High
Reverse-engineered protocols may contain errors.
**Mitigation:** No implementation from such sources without hardware
validation. Evidence ledger tracks confidence.

## R-19-02: Future adapter may over-claim
**Likelihood:** Medium | **Impact:** High
An adapter with a method name is not proof of hardware support.
**Mitigation:** Capability declarations require evidence references;
verification statuses never upgraded without evidence.

## R-19-03: Fingerprint collisions
**Likelihood:** Low | **Impact:** Medium
Broad rules could match unrelated models.
**Mitigation:** Ambiguous → null; exact-match required for writes.

## R-19-04: License violations
**Likelihood:** Low | **Impact:** High
Protocol docs may have redistribution restrictions.
**Mitigation:** Nothing copied in Phase 19; licenses verified before use.
