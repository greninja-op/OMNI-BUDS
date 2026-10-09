# Phase 40 — Protocol Evidence Policy

## Rules

- Protocol knowledge (Phase 22) describes protocols; evidence
  supports claims; implementations execute; capabilities are
  registered; user settings stay separate; test results are
  per-environment.
- Evidence levels: INFERRED, IMPLEMENTED, LAB_TESTED,
  HARDWARE_VERIFIED, PERSISTENCE_VERIFIED.
- No automatic promotion from compilation or mock tests.
- Integrations expose match/feature/evidence metadata for
  diagnostics.

## Synthetic evidence

Test adapters and fixtures are labeled synthetic and never count
as protocol evidence.
