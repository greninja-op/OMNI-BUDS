# Phase 25 — Decisions

## D-25-01: thin TileService
**Decision:** All logic in testable classes; the service is a shell.
**Rationale:** TileService lifecycle is framework-driven and hard to unit
test; logic must not live there.

## D-25-02: tile package in platform/android
**Decision:** Tile code lives in the Android module, not core.
**Rationale:** It needs Android framework types; core stays pure Kotlin.

## D-25-03: injected seams for dispatch
**Decision:** Access check and write executor are injected lambdas.
**Rationale:** Unit-testable without the feature engine; real wiring at
the host app layer.

## D-25-04: no blind toggles
**Decision:** Next mode computed from observed mode + real mode set.
**Rationale:** A click must not invert a local boolean.

## D-25-05: explicit target resolution
**Decision:** Ambiguous → refuse; never first-match.
**Rationale:** Matches the project's ambiguity discipline.

## D-25-06: manifest test earns the service
**Decision:** `platformManifestDeclaresNothingUnjustified` allows exactly
the TileService with Phase 25 justification.
**Rationale:** The existing machine-check for fabricated capabilities.
