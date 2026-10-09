# Phase 38 — Rig Architecture

## Components

- `HilRig` — interface: rigId, environment, initialize, cleanup.
- `FakeHilRig` — deterministic fake; records requested operations;
  never touches Bluetooth.
- Future: Android-host rig, DUT adapter, evidence collector —
  deferred to Phase 52.

## Design rules

- Interfaces and injectable dependencies.
- No BLE/GATT assumptions.
- Cleanup always attempted.
- No global mutable rig state.
