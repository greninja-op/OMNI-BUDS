# Phase 28 — Design

## Modules

`core/lifecycle/`:

- `LifecycleStateMachine` — pure process-phase state machine.
- `LifecycleEvent` / `LifecycleTransition` — typed events and outcomes.
- `ReconnectPolicy` — bounded, deterministic reconnect decisions.
- `ProcessRecoveryPlanner` — recovery plans; invalidates unprovable
  sessions; marks interrupted operations (never replays).
- `ResourceLifecycleRegistry` — explicit resource ownership.
- `BackgroundLifecycleCoordinator` — orchestrates events, recovery,
  reconnects, cleanup.

`platform/android/lifecycle/`:

- `OmniBudsLifecycleMonitor` — Application callbacks → typed events.

## State ownership

`GlobalDeviceStateRepository` remains the device-state authority.
The lifecycle system owns *process* state and *recovery decisions*, not
device state.

## Data flow

Platform callbacks → monitor → events → coordinator → (recovery plans,
reconnect decisions, resource release) → hooks → transport/session/state
engines.

## Lifecycle

STARTING → FOREGROUND ↔ BACKGROUND → TERMINATING. Signal events
(adapter, permission, device) don't change the process phase.

## Concurrency

One collector; reconnect jobs keyed by device; cancellation on session
end; deduped recovery triggers.

## Security boundaries

- Permission revocation stops unauthorized work immediately.
- No background operation bypasses the access policy.
- No evasion of Android background restrictions.

## Service decision

No foreground service justified in this phase (see service-eligibility.md).
