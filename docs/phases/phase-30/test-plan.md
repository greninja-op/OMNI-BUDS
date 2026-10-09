# Phase 30 — Test Plan

## Unit (`TestkitTests`, 29 tests)
Contract (3): valid case, blank id, bad timeout.
Fixtures (4): valid, unknown schema, bad version, consent.
ScriptedTransport (7): success, exhaustion, disconnect, closed,
failure category, order, kind.
FailureInjector (5): determinism, zero/full probability, reset,
invalid probability.
Evidence (5): all-passed honesty, empty report, summary, simulated
level, diagnostics cap.
Isolation (2): no shared state, repeatable runs.

## Regression
Full suite: core + android, 0 failures.
