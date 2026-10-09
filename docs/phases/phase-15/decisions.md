# Phase 15 — Decisions

## D-15-01: reuse, don't duplicate
**Decision:** Reuse `FeatureCapability`, `FeatureState`, `ConfigurationValue`,
`VerificationLevel`, `CoreFeature`; Phase 15 adds only the domain dimension.
**Rationale:** The audit found the initial draft duplicated existing
architecture. Annotation > replacement.

## D-15-02: processing at layer 5
**Decision:** `core.processing` at layer 5, importing only from layers 0–4.
**Rationale:** Never sideways-import `feature` (5).

## D-15-03: UNKNOWN by default
**Decision:** Hardware domain requires verified protocol + IMPLEMENTED+.
**Rationale:** Domain fabrication is the phase's dominant risk.

## D-15-04: no substitution
**Decision:** Cross-domain control requests are denied with reasons.
**Rationale:** Silent rerouting would fabricate capability.
