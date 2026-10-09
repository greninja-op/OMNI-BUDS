# Phase 26 — Notification Design

## Notification kinds

| Engine state | Notification |
|---|---|
| No device | None (hidden) |
| Disconnected | None (controls removed) |
| Unidentified | "Device connected, identifying…" — status only |
| Discovering | "Discovering capabilities…" — status only |
| Ready, no actions | "Connected" (+ battery if known+fresh) — status |
| Ready, actions | "Connected" + up to 3 verified actions |
| Operation pending | "Applying change…" — progress |
| Failed | "Last action failed" |

## Labels

Fixed "OmniBuds" title. Body carries honest status. Lock screen shows
the same generic text (PRIVATE visibility).

## Actions

Toggle between verified modes of a feature, computed from the observed
mode and the real mode set. Capped at 3 actions.
