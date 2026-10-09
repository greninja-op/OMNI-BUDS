# Phase 17 — Risk Register

## R-17-01: Hand-written JSON parser bugs
**Likelihood:** Medium | **Impact:** Medium
Custom parser risks edge cases.
**Mitigation:** Round-trip tests for all 9 value types; malformed input →
null (corruption path), never silent coercion.

## R-17-02: File storage on-device behavior
**Likelihood:** Low | **Impact:** Low
Atomic rename semantics verified by unit tests, not on-device.
**Mitigation:** Standard temp+rename pattern; documented limitation.

## R-17-03: Key collision via sanitization
**Likelihood:** Low | **Impact:** Medium
Two distinct keys could sanitize to the same filename.
**Mitigation:** Identity keys are fingerprint-derived (safe charset);
sanitization is a guard, not the primary mechanism.

## R-17-04: Preference/hardware confusion
**Likelihood:** Low | **Impact:** High
Future code might treat saved prefs as applied state.
**Mitigation:** Scope tests ban claim vocabulary; eligibility gating.
