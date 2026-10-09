# Phase 35 — Privacy Data Inventory

## Data categories

| Category | Purpose | Personal? | Persisted? | Retention | Transmitted? |
|---|---|---|---|---|---|
| Device identity (model, firmware) | capability matching | no | yes (config) | until removed | never |
| Bluetooth address | connection | potentially | no (session only) | session | never |
| User preferences (per device) | settings | no | yes | until changed | never |
| Protocol knowledge | compatibility | no | yes | versioned | never |
| Diagnostic events | failure diagnosis | no | bounded (256) | evicted | never |
| Test fixtures | development | no | no | n/a | never |

## Controls

- No analytics, no telemetry, no account system.
- No network transmission (verified: no HTTP clients, no network deps).
- No microphone or media capture.
- No location permissions.
- MAC addresses redacted in diagnostics.
- Diagnostic sink bounded; failure cannot crash the app.
