# Phase 40 — Decisions

## D-40-01: extend vendor in place
**Decision:** New files in `core/vendor/`; no new modules.
**Rationale:** No dependency-boundary justification for modules.

## D-40-02: contract versioning
**Decision:** Explicit contractVersion with rejection of unknown
versions.
**Rationale:** Compatibility changes must be explicit.

## D-40-03: typed resolution outcomes
**Decision:** VendorResolution sealed interface with reasons.
**Rationale:** Diagnostics can explain matching.

## D-40-04: synthetic test adapters
**Decision:** Multi-vendor isolation proven with clearly synthetic
adapters.
**Rationale:** No real vendor evidence exists to build on.

## D-40-05: no registry behavior change
**Decision:** VendorRegistry untouched.
**Rationale:** Its semantics are already correct and tested.
