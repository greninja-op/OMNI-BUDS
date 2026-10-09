# Phase 25 — Android Compatibility

## SDK configuration

minSdk 26, targetSdk 35, compileSdk 35.

## Compatibility matrix

| API | TileService | Notes |
|---|---|---|
| 26–28 | Yes | No subtitle (`Tile.subtitle` is API 29+); guarded |
| 29–32 | Yes | Subtitle available |
| 33+ | Yes | `requestAddTileService()` for user-initiated add; not used yet |

## Guarded paths

- `tile.subtitle` set only on API 29+ (`Build.VERSION_CODES.Q`).
- `requestListeningState()` available API 24+; safe when not listening.

## Permissions

`BIND_QUICK_SETTINGS_TILE` — platform-required for tile services,
declared on the service (not a runtime permission).

## Background restrictions

The tile does not run a permanent service. Updates happen while the
tile is listening or via `requestListeningState()`. Continuous real-time
updates are not promised.
