# Phase 23 — Decisions

## D-23-01: optional capability interfaces
**Decision:** `VendorFeatureExtension` is minimal (descriptor + resolve);
reader/writer are separate optional interfaces.
**Rationale:** Not every vendor supports every operation; forcing a
mega-interface invites stub implementations that lie.

## D-23-02: no sideways imports
**Decision:** `extension` imports only common(0) and state(0). Access-policy
integration is by injection, not import.
**Rationale:** `access` is layer 5; importing it would be sideways. The
established pattern is caller-side wiring.

## D-23-03: ambiguity as a result
**Decision:** Resolution returns all candidates; ambiguity is an explicit
result type, not an exception.
**Rationale:** Matches Phase 21's ambiguity discipline.

## D-23-04: unknown outcome as a result
**Decision:** Post-submission disconnect/timeout → `Unknown`, not assumed
success/failure.
**Rationale:** Honest uncertainty; no auto-retry of non-idempotent ops.

## D-23-05: no transport in the framework
**Decision:** The framework defines contracts; verified adapters execute.
**Rationale:** Keeps the safety boundary intact.
