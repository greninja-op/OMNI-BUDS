# Phase 38 — Hardware Test Plan (deferred to Phase 52)

## Scope

Physical hardware verification happens in Phase 52, after the
complete application and final automated test suite exist.

## Prerequisites for Phase 52

- All production phases complete (0–51).
- A supported device with a verified hardware profile.
- Explicit operator consent for each mutating operation.
- A rollback or safe recovery strategy.
- Bounded timeouts and read-back plans.

## What stays deferred

- Device pairing and connection.
- Real Bluetooth discovery and commands.
- Hardware writes and persistence verification.
- Acoustic measurements.
- Any mutating operation on a physical device.

## What Phase 38 delivers

The contracts, safety gates, dry-run environment, profile schema,
campaign definitions, and report model that Phase 52 will execute
against — all validated in simulation.
