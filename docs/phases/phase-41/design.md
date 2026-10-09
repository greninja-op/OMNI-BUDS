# Phase 41 — Design

## Modules

`core/vendor/` extended (layer 5):

- `VendorEvidence.kt` — evidence records + assessor.
- Test: `VendorAdapterContractTest` harness + null-adapter
  application; `VendorEvidenceTest`.

## Reuse

Phase 40 contracts, VendorRegistry, VerificationLevel (Phase 7),
DeviceAccessPolicy (Phase 35).

## Data flow

integration → VendorEvidence records → effective level →
capability/authorization decisions. No evidence → INFERRED.

## Test strategy

Shared abstract contract tests run against every registered
adapter. Future integrations subclass the harness.
