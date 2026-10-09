# Phase 42 — Design

## Modules

`core/vendor/apple/` (new package, layer 5):

- `AppleAirpodsAdapter` — VendorAdapter implementation.
- `AirpodsIdentityEvidence` — identity data loaded from a JSON
  resource (no code literals).
- `AirpodsCapabilityScope` — read-only observations,
  unsupported features, battery mapping.

## Reuse

Phase 40 contracts, Phase 41 evidence + contract harness,
DeviceFingerprint, BatteryState, VerificationLevel.

## Identity flow

fingerprint → company ID 0x004C? → audio class-of-device?
→ Matched(family) / Ambiguous / NotMatched.

No display-name use, no undocumented payload decoding.

## Test strategy

Identity tests (family/ambiguous/not-matched/deterministic),
capability-scope tests, battery tests, contract-harness
subclass. Full regression.
