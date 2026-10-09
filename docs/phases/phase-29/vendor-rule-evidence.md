# Phase 29 — Vendor Rule Evidence

## Policy

- Vendor-declared relationships register with their trust level
  preserved (from the Phase 23 extension framework).
- A vendor declaration starts at IMPLEMENTED at most; it becomes
  HARDWARE_VERIFIED only through hardware evidence.
- Rules cite evidence/claim IDs from the Phase 22 knowledge database.
- Inferred vendor behavior is advisory until verified.

## Evidence quality

Contradictory evidence is preserved, not discarded. If trust cannot be
established, the evaluator returns Unresolved instead of guessing.

## Scope

Vendor rules are scoped to manufacturer/model/firmware and never leak
across device identities.
