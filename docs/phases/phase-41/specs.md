# Phase 41 — Specifications

## VendorEvidence

- Fields: integrationId, source, scope, level, reviewedDate?.
- init rejects blank fields; HARDWARE_VERIFIED/PERSISTENCE_VERIFIED
  require a review date.

## VendorEvidenceAssessor

- effectiveLevel: max level, or INFERRED when empty.
- isHardwareVerified: dated HARDWARE_VERIFIED record exists.

## VendorAdapterContractTest (test harness)

- Abstract: adapter(), evidence().
- Tests: stable ID, unobserved never matches, deterministic
  matching, stable protocol ID, evidence-level consistency.
