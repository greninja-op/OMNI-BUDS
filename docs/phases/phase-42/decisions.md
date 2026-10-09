# Phase 42 — Decisions

## D-42-01: no AAP implementation
**Decision:** Apple-proprietary protocol not implemented.
**Rationale:** Requires device-identity spoofing + root
(access-control bypass); sources GPL-encumbered;
firmware-fragile.

## D-42-02: family-level identity only
**Decision:** Company ID + audio class → family match; no exact
model resolution.
**Rationale:** Standard Bluetooth evidence only; no undocumented
payload decoding.

## D-42-03: read-only adapter
**Decision:** No write operations; all Apple-specific controls
UNSUPPORTED.
**Rationale:** No legitimate Android-accessible control path.

## D-42-04: battery honesty
**Decision:** Unattributable aggregate values stay null.
**Rationale:** Recording a single value against one earbud
would fabricate per-bud data.
