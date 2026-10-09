# Phase 40 — Vendor Integration Contract

## Contract

`VendorIntegrationContract`:

- contractVersion (current: 1).
- integrationId (stable, e.g. `vendor.acme`).
- productFamilies (supported families).
- verifiedFirmware? (null = firmware-agnostic).

## Versioning rules

- Contract changes bump CURRENT_CONTRACT_VERSION.
- Integrations declare the version they implement.
- Unknown versions are rejected, never silently interpreted.
- App integration version ≠ device firmware version.

## Firmware compatibility

`FirmwareCompatibilityEvaluator.evaluate`:

- VERIFIED: firmware in verifiedFirmware.
- INCOMPATIBLE: firmware listed scope but not in it.
- UNKNOWN_FIRMWARE: firmware-agnostic integration or missing
  version. Never invents support.
