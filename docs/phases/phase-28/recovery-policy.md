# Phase 28 — Recovery Policy

## Principles

- Respond to trustworthy connection events.
- Reconcile after process recreation; never trust a persisted CONNECTED flag.
- Reuse sessions only with live proof.
- Preserve verified observations and provenance.
- Mark unavailable info as unknown/stale.
- Never replay interrupted hardware writes.
- Never infer success from a restart.

## Process-start sequence

1. Init DI and repositories.
2. Load persisted config + last-known state.
3. Mark observations by age/provenance.
4. Init lifecycle observers.
5. Reconcile platform observations.
6. Invalidate unprovable sessions.
7. Restore only safely restorable resources.
8. Rediscover capabilities when required.
9. Publish reconciled state.
10. Update surfaces where the platform allows.

## Interrupted operations

Marked `MarkInterrupted`; surfaced as unknown/pending-never-confirmed;
require explicit user action to retry.
