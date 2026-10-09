# Phase 20 — Decisions

## D-20-01: passive analysis only
**Decision:** Lab has no transport handles or write APIs.
**Rationale:** A parsed message must never become a command.

## D-20-02: unknown stays unknown
**Decision:** `semanticMeaning` null for uninterpreted data; hypotheses
human-recorded.
**Rationale:** Auto-inference from byte differences is fabrication.

## D-20-03: synthetic ≠ captured
**Decision:** Distinct source types; mislabeling is a test failure.
**Rationale:** Provenance is the foundation of evidence.

## D-20-04: evidence gates promotion
**Decision:** Workflow enforces per-transition evidence; synthetic can never
reach hardware statuses.
**Rationale:** The lab prepares evidence; it doesn't award it.

## D-20-05: lab at layer 5, minimal imports
**Decision:** Only common (0) and state (0) imported.
**Rationale:** Passive analysis needs nothing else.
