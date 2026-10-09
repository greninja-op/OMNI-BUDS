# Phase 40 — Vendor Onboarding Guide

## Adding a manufacturer

1. **Gather evidence.** Byte-level protocol documentation or
   captured fixtures with a clear license. No evidence → stop
   (Phase 19 precedent).

2. **Define the integration.** Create an adapter implementing
   `VendorAdapter` in `core/vendor/` (or a vendor package):
   - Stable `adapterId` (e.g. `vendor.acme`).
   - `match()` on manufacturer IDs / service fingerprints —
     never names alone.
   - `protocol`: ProtocolDefinition with real command/response
     shapes.
   - `supportedFirmware`: verified versions or null.

3. **Declare the contract.** Build a `VendorIntegrationContract`
   (contractVersion 1, integrationId, productFamilies,
   verifiedFirmware).

4. **Register features.** Use the Phase 23 extension framework:
   namespaced feature IDs, capability states, dependencies.

5. **Wire authorization.** Operations go through DeviceAccessPolicy
   and the Phase 29 operation pipeline. No alternate paths.

6. **Write tests.** Identity, isolation, protocol conformance
   (Phase 37), failure injection. Synthetic fixtures labeled.

7. **Document evidence.** Protocol knowledge records with
   provenance; never promote to HARDWARE_VERIFIED without a
   device.

## What not to do

- Guess packet formats.
- Widen matching rules to "support" more devices.
- Bypass authorization, state reconciliation, or recovery.
- Modify unrelated core components.
