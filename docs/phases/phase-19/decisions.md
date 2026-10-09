# Phase 19 — Decisions

## D-19-01: no vendor adapter implemented
**Decision:** Build infrastructure only; implement zero vendor commands.
**Rationale:** No candidate has sufficient accessible, verifiable,
license-clear byte-level protocol evidence. The phase explicitly permits
stopping and reporting the blocker.

## D-19-02: Bose BMAP as leading future candidate
**Decision:** Documented as strongest future candidate (MIT, fixtures, docs).
**Rationale:** Best evidence quality among candidates; still insufficient
without accessible spec and hardware validation.

## D-19-03: NullVendorAdapter as honest default
**Decision:** A concrete adapter that matches nothing.
**Rationale:** Gives the registry and matching logic something real to
test against without claiming any device support.

## D-19-04: ambiguous/conflicting matches resolve to null
**Decision:** Any ambiguity or conflict → no adapter (safe fallback).
**Rationale:** Writes must never be enabled on uncertain identity.

## D-19-05: vendor at layer 5
**Decision:** `core.vendor` consumes device (2), protocol (4), common (0).
**Rationale:** Same layer as feature; no sideways imports.
