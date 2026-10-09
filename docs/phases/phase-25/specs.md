# Phase 25 — Specifications

## TileService

`OmniBudsTileService : TileService()`:
- `onStartListening` / `onStopListening` — observation lifecycle.
- `onClick` — delegates to coordinator.
- `onTileAdded` / `onTileRemoved` — reset.
- `onDestroy` — cancel scope.
- Renders via `qsTile`: state, label, subtitle (API 29+), content
  description, `updateTile()`.

## Manifest

```xml
<service android:name=".tile.OmniBudsTileService"
    android:label="OmniBuds"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

## TileState

`TileState(active, label, subtitle?, clickable, kind)`.
Kinds: NO_DEVICE, DISCONNECTED, UNIDENTIFIED, DISCOVERING,
READY_NO_ACTION, READY_WITH_ACTION, OPERATION_PENDING, UNAVAILABLE,
UNKNOWN, FAILED.

## Mapping rules

- Null → NO_DEVICE.
- Failure → FAILED (clickable iff actions available).
- Pending op → OPERATION_PENDING (not clickable).
- Disconnected/connecting → DISCONNECTED/UNAVAILABLE.
- Connected + unidentified → UNIDENTIFIED.
- Protocol not resolved/compatible → UNAVAILABLE.
- Capabilities not ready → DISCOVERING/UNAVAILABLE.
- Expired observations → UNKNOWN (never guessed).
- Ready + no controllable features → READY_NO_ACTION.
- Ready + features → READY_WITH_ACTION (clickable).
- Battery shown only when known; missing ≠ 0%.

## Action dispatch

`prepareToggle(state, featureId, modes, writable)`:
1. Connected session required.
2. Writable required.
3. Observed value known + usable required.
4. Observed mode must be in the mode set.
5. Duplicate-click guard.
6. Access policy check.
7. Next mode = cycle from real mode set.
8. Write through injected executor; target bound at dispatch.

## Target resolution

No devices → NoDevice. Explicit selection (if eligible) → Resolved.
One eligible → Resolved. Multiple → Ambiguous (refuse). None eligible →
Unresolvable.
