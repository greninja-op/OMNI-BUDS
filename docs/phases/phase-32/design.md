# Phase 32 — Design

## New modules

`platform/android/compat/`:

- `ApiLevelPolicy` — pure API-boundary decisions (26–35).
- `PermissionPolicy` — five-state permission decisions.
- `BluetoothPlatformPolicy` — adapter + permission → operation decision.

## Audits (no redesign)

- Tile (Phase 25): manifest + behavior verified.
- Notifications (Phase 26): channels + permission checker verified.
- Widget (Phase 27): metadata + honest rendering verified.
- Lifecycle (Phase 28): no-service compliance verified.

## Data flow

Platform state → policy objects → Allow/Refuse/Defer/CannotOperate/
Degraded → existing engines handle the decision.

## Concurrency

Policies are pure objects; no state, no locks.

## Security boundaries

- No new permissions declared.
- No bypasses; denials are refusals with reasons.
- Grant never implies operation success.
