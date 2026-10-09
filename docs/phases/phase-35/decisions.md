# Phase 35 — Decisions

## D-35-01: reuse existing authorization
**Decision:** No new policy engine; DeviceAccessPolicy stays canonical.
**Rationale:** One authorization model.

## D-35-02: generous-but-bounded limits
**Decision:** 64 KiB / 1024 / 16 / 16 MiB.
**Rationale:** Above legitimate traffic, below resource risk.

## D-35-03: redaction at two layers
**Decision:** Pure LogRedactor + existing sink-side redaction.
**Rationale:** Defense in depth; testable boundary.

## D-35-04: no new encryption
**Decision:** No custom crypto; no Keystore (no secret material).
**Rationale:** The data model doesn't justify it.

## D-35-05: honest scanner gap
**Decision:** Report dependency review as review-only.
**Rationale:** No scanner available; never claim a clean scan.
