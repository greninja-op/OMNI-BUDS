# Phase 26 — Privacy and Permissions

## Lock-screen policy

- Default visibility: PRIVATE (sensitive content redacted).
- Titles are generic ("OmniBuds"); bodies carry status, never device
  names, addresses, or feature values beyond basic status.
- No device identifiers in labels.

## Permission policy

- API 33+: `POST_NOTIFICATIONS` runtime permission. Denial → notifications
  removed; core Bluetooth/hardware architecture unaffected.
- Pre-33: install-time grant; channel-disabled → graceful degradation.
- Never repeatedly prompt; never circumvent denial.

## Intent security

- Explicit intents to `OmniBudsNotificationReceiver` only.
- Immutable PendingIntents; identity-derived request codes.
- Nonce replay guard (window of 1000).
- No raw protocol payloads in extras — identifiers only.
- Receiver exported=false; manifest test enforces it.

## Authorization

Access policy checked immediately before every dispatch. A notification
action is never an authorization bypass.

## Logging

Typed rejection reasons only; no identifiers, payloads, or credentials.
