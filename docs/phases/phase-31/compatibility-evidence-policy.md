# Phase 31 — Compatibility Evidence Policy

## Rules

- Synthetic passes → SYNTHETIC_ONLY at best.
- Fixture-derived → SIMULATED evidence.
- Lab-tested → DOCUMENTED evidence.
- Hardware-verified → HARDWARE_OBSERVED, scoped to exact
  model/firmware/protocol tested.
- Never infer vendor-wide or firmware-wide compatibility.
- Contradictory evidence recorded, not discarded.
- Missing evidence → UNKNOWN or BLOCKED, never compatible.

## Scope discipline

Every verdict carries its scope string. Claims outside the tested
scope are invalid.
