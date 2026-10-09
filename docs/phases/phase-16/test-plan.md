# Phase 16 — Test Plan

## Levels (`BatteryLevelTest`)
0/1/50/99/100 valid; null → null; -1/101 rejected; lenient parse; zero ≠ unknown.

## Engine (`BatteryEngineTest`)
Initial unknown; partial updates preserve siblings; omitted ≠ explicit
unknown; zero stays zero; stale sessions rejected; disconnect preserves
battery/invalidates charging; device isolation; dedup; charging ⊥ percentage;
freshness aging.

## Adaptation (`BatteryAdaptationTest`)
Legacy → updates; invalid dropped; false → NOT_CHARGING; never FULL;
conflict fresher-wins; stale incoming rejected.

## Scope (`BatteryScopeTest`)
No fabrication, no inference, no protocol literals, no UI, no hidden APIs.

## Android (`AndroidBatteryObservationSourceTest`)
Unsupported reported; empty flow; refresh reason.

## Regression
Full suite: 1018 core + 139 android, 0 failures.
