# Phase 16 — Design: Battery & Power State

## Architecture

```
BatteryReportingSupport (Phase 7 protocol)
         ↓
LegacyBatteryStateAdapter (Phase 16)  ← NEW: Phase 1 → Phase 16 mapping
         ↓
BatteryUpdate (explicit partial semantics)
         ↓
BatteryEngine (Phase 16)  ← NEW: per-device StateFlow repository
         ↓
BatterySnapshot (immutable aggregate)
```

## New types (`com.omnibuds.core.battery`, layer 5)

| Type | Role |
|---|---|
| `BatteryComponent` | 6-value component enum |
| `BatteryLevel` | Validated 0–100 value class; null = unknown |
| `ChargingState` | CHARGING / NOT_CHARGING / FULL / UNKNOWN |
| `BatteryFreshness` | CURRENT / STALE / UNKNOWN |
| `ComponentBatteryState` | Per-component immutable truth |
| `BatterySnapshot` | Per-device immutable aggregate |
| `UpdateField` / `BatteryUpdate` | Explicit partial-update semantics |
| `BatteryEngine` | Per-device StateFlow repository |
| `BatteryConflictResolver` | Fresher-wins + recorded conflicts |
| `LegacyBatteryStateAdapter` | Phase 1 → Phase 16 mapping |
| `BatteryObservationSource` | Source contract |

## Android (`com.omnibuds.android.bluetooth.battery`)

`AndroidBatteryObservationSource`: honestly reports UNSUPPORTED — no public
API exists for Bluetooth device battery as of API 35
(`BluetoothDevice.getBatteryLevel()` is hidden). No permissions, no polling.

## Reused (not duplicated)

- `BatteryState` (Phase 1), `BatteryReportingSupport` (Phase 7)
- `DeviceIdentity`, `CodecEvidence`, verification models

## Key decisions

1. **Zero ≠ unknown.** `BatteryLevel.of(0)` is a reading; `of(null)` is unknown.
2. **No inference.** Charging never derived from percentage; FULL never from 100%.
3. **Explicit partial updates.** Omitted ≠ explicit unknown ≠ zero.
4. **Disconnect preserves history.** Battery kept stale; charging → UNKNOWN.
5. **Android honesty.** No public API → explicit UNSUPPORTED, not silence.
