# Phase 45 — Security & Trust Boundaries

## 1. Input Validation and Defense in Depth
Firmware version strings originate from untrusted or semi-trusted peripheral devices over Bluetooth.
- **Length Bounds**: Input strings are strictly truncated / bounded at 128 characters.
- **Character Filtering**: Control characters (ASCII < 32 or 127) are rejected with `REJECTED_BY_POLICY`.
- **Injection Prevention**: Firmware version strings are treated purely as opaque or structured data; they are never passed into shell interpreters or dynamic code evaluators.

## 2. Provenance-Based Authorization Gates
- Only observations from authoritative device queries (`DEVICE_DIS_AUTHORITATIVE`, `VENDOR_TELEMETRY`), verified persistent cache (`PERSISTED_CACHE`), or test fixtures (`TEST_FIXTURE`) with verification level $\ge$ `LAB_TESTED` can authorize mutating operations.
- `USER_MANUAL_ENTRY` and unprobed broadcast inferences cannot authorize mutating operations.

## 3. Scope Boundaries
- This phase deliberately does not implement firmware flashing, firmware downloads, or firmware downgrade routines.
