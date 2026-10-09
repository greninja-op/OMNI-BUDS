# Phase 14 — Specifications

## ValidationStatus

VALID, INVALID, INCONCLUSIVE, NOT_OBSERVABLE, STALE, CONFLICT, UNSUPPORTED.

Semantics:
- INCONCLUSIVE ≠ INVALID (missing evidence is not failure).
- NOT_OBSERVABLE ≠ UNSUPPORTED (platform limit is not device limit).
- VALID ≠ audible output proven.

## ValidationSeverity

INFO < WARNING < ERROR < CRITICAL. Orthogonal to status.

Policy:
- Missing optional info → INFO, never ERROR.
- Warning → never invalidates.
- Device-association violation → ERROR/CRITICAL.

## Aggregation policy

1. INVALID (ERROR/CRITICAL) → INVALID.
2. CONFLICT → CONFLICT.
3. STALE → STALE.
4. INVALID (WARNING) → INCONCLUSIVE.
5. All VALID → VALID.
6. VALID + unknown → INCONCLUSIVE.

## Session generations

- Per-device monotonic counter, starts at 0.
- `onDisconnected` bumps the generation.
- `validate(expectedGeneration)` discards mismatches (returns null).

## Snapshot contents

ID, device, generation, timestamp, transport, route, codec, negotiation,
freshness summary, results, overall status, unevaluated rules, evidence,
limitations.
