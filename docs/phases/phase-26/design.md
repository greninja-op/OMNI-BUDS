# Phase 26 — Design

## Modules

`platform/android/notification/`:

- `NotificationState` — pure presentation model (kinds: HIDDEN, STATUS,
  CONTROLS, PROGRESS, FAILED).
- `NotificationIds` — stable channel/notification IDs, action strings,
  intent extra keys.
- `NotificationStateMapper` — pure `GlobalDeviceState? -> NotificationState`.
- `NotificationActionDispatcher` — validates + dispatches through injected
  seams (access check, write executor). Nonce replay guard, in-flight guard.
- `NotificationCoordinator` — collects `GlobalDeviceStateRepository.allDevices`,
  reconciles, deduplicates, cancels on disconnect/permission denial.
- `NotificationFactory` — platform `Notification.Builder`; channel creation;
  immutable PendingIntents with identity-derived request codes.
- `OmniBudsNotificationReceiver` — manifest-declared, exported=false;
  validates every intent as untrusted input.
- `NotificationPermissionChecker` — API 33+ runtime check; channel-enabled check.
- `NotificationDependencies` — process-scoped install seam.

## State ownership

Single source: `GlobalDeviceStateRepository`. Notifications render
immutable snapshots; they never mutate engine state directly.

## Data flow

Engine → coordinator → mapper → factory → NotificationManager.
Actions: PendingIntent → receiver → dispatcher → feature engine →
engine state → coordinator reconciles.

## Lifecycle

Coordinator start/stop cancels collection. No polling, no permanent
service. Process recreation rebuilds from the repository.

## Concurrency

Conflated StateFlow; in-flight guards keyed by feature; nonce replay
window (1000). Idempotent reconciliation.

## Security boundaries

- Receiver exported=false; immutable PendingIntents.
- Every intent validated: shape, nonce, device, session, support,
  freshness, policy.
- No raw payloads in extras; identifiers only.
- Lock-screen visibility PRIVATE by default.

## Quick Settings integration

Shared authoritative state (`GlobalDeviceStateRepository`) and command
contracts (feature engine + access policy). No presentation coupling.
