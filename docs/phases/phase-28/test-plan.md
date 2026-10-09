# Phase 28 — Test Plan

## Unit (`LifecycleTests`, 28 tests)
State machine (7): transitions, rejections, signal events, terminal.
Reconnect policy (9): eligibility, denials, backoff bounds, user-initiated.
Recovery planner (6): invalidation, live proof, staleness, interrupted
marking, conditional rediscovery.
Resource registry (4): release, replace, releaseAll, idempotency.

## Integration
Coordinator + hooks (documented contract; hooks implemented by the host).

## Regression
Full suite: core + android, 0 failures.
