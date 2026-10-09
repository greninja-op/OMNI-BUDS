# Phase 18 — Decisions

## D-18-01: event-driven, not port-driving
**Decision:** Framework consumes events; caller drives `FeatureProtocolPort`.
**Rationale:** Avoids sideways `feature` imports; keeps framework pure/testable.

## D-18-02: stages separate from outcomes
**Decision:** 12-stage enum + 7-outcome enum.
**Rationale:** Progress ≠ result; terminal outcomes immutable.

## D-18-03: timeout is ambiguous
**Decision:** Timeout → INCONCLUSIVE, never FAILED.
**Rationale:** A timeout proves nothing about the device.

## D-18-04: move ConfigurationValueJson to core.config
**Decision:** Codec lives with the type it serializes (layer 2).
**Rationale:** Fixes sideways import; both layer-5 packages use it downward.

## D-18-05: no confidence scores
**Decision:** Deterministic scope evaluation, no numerics.
**Rationale:** Explicit evidence semantics beat opaque scores.

## D-18-06: local storage seam
**Decision:** `VerificationStorage` interface mirrors Phase 17's contract.
**Rationale:** Respects layer boundaries; trivial adapter bridges them.
