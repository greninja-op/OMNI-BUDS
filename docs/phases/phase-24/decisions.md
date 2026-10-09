# Phase 24 — Decisions

## D-24-01: per-device aggregators
**Decision:** One `DeviceStateAggregator` (one Mutex) per device, owned by
the repository.
**Rationale:** Isolation by construction; no cross-device locking.

## D-24-02: StateFlow (conflated)
**Decision:** StateFlow for observation APIs.
**Rationale:** Slow collectors get the latest snapshot, never a backlog.
Matches the `DeviceSessionEngine` pattern.

## D-24-03: injected TimeProvider
**Decision:** Wall-clock-free; `NoTimeProvider` default.
**Rationale:** Deterministic tests; matches Phase 21's finding.

## D-24-04: sequences over timestamps for ordering
**Decision:** Monotonic per-device sequences; no cross-clock comparison.
**Rationale:** Source clocks are unrelated; sequences are reliable.

## D-24-05: no silent corrections
**Decision:** The validator reports; it does not rewrite.
**Rationale:** Evidence must never be silently "fixed".

## D-24-06: desired/observed separation
**Decision:** Separate maps; ambiguous ops never overwrite confirmed state.
**Rationale:** Core principle — state is evidence, not invention.
