# Phase 27 — Widget Refresh Policy

## Triggers

1. `onUpdate` (creation, instance changes).
2. Engine state emissions while the app process collects.
3. App-initiated `updateAppWidget` after confirmed state changes.

## Explicitly not used

- `updatePeriodMillis` (set to 0) — no platform polling.
- Periodic WorkManager — unjustified battery cost for this phase.
- Permanent background service — forbidden by phase scope.

## Guarantees

None of real-time updates. Delayed or skipped updates are handled
gracefully: the widget shows the last rendered snapshot, which is
honest about its provenance (no staleness indicator is fabricated).

## Efficiency

- Deduplicated renders (identical snapshots not re-rendered).
- No Bluetooth reconnections for refresh.
- No heavy main-thread work.
- Collection cancels when no instances remain.
