# Phase 13 — Decisions

## D-13-01: Derived negotiation states
**Decision:** Derive `NegotiationState` from transport/codec observations
rather than claiming protocol insight.
**Rationale:** Android exposes no negotiation protocol events. Inventing
them would be fabrication.
**Consequence:** NEGOTIATING means "capabilities observed in flight", not
"protocol handshake seen".

## D-13-02: No debouncing
**Decision:** No debounce timers in the engine.
**Rationale:** Inputs are snapshot-derived, not rapid event bursts; there is
nothing legitimate to debounce (OB-P13-REQ-022).
**Consequence:** Every meaningful change propagates immediately.

## D-13-03: quality at layer 4
**Decision:** New `core.quality` package at layer 4 (with protocol).
**Rationale:** It consumes audio (2) and codec (3); nothing below may depend
on it.
**Consequence:** `DependencyDirectionTest` updated.

## D-13-04: Resolver is pure, engine is stateful
**Decision:** `AudioQualityResolver` is a stateless object; `AudioQualityEngine`
owns ingestion, sessions, events, and Flow.
**Rationale:** Conflict logic stays testable in isolation; lifecycle stays in
one place.

## D-13-05: Synchronous event emission
**Decision:** `tryEmit` into a 64-slot buffer, not `scope.launch { emit }`.
**Rationale:** Deterministic in tests; no orphan coroutines; backpressure via
bounded buffer + timeline.

## D-13-06: Caller-supplied device correlation
**Decision:** The Android bridge takes a `(ObservedAudioDevice) -> DeviceIdentity?`
function; uncorrelated devices keep UNKNOWN codec fields.
**Rationale:** Guessing the correlation would fabricate device identity.

## D-13-07: No quality scores
**Decision:** No scoring, no ranking, no subjective claims (OB-P13-REQ-028).
**Rationale:** Codec identity ≠ quality; scoring needs its own architecture.
