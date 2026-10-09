# Phase 30 — Decisions

## D-30-01: testkit package
**Decision:** New `core/testkit/` for reusable infrastructure.
**Rationale:** Single home for fixtures, doubles, evidence.

## D-30-02: script against the real contract
**Decision:** ScriptedTransport implements TransportContract.
**Rationale:** Tests exercise the real interface, not a parallel one.

## D-30-03: fail closed on exhausted scripts
**Decision:** TIMEOUT, never an invented response.
**Rationale:** A test must not pass on fabricated data.

## D-30-04: seeded injection only
**Decision:** Deterministic pseudo-random; seeds recorded.
**Rationale:** Repeatable failure scenarios.

## D-30-05: evidence honesty
**Decision:** SIMULATED/DOCUMENTED/HARDWARE_OBSERVED levels; simulated
passes never upgrade capability verification.
**Rationale:** Prevents overstating real-device compatibility.
