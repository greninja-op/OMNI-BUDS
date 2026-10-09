# Phase 34 — Decisions

## D-34-01: reuse existing retry infrastructure
**Decision:** Map to `RetryClass`; keep `ReconnectPolicy` untouched.
**Rationale:** One recovery model; no duplicate loops.

## D-34-02: ambiguous writes reconcile
**Decision:** mayHaveExecuted + non-idempotent → RECONCILE, never
blind replay.
**Rationale:** Prevents duplicated hardware writes.

## D-34-03: cancellation first
**Decision:** Cancelled context always aborts before any other rule.
**Rationale:** Cancellation is not a failure to recover from.

## D-34-04: bounded diagnostics
**Decision:** 256-event sink, oldest-first eviction.
**Rationale:** Diagnostics must never become a failure source.

## D-34-05: no new production session changes
**Decision:** Recovery integrates via policy + state machine; session
engine untouched.
**Rationale:** Minimal, reviewable changes.
