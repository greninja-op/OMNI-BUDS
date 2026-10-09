# Phase 28 — Specifications

## Lifecycle phases

UNKNOWN → STARTING → FOREGROUND ↔ BACKGROUND → TERMINATING.

## Events

`ProcessStarted(previousDeath)`, `ForegroundEntered`, `BackgroundEntered`,
`AdapterDisabled`, `PermissionRevoked`, `DeviceDisconnected(deviceId)`,
`DeviceReconnected(deviceId)`, `ProcessTerminating`.

## Reconnect policy

- Max 3 automatic attempts per disconnection episode.
- Backoff: 5s × 2^attempt, capped at 60s. User-initiated: no backoff.
- Eligible triggers: PLATFORM_EVENT, USER_REQUEST, PROCESS_RESTART.
- Denied when: bluetooth off, permission revoked, device unpaired,
  max attempts reached, periodic/unknown trigger.

## Recovery actions

`InvalidateSession` (default), `KeepSession` (only with live proof),
`MarkStale`, `MarkInterrupted` (never replay), `RediscoverCapabilities`
(only when required).

## Resource registry

Owner-keyed; replace releases old; `releaseAll` on termination;
idempotent release.
