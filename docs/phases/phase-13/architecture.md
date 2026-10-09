# Phase 13 — Architecture

## Layer placement

The `quality` package sits at layer 4 (with `protocol`), above `audio`
(layer 2) and `codec` (layer 3). It consumes their vocabularies; nothing
below it depends on it. Enforced by `DependencyDirectionTest`.

## Data flow

1. **Ingest:** The bridge (or tests) calls `onTransportUpdate`,
   `onCodecSnapshot`, `onControlState`, `onDisconnected`, `onBecameStale`.
2. **Resolve:** `AudioQualityResolver.resolve()` — pure, deterministic —
   produces the new `AudioQualityState`.
3. **Detect:** Previous vs new state → meaningful `NegotiationEvent`s.
4. **Session:** Negotiation lifecycle drives `NegotiationSession`.
5. **Publish:** Deduplicated `StateFlow` update; events to `SharedFlow` +
   bounded timeline.

## Concurrency

- One `Mutex` guards all mutable engine state.
- `SupervisorJob + dispatcher`; `stop()` cancels the scope.
- Event emission is synchronous (`tryEmit` into a 64-slot buffer).
- No polling, no timers, no background loops.

## Multi-device

All state is keyed by `DeviceIdentity`. Device A's observations never touch
Device B's state. Disconnecting A leaves B untouched (tested).

## Honesty invariants

- Negotiated ≠ active; supported ≠ negotiated; connected ≠ active.
- UNKNOWN is never upgraded without evidence.
- Stale state is preserved, flagged, and never presented as current.
- Conflicts are flagged (`hasConflict`), never silently resolved.
