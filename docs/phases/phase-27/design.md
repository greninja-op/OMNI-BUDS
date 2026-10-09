# Phase 27 — Design

## Modules

`platform/android/widget/`:

- `WidgetState` — pure presentation model (kinds: LOADING, UNAVAILABLE,
  STATUS, CONTROLS, PROGRESS, FAILED).
- `WidgetStateMapper` — pure `GlobalDeviceState? -> WidgetState`.
- `WidgetActionDispatcher` — validates + dispatches through injected seams.
  Per-instance binding; nonce replay guard; in-flight guard keyed by
  widget+feature.
- `WidgetTargetResolver` — explicit target policy (bound → selected →
  single → ambiguous → none).
- `WidgetCoordinator` — per-instance registration, reconciliation,
  deduplication, cleanup on removal.
- `OmniBudsWidgetProvider` — thin AppWidgetProvider shell; `onUpdate`,
  `onDeleted`, `onDisabled`, `onReceive`.
- `AndroidWidgetRenderer` — RemoteViews binding from `WidgetState`.
- `WidgetIds` — action strings and intent extra keys.
- `WidgetDependencies` — process-scoped install seam.

Resources: `res/xml/omnibuds_widget_info.xml` (metadata, updatePeriod=0),
`res/layout/widget_compact.xml`, `res/layout/widget_standard.xml`.

## State ownership

Single source: `GlobalDeviceStateRepository`. Widget renders immutable
snapshots; never mutates engine state directly.

## Data flow

Engine → coordinator → mapper → renderer → AppWidgetManager.
Actions: PendingIntent → provider → dispatcher → feature engine →
engine state → coordinator reconciles.

## Lifecycle

`onUpdate` registers; `onDeleted` unregisters; `onDisabled` stops.
Collection cancels when no instances remain. No polling, no permanent
service. Process recreation rebuilds from the repository.

## Concurrency

Conflated StateFlow; in-flight guards; nonce window (1000); idempotent
reconciliation; deduplicated renders.

## Security boundaries

- Provider exported=false; immutable PendingIntents; per-instance
  request codes.
- Every action validated: shape, nonce, instance binding, device, session,
  support, freshness, policy.
- No raw payloads in extras; identifiers only.

## Quick Settings / notification integration

Shared authoritative state and command contracts. No presentation
coupling.
