# Phase 15 — Risk Register

## R-15-01: Domain fabrication
**Likelihood:** Medium | **Impact:** High
Claiming DEVICE_HARDWARE_DSP without evidence.
**Mitigation:** Resolver defaults UNKNOWN; hardware needs protocol +
verification.

## R-15-02: Empty protocol registry
**Likelihood:** Certain | **Impact:** Low
All hardware writes NOT_CONFIGURABLE today.
**Mitigation:** Correct state, not a gap to bridge.

## R-15-03: Android↔hardware substitution
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** Control boundary denies cross-domain routing.
