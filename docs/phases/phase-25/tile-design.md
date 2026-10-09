# Phase 25 — Tile Design

## Tile conditions

| Engine state | Tile |
|---|---|
| No device | "OmniBuds / No device", inactive, unavailable |
| Disconnected | "OmniBuds / Disconnected", unavailable |
| Connected, unidentified | "OmniBuds / Identifying…", inactive |
| Discovering | "OmniBuds / Discovering…", inactive |
| Ready, no actions | "OmniBuds / Battery N% or Connected", active, not clickable |
| Ready, actions | "OmniBuds / Battery N% or Tap for options", active, clickable |
| Operation pending | "OmniBuds / Working…", unavailable |
| Failed | "OmniBuds / Action failed", clickable iff actions available |
| Stale/unknown | "OmniBuds / State unknown", unavailable |

## Labels

Fixed "OmniBuds" label. Subtitles carry honest status, never fabricated
hardware values, never sensitive identifiers. Lock screen shows the same
generic text — no per-device details.

## Actions

Toggle between verified modes of a single feature (e.g. ANC modes),
computed from the observed mode and the real mode set. No blind toggles.
