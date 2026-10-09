# Phase 13 — Negotiation Model

## States

`UNKNOWN → IDLE → PREPARING → NEGOTIATING → NEGOTIATED → ACTIVE`

Plus: `FAILED` (from NEGOTIATING/PREPARING/NEGOTIATED), `DISCONNECTED` (from
any active state), `STALE` (from ACTIVE and others).

## Derivation rules

The negotiation state is **derived** from observable facts:

| Observation | Derived state |
|---|---|
| No device info | UNKNOWN |
| Transport connected, nothing else | IDLE |
| Codec at SUPPORTED rung | PREPARING |
| Codec at ENABLED/AVAILABLE rung | NEGOTIATING |
| Codec at NEGOTIATED rung | NEGOTIATED |
| Codec ACTIVE + route live | ACTIVE |
| Transport disconnected | DISCONNECTED |
| Snapshot freshness STALE | STALE |

## Transitions

See `NegotiationTransitions` — the legal table. Illegal transitions are
rejected; the engine keeps the current state.

## Sessions

A session begins on entering NEGOTIATING and completes on NEGOTIATED/ACTIVE/
FAILED. Disconnect terminates the session. Sessions never live forever.

## Events

Twelve immutable event kinds cover transport detection, device availability,
route changes, negotiation start/completion, codec changes, parameter
changes, active/inactive transitions, disconnects, staleness, and failures.

## Timeline

Per-device bounded history (32 events) enables coherent reconstruction:
connected → device → negotiating → codec → route → parameters.
