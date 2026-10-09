# Phase 41 — Future Vendor Onboarding

## Workflow

1. Research: gather licensed, inspectable protocol evidence.
   Classify the candidate (READY / PARTIALLY_READY /
   RESEARCH_REQUIRED / INSUFFICIENT_EVIDENCE / DEFERRED).
2. If INSUFFICIENT_EVIDENCE: document and stop. Do not invent.
3. Implement the adapter using `VendorAdapter` + Phase 40
   contracts; register `VendorEvidence` records.
4. Extend `VendorAdapterContractTest` for the new adapter.
5. Register features via the Phase 23 extension framework with
   evidence provenance.
6. Wire operations through DeviceAccessPolicy and the Phase 29
   pipeline.
7. Add identity fixtures (ambiguous names, missing metadata,
   firmware variants).
8. Update the candidate, evidence, device, and feature matrices.
9. Run the full regression suite.

## Rules

- Evidence before implementation.
- Synthetic stays synthetic.
- No authorization bypasses.
- Hardware verification stays deferred to Phase 52.
