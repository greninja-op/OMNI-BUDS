# Phase 16 — Decisions

## D-16-01: build on Phase 1, don't replace
**Decision:** Reuse `BatteryState` (Phase 1) and `BatteryReportingSupport`
(Phase 7) via `LegacyBatteryStateAdapter`; Phase 16 adds the engine layer.
**Rationale:** The flat struct exists; the engine was missing.

## D-16-02: explicit partial-update semantics
**Decision:** `UpdateField` sealed type: Omitted / ExplicitUnknown / Set.
**Rationale:** Nullable merging cannot distinguish "not said" from "unknown".

## D-16-03: Android honesty
**Decision:** `AndroidBatteryObservationSource` reports UNSUPPORTED with
reason; no hidden API calls.
**Rationale:** `BluetoothDevice.getBatteryLevel()` is `@hide`; calling it
would violate the no-hidden-APIs rule.

## D-16-04: fresher wins, conflicts recorded
**Decision:** Conflict resolver prefers freshness, then source rank; every
real conflict surfaces as a snapshot warning.
**Rationale:** Silent arbitrary picks are fabrication.

## D-16-05: 5-minute staleness policy
**Decision:** `staleAfterMillis` default 5 minutes, documented and injectable.
**Rationale:** Arbitrary timeouts need rationale; this is conservative for
battery (slow-moving state) and testable via injected clock.
