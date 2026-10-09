# Phase 27 — Widget Design

## Widget kinds

| Engine state | Widget |
|---|---|
| No target | "No device" — unavailable |
| Disconnected | "Disconnected" — unavailable |
| Unidentified | "Identifying…" — status |
| Discovering | "Discovering…" — status |
| Ready, no actions | "Connected" + battery — status |
| Ready, actions | "Connected" + battery + up to 2 actions |
| Operation pending | "Applying change…" — progress |
| Failed | "Action failed" |

## Layouts

Compact: status text (+ battery when known). Standard: status, battery,
two action buttons. Buttons hidden when no actions; battery hidden when
unknown.

## Battery

"L 80%" style; unknown → hidden; stale → hidden. Never 0% for missing.

## Actions

Toggle between verified modes; labels clear; content descriptions include
current mode. Max 2 actions.
