# Phase 38 — Decisions

## D-38-01: new hil package, no competing framework
**Decision:** New `core/hil/` at layer 5; extends Phase 30–37 models.
**Rationale:** HIL-specific abstractions missing; no duplication.

## D-38-02: safety in code
**Decision:** Defaults and invariants enforced in data-class init
and the gate, not just docs.
**Rationale:** Documentation can't deny execution.

## D-38-03: physical checks deferred, not failed
**Decision:** DEFERRED_TO_FINAL_HARDWARE_VERIFICATION status.
**Rationale:** A deferred check is information, not a failure.

## D-38-04: no Bluetooth imports
**Decision:** No android.bluetooth imports in core/hil.
**Rationale:** The phase must not be able to touch hardware.

## D-38-05: profile unknowns are nulls
**Decision:** Unavailable fields are explicit nulls.
**Rationale:** Invented metadata is worse than missing metadata.
