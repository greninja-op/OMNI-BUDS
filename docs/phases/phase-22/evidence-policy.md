# Phase 22 — Evidence Policy

## Verification statuses

Reuse `VerificationLevel`: INFERRED, IMPLEMENTED, LAB_TESTED,
HARDWARE_VERIFIED, PERSISTENCE_VERIFIED.

### INFERRED
A hypothesis or interpretation exists with recorded supporting evidence.
Not yet established.

### IMPLEMENTED
The relevant implementation exists; behavior and limitations documented.

### LAB_TESTED
Deterministic or controlled offline tests passed, with identifiable
fixtures, schemas, and implementation versions.

### HARDWARE_VERIFIED
Real-device evidence from an authorized verification process.
Synthetic fixtures, code inspection, and documentation alone cannot
establish this.

### PERSISTENCE_VERIFIED
Configuration observed to survive the lifecycle boundary and read back
from the device. Local database persistence cannot establish this.

## Evidence rules

- Preserve contradictory evidence; never average into false consensus.
- Do not auto-select the newest claim among incompatible sources.
- Do not elevate a claim because derivative sources copied one original.
- Record applicable firmware and protocol versions.
- Source trust and claim confidence are distinct.
- Observed values and semantic interpretations are distinct.
- Verification transitions are auditable (evidence IDs on every transition).

## Evidence evaluation

Deterministic and testable: the `EvidenceRecord` init block enforces the
synthetic-fixture ceiling; `KnowledgeLifecycleRules.checkActivatable`
requires evidence for accepted claims.
