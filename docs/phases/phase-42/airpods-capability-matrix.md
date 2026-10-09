# Phase 42 — AirPods Capability Matrix

**Adapter:** `vendor.apple.airpods` (family-level, read-only).

## Read-only observations

| Capability | Model scope | Evidence | State | Verification |
|---|---|---|---|---|
| connection-state | AirPods family | Android Bluetooth API | READ_ONLY | INFERRED |
| device-name | AirPods family | Android Bluetooth API | READ_ONLY | INFERRED |
| bond-state | AirPods family | Android Bluetooth API | READ_ONLY | INFERRED |
| audio-profiles | AirPods family | Android Bluetooth API | READ_ONLY | INFERRED |
| battery-level | AirPods family | Android battery extra; often absent | READ_ONLY (nullable) | INFERRED |
| codec-info | AirPods family | Android API; often UNKNOWN | READ_ONLY | INFERRED |

## Unsupported controls

Noise-control set, adaptive audio, spatial audio, head tracking,
ear-detection control, gesture customization, firmware update,
Find My, Siri, auto-switching, per-bud battery, case battery,
multipoint, conversation awareness — all UNSUPPORTED. Reasons in
`AirpodsCapabilityScope.UnsupportedFeature`.

## Notes

- No capability is SUPPORTED_VOLATILE/PERSISTENT: there are no
  write operations.
- Exact model (generation/Pro/Max) is not resolved; family-level
  identity only.
- Firmware scope: unknown; no firmware-dependent behavior claimed.
