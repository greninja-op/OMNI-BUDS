# Phase 24 — Event Ordering

## Sequence numbers

Per-device monotonic sequences. An event with `sequence < lastApplied`
is rejected as stale; `== lastApplied` is a safe duplicate.

## Session correlation

`ConnectionChanged` establishes the current session id and generation.
Later non-connection events carrying a different non-null session id are
rejected as old-session events. Sessionless events always apply.

## Deduplication

Exact sequence repeats return `Duplicate` without state churn.

## Stale-event rejection

Recorded as bounded diagnostics (`StaleEvent`, `OldSessionEvent`).

## Clock discipline

Source timestamps are never compared across unrelated clock domains.
Ordering uses sequences and session generations, not wall-clock
comparison.

## Uncertainty

When ordering cannot be established, the event is applied (sequences are
the ordering mechanism) — but conflicting sources are preserved rather
than silently merged.
