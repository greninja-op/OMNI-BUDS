# Phase 31 — Unsupported Device Reporting

## Policy

- Profiles with unsupported capabilities → NOT_APPLICABLE for those
  tests; verdict reflects the gap.
- Unknown firmware → no assumptions; UNKNOWN or BLOCKED.
- Out-of-scope firmware/protocol → INCOMPATIBLE or BLOCKED per evidence.
- Invalid profiles → rejected, never treated as compatible.
- Reports list unsupported profiles with reasons and evidence gaps.

## No silent compatibility

A profile is never "compatible" by default. Compatibility is earned
per scope, per evidence.
