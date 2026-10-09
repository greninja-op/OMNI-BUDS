# Phase 36 — Retention and Storage Policy

## Limits

| Item | Limit | Rationale |
|---|---|---|
| Store capacity | 512 events | Enough for recent failure diagnosis; bounded memory |
| Message size | 2,048 chars (truncate) | Preserves the event; bounds allocation |
| Recovery sink | 256 events | Existing Phase 34 bound |

## Overflow

Oldest-first eviction. Dropped events counted (saturating counter).
No recursive logging about drops.

## Cleanup

`clear()` resets store and counters. No automatic time-based expiry
in this phase — capacity bounding is the retention mechanism.

## Failure behavior

Invalid events (negative timestamp, blank message) are rejected and
counted. A corrupt record can never make the store unreadable.
