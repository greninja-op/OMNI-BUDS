# Phase 14 — Risk Register

## R-14-01: VALID misread as signal proof
**Likelihood:** Medium | **Impact:** Medium
Consumers might interpret VALID as "audio is playing".
**Mitigation:** Documented semantics; scope test bans signal vocabulary.

## R-14-02: Generation map growth
**Likelihood:** Low | **Impact:** Low
`generations` retains one entry per device ever seen.
**Mitigation:** Bounded by paired-device count; documented.

## R-14-03: Rule coverage gaps
**Likelihood:** Medium | **Impact:** Low
10 rules cover the 7 categories; exotic transports may lack rules.
**Mitigation:** Unknown → INCONCLUSIVE, never false VALID.

## R-14-04: No Android validation adapter
**Likelihood:** Low | **Impact:** Low
Phase 14 adds no Android code; validation runs on domain observations.
**Mitigation:** The Phase 13 bridge is the tested integration point.
