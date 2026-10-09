# Phase 27 — Privacy and Interactions

## Privacy policy

- Device names are not shown on the widget (generic "OmniBuds" implied
  by the widget itself; status text only).
- No addresses, credentials, protocol payloads, or diagnostics.
- Battery shown only when known and fresh.
- A home-screen widget is visible to onlookers: conservative defaults.

## Interaction safety

- Action labels are explicit ("Toggle ANC"); content descriptions state
  the current mode to avoid accidental changes.
- Status is text, never color-only.
- Tap targets are standard buttons; hidden when no action.
- Stale/unknown state never presents an actionable control.

## Intent security

- Explicit intents; immutable PendingIntents; per-instance request codes.
- Nonce replay guard; strict ID validation; no payload extras.
- Authorization rechecked at execution; stale actions rejected.
