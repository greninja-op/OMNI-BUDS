# Phase 42 — Specifications

## AppleAirpodsAdapter

- adapterId: `vendor.apple.airpods`.
- match: Apple company ID (0x004C) + audio CoD
  (major class 0x2000) → Matched(family). Company ID without
  audio class → Ambiguous. Otherwise → NotMatched.
  Unobserved → NotMatched.
- protocol: metadata-only definition, BLUETOOTH_CLASSIC,
  no commands, confidence INFERRED (documentation-derived).
- supportedFirmware: null (firmware-independent).

## AirpodsCapabilityScope

- ReadOnlyObservation: 6 Android-exposed observations.
- UnsupportedFeature: 14 features with reasons.
- batteryFromAndroidAggregate: no provenance → all-null
  BatteryState; null → all-null.

## Identity evidence data

`AirpodsIdentityEvidence` loads Bluetooth SIG assigned numbers
from `core/src/main/resources/omnibuds/vendor/apple/airpods-identity.json`
as data (ADR-P0-003): company ID 76 (0x004C), CoD major-class mask
7936 (0x1F00), audio/video value 1024. No hex literals in code.
