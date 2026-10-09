# Phase 28 — Lifecycle State Machine

## Phases

```
UNKNOWN → STARTING → FOREGROUND ⇄ BACKGROUND → TERMINATING
```

## Transitions

| Event | From | To | Rule |
|---|---|---|---|
| ProcessStarted | UNKNOWN | STARTING | Once; repeats rejected |
| ForegroundEntered | STARTING, BACKGROUND | FOREGROUND | |
| BackgroundEntered | FOREGROUND | BACKGROUND | |
| ProcessTerminating | any (not TERMINATING) | TERMINATING | Best-effort |
| AdapterDisabled, PermissionRevoked, DeviceDisconnected, DeviceReconnected | any | (no change) | Signal events; ignored by the machine, handled by the coordinator |

## Invalid transitions

Rejected with a typed reason; never silently applied. Tested in
`LifecycleStateMachineTest`.

## Notes

- Device lifecycle (CONNECTED → READY → …) is separate and owned by the
  session/state engines.
- A device may be READY while the app is BACKGROUND-restricted.
