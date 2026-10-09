# Phase 27 — Specifications

## Widget metadata

| Field | Value |
|---|---|
| minWidth/minHeight | 110dp × 40dp |
| updatePeriodMillis | 0 (app-initiated only) |
| resizeMode | horizontal\|vertical |
| widgetCategory | home_screen |

## Layouts

| Layout | Use |
|---|---|
| `widget_compact` | Status only |
| `widget_standard` | Status + battery + up to 2 actions |

## Action contract

- Action: `com.omnibuds.android.widget.TOGGLE_FEATURE`
- Extras: `widget_id`, `device_id`, `session_id`, `feature_id`, `nonce`
- PendingIntent: `getBroadcast`, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`
- Request code: `hashCode(widgetId + featureId + deviceId)`

## Operation outcomes

Accepted → Dispatched; Rejected (malformed, invalid widget id, unknown
device, target mismatch, stale session, unsupported, stale/unknown state,
access denied, duplicate, disconnected, executor refused).

## Refresh

App-initiated via `AppWidgetManager.updateAppWidget` on state changes
while collecting; `updatePeriodMillis=0` (no platform polling). No
claim of real-time updates.

## State reconciliation

Idempotent; identical snapshots deduplicated; instance removal cleans up.
