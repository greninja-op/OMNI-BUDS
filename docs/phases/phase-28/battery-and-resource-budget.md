# Phase 28 — Battery and Resource Budget

## Budget principles

- No polling loops.
- No permanent foreground service.
- No aggressive reconnect (max 3 attempts, exponential backoff to 60s).
- No background scanning.
- No redundant transport sessions (one owner per session).
- No heavy main-thread work.
- No wake locks in this phase.

## Measurable expectations

- Idle background: zero scheduled work (updatePeriod=0 on the widget;
  no periodic workers).
- Reconnect episode: ≤ 3 attempts with backoff; cancelled on
  adapter-off/permission-revocation.
- Resource registry: every owned callback/job released on owner end;
  `releaseAll` on termination (best-effort).

No performance measurements are claimed; these are architectural
bounds enforced by design and tests.
