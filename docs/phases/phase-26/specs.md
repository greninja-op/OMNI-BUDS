# Phase 26 — Specifications

## Channels

| ID | Name | Importance |
|---|---|---|
| `omnibuds_device_status` | Device status | LOW (no sound/vibration/badge) |

## Notification IDs

| ID | Value | Purpose |
|---|---|---|
| `NOTIFICATION_DEVICE_STATUS` | 1001 | Device status + controls |

## Action contract

- Action: `com.omnibuds.android.notification.TOGGLE_FEATURE`
- Extras: `action_id`, `device_id`, `session_id`, `feature_id`, `nonce`
- PendingIntent: `getBroadcast`, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`
- Request code: `hashCode(deviceId + featureId + index)`

## Operation outcomes

Accepted → Dispatched; Rejected (malformed, unknown device, stale
session, ambiguous, unsupported, stale/unknown state, access denied,
duplicate, disconnected, executor refused).

## Timeouts

No arbitrary retries. Operation completion signaled via engine state;
in-flight guard released by `complete(featureId)`.

## State reconciliation

Idempotent; identical snapshots deduplicated; disconnect/permission
denial cancels the notification.
