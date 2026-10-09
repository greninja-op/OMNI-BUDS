# Phase 25 — Design

## Architecture

New package `com.omnibuds.android.tile` in `platform/android`:

- **TileState.kt** — platform-independent presentation model.
- **TileStateMapper.kt** — pure `GlobalDeviceState -> TileState` mapping.
- **TileTargetResolver.kt** — explicit multi-device resolution policy.
- **TileActionDispatcher.kt** — pre-execution checks + dispatch through
  injected seams (access check, write executor).
- **QuickSettingsCoordinator.kt** — lifecycle-aware observation and clicks.
- **OmniBudsTileService.kt** — thin TileService shell; all logic in the
  above, unit-tested without Android.

## Data flow

`GlobalDeviceStateRepository.allDevices` → coordinator → target resolution
→ `TileStateMapper` → `Tile.render` → `qsTile.updateTile()`.

Clicks: coordinator → target resolution → dispatcher (session, support,
freshness, access checks) → feature engine → observed state → re-render.

## State transitions

Tile kinds: NO_DEVICE → DISCONNECTED → UNIDENTIFIED → DISCOVERING →
READY_NO_ACTION / READY_WITH_ACTION → OPERATION_PENDING → (observed) →
READY_*. Failures → FAILED (honest, clickable when actions available).

## Tile lifecycle

`onStartListening` → collect; `onStopListening` → cancel; `onClick` →
dispatch; `onTileAdded/Removed` → reset; `onDestroy` → cancel scope.
No permanent background service.

## API contracts

`TileDependencies.install()` provides the coordinator and context.
`requestListeningState()` for out-of-band refreshes.

## Permissions

`BIND_QUICK_SETTINGS_TILE` (platform-required). No new Bluetooth
permissions.

## Failure handling

Typed `TileDispatchOutcome`/`TileDispatchRefusal`; failures render as
FAILED without false success; disconnection invalidates controls.

## Concurrency

Coordinator cancels previous collection on restart; dispatcher guards
duplicate clicks with a synchronized in-flight set; per-device Mutex in
the engine serializes state.
