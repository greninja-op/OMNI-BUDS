# Phase 40 — Specifications

## VendorIntegrationContract

- CURRENT_CONTRACT_VERSION = 1.
- Fields: contractVersion, integrationId, productFamilies,
  verifiedFirmware?.
- init rejects contractVersion < 1 and blank integrationId.

## FirmwareCompatibilityEvaluator

- evaluate(contract, firmwareVersion?): VERIFIED /
  INCOMPATIBLE / UNKNOWN_FIRMWARE.

## VendorResolution

- Sealed: ExactMatch, FamilyMatch, Ambiguous, KnownUnsupported,
  UnknownDevice, IncompatibleVersion.

## VendorResolver

- resolve(fingerprint): Ambiguous when registry.isAmbiguous;
  ExactMatch when registry.resolve returns an adapter;
  UnknownDevice otherwise.
