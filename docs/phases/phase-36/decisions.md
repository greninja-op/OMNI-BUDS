# Phase 36 — Decisions

## D-36-01: reuse the event model
**Decision:** No new event schema; DiagnosticEvent is the model.
**Rationale:** One schema; the privacy design is already sound.

## D-36-02: in-memory store only
**Decision:** No disk persistence layer this phase.
**Rationale:** No documented need; capacity bounding is the
retention mechanism.

## D-36-03: truncate, don't drop
**Decision:** Oversized messages truncated with a marker.
**Rationale:** The event's metadata still has diagnostic value.

## D-36-04: health is passive
**Decision:** Health snapshots never generate events.
**Rationale:** Prevents recursive logging loops.

## D-36-05: export produces content, not files
**Decision:** The exporter returns a string; callers own file I/O.
**Rationale:** File handling is platform-specific.
