# Phase 40 — Design

## Modules

`core/vendor/` extended in place (layer 5):

- `VendorIntegrationContract.kt` — contract versioning +
  firmware compatibility.
- `VendorResolution.kt` — typed resolution outcomes.
- Existing: VendorAdapter, VendorRegistry, NullVendorAdapter.

## Reuse

Extension framework (Phase 23), capability states (Phase 8),
DeviceAccessPolicy (Phase 35), DeviceFingerprint (Phase 5),
protocol knowledge (Phase 22).

## Dependency direction

vendor → device, protocol, common, state. No UI, no Bluetooth
APIs. Vendor adapters contribute implementations but use shared
safety, state, persistence, and recovery.

## Data flow

fingerprint → VendorResolver → ExactMatch/Ambiguous/Unknown →
adapter operations via centralized pipeline.
