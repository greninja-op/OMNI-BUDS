# Phase 41 — Decisions

## D-41-01: no new vendor integration
**Decision:** No adapter implemented; all candidates below the
evidence threshold.
**Rationale:** The phase explicitly permits framework improvement
instead of invented protocols.

## D-41-02: evidence records
**Decision:** VendorEvidence ties claims to sources with levels.
**Rationale:** Claims without evidence stay claims.

## D-41-03: shared contract harness
**Decision:** Abstract VendorAdapterContractTest for all future
integrations.
**Rationale:** Every integration gets the same safety checks.

## D-41-04: honest empty matrices
**Decision:** Device/feature matrices document the empty
supported set.
**Rationale:** An empty matrix is honest; a speculative one is not.
