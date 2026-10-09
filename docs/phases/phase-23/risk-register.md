# Phase 23 — Risk Register

## R-23-01: Extension metadata mistaken for authorization
**Likelihood:** Medium | **Impact:** High
A registered feature may be read as permission to execute it.
**Mitigation:** No execution APIs in descriptors; access policy injected
separately; tests assert the separation.

## R-23-02: Compatibility false positives
**Likelihood:** Medium | **Impact:** High
A wrong model match could enable wrong-vendor commands.
**Mitigation:** Exact model matching; 10-rule policy; ambiguity preserved;
firmware rules fail closed.

## R-23-03: Dependency bypass
**Likelihood:** Low | **Impact:** Medium
Callers could skip dependency checks.
**Mitigation:** Documented contract; typed results; tests cover the checks.

## R-23-04: Scope creep into control
**Likelihood:** Medium | **Impact:** Medium
The framework could grow transport shortcuts.
**Mitigation:** No transport handles by design; architecture tests;
security tests pin the surface.
