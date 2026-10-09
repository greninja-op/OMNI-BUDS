# Phase 16 — Specifications

## BatteryComponent
LEFT_EARBUD, RIGHT_EARBUD, CHARGING_CASE, HEADPHONES, DEVICE, UNKNOWN.

## BatteryLevel
Value class; `of(Int?)`: 0–100 valid, null → null, else
IllegalArgumentException. `parseLenient`: null for missing/invalid.

## ChargingState
CHARGING, NOT_CHARGING, FULL, UNKNOWN (default).

## BatteryFreshness
CURRENT, STALE, UNKNOWN. Stale-after: 5 minutes (documented policy).

## ComponentBatteryState
component, level?, charging, observedAtMillis?, freshness, evidence?,
limitations. `unknown()` factory.

## BatterySnapshot
snapshotId, device, sessionGeneration, observedAtMillis,
components (only observed), overallFreshness, warnings. `empty()` factory.

## BatteryUpdate
component, sessionGeneration, level: UpdateField, charging: UpdateField,
observedAtMillis, evidence?, sourceName. `isEmpty` when both omitted.

## BatteryEngine
- `observe(device): StateFlow<BatterySnapshot>`
- `latest(device): BatterySnapshot?`
- `applyUpdate`: rejects stale sessions; dedups; returns null when no change.
- `onDisconnect`: bumps generation; marks stale; charging → UNKNOWN.
- `applyFreshnessPolicy`: CURRENT → STALE past threshold.

## BatteryConflictResolver
Fresher wins; same-timestamp → higher rank wins; conflicts recorded in
warnings. Ranks: UNKNOWN 0, INFERRED 1, ANDROID_PLATFORM 2,
VERIFIED_PROTOCOL 3.

## LegacyBatteryStateAdapter
Phase 1 `BatteryState` → per-component `BatteryUpdate`s. Boolean? →
CHARGING/NOT_CHARGING/omitted. Never manufactures FULL.

## AndroidBatteryObservationSource
`isSupported()` = false (no public API as of API 35). `observe` = emptyFlow.
`refresh` = Unsupported with reason.
