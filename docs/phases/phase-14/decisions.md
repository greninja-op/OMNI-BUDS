# Phase 14 — Decisions

## D-14-01: validation at layer 5
**Decision:** New `core.validation` package at layer 5 (above quality at 4).
**Rationale:** It consumes quality (4); sideways dependencies are rejected
by `DependencyDirectionTest`.
**Consequence:** Layer map updated.

## D-14-02: pure rules, stateful engine
**Decision:** `ValidationRule` is a pure interface; `AudioPathValidationEngine`
owns generations, snapshots, and Flow.
**Rationale:** Rules testable in isolation; lifecycle in one place.

## D-14-03: session generations
**Decision:** Per-device monotonic generation, bumped on disconnect;
obsolete inputs discarded (return null).
**Rationale:** Late events from dead sessions must not restore state.

## D-14-04: content-based dedup
**Decision:** Snapshots compared by content (excluding ID); identical
observations don't re-emit.
**Rationale:** No duplicate emissions for unchanged state.

## D-14-05: no new Android code
**Decision:** Phase 14 is pure domain; Android integration reuses the Phase
13 bridge.
**Rationale:** The validation engine consumes the quality engine, which the
bridge already feeds. No duplication.

## D-14-06: signal-path honesty
**Decision:** VALID = "observations consistent", never "sound confirmed".
**Rationale:** The platform offers no measurement mechanism; claiming more
would be fabrication.
