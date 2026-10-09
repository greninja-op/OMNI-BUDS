# Phase 41 — Vendor Candidate Matrix

## Assessment criteria

Evidence quality, accessible protocol documentation, licensing,
hardware availability, testability. No numerical scores invented.

| Candidate | Product families | Protocol evidence | License | Hardware | Classification |
|---|---|---|---|---|---|
| Sony | WF/WF-1000X series | None in repo; no accessible spec | — | None | INSUFFICIENT_EVIDENCE |
| Bose | QC35 / QC Ultra 2 (BMAP) | Third-party reverse-engineering (bosectl); byte-level specs inaccessible | MIT (tool) | None | RESEARCH_REQUIRED |
| JBL | Various | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| Samsung | Galaxy Buds series | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| Sennheiser | Momentum series | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| Anker Soundcore | Various | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| Nothing | Ear series | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| OnePlus | Buds series | None in repo | — | None | INSUFFICIENT_EVIDENCE |
| Google | Pixel Buds | None in repo | — | None | INSUFFICIENT_EVIDENCE |

## Decision

No candidate is READY_FOR_IMPLEMENTATION. Bose BMAP is the only
RESEARCH_REQUIRED candidate; all others are INSUFFICIENT_EVIDENCE
or DEFERRED. No integration will be implemented in Phase 41.

## What Phase 41 delivers instead

- Common vendor-adapter contract test harness.
- Compatibility metadata improvements.
- Fixture coverage for identity scenarios.
- Evidence register and supported-device/feature matrices
  (documenting the empty supported set honestly).
