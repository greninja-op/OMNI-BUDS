# Phase 27 — Decisions

## D-27-01: updatePeriodMillis = 0
**Decision:** No platform polling; app-initiated refreshes only.
**Rationale:** Battery cost of polling unjustified; engine state drives
updates while the process is alive.

## D-27-02: two layouts
**Decision:** Compact (status) + standard (status + battery + 2 actions).
**Rationale:** Covers supported sizes without decorative complexity.

## D-27-03: provider is also the action receiver
**Decision:** Actions go to `OmniBudsWidgetProvider.onReceive` via
`getBroadcast`.
**Rationale:** Standard widget pattern; exported=false; validated as
untrusted input.

## D-27-04: per-instance binding
**Decision:** Coordinator binds widgetId → deviceId; dispatcher rejects
mismatches without redirection.
**Rationale:** Prevents cross-instance target leakage.

## D-27-05: aapt2-verified resources
**Decision:** Layouts compiled with aapt2; R.java generated for the
sandbox build.
**Rationale:** Honest resource pipeline; XML is the source of truth.
