# Phase 26 — Android Compatibility

## SDK configuration

minSdk 26, targetSdk 35, compileSdk 35.

## Compatibility matrix

| API | Behavior |
|---|---|
| 26–32 | Notification channels required (native API 26+); permission is install-time |
| 33+ | `POST_NOTIFICATIONS` runtime permission; denial → graceful degradation |

## Guarded paths

- Permission check branches on `Build.VERSION_CODES.TIRAMISU`.
- `Notification.Action.Builder` uses the Icon-based constructor (API 23+;
  the int-based one is deprecated on 35).

## Permissions

No `POST_NOTIFICATIONS` uses-permission declared in the library manifest:
on API 33+ it is a runtime permission the host app requests; declaring it
here would move the library into a prompt group for a permission the host
may never grant.

## No foreground service

No service created for notifications. `goAsync()` keeps the receiver
alive only for the dispatch itself.
