# Battery Architecture — Iteration 2

## The defect (from root-cause-audit.md, Issue 4)
Two battery architectures existed: a component-aware Phase-16 stack
(core/battery, dead code) and the live Phase-24 global-state stack,
whose BatteryState.Known had no component slots. The app ingested
exactly one aggregate value; per-component UI was unreachable.

## The corrected live pipeline
Bluetooth observation → DeviceStateBridge → DeviceStateEvent
.BatteryUpdated → GlobalDeviceStateRepository/DeviceStateAggregator →
BatteryState.Known → UnifiedBatteryModel → BatteryPresentationModel →
DeviceWorkspaceViewModel → WorkspaceScreen battery section.

`BatteryState.Known` now carries, beside the aggregate:
- `components: List<ComponentLevel>` — typed slots
  (LEFT_EARBUD / RIGHT_EARBUD / CHARGING_CASE), each with nullable
  levelPercent (validated 0..100) and nullable charging.
- `observation` provenance (source, observed-at) as before.

Invariants (enforced by construction + tests):
- A component exists only when a source reported that component.
- The aggregate is never copied into components (the AirPods helper
  that did this was rewritten; regression-tested).
- Components never collapse into a fake aggregate
  (UnifiedBatteryModel.fromComponents no longer borrows a component
  value as "overall").
- Unknown is null, never 0, at every layer.
- Per-device isolation via per-device aggregators (unchanged) and
  bridge maps keyed by address (unchanged).
- Out-of-order protection via the aggregator's sequence checks
  (unchanged; component payloads ride the same events).

## Freshness model
- Live: device connected, value from the current connection.
- Last known: device disconnected → the ViewModel marks the retained
  values `isLastKnown`; the UI greys the rings and captions them
  "Last known — the device is disconnected."
- Stale: presentation computes `isStale` from provenance age (60 s
  threshold in the model); the aggregate view captions stale readings
  "This reading may be out of date."
- Reconnect refresh: the bridge now clears its battery dedupe cache
  on disconnect, so a reconnect re-ingests the same level with a
  fresh timestamp instead of the old reading ageing silently.

## Component ingestion contract
`DeviceStateBridge.onBatteryComponents(address, components,
aggregateLevel)` is the single entry point for any future verified
per-component source (vendor protocol). It validates each component,
drops invalid values (never coerces), dedupes unchanged snapshots,
and preserves the last standard aggregate unless a fresher one is
supplied. **No production source calls it yet** — see
battery-source-matrix.md.

## Charging state
The only live source (Android's battery-level broadcast) carries no
charging flag, so charging is null end-to-end and the UI shows no
charging indicator. The model supports it per component when a source
provides it.
