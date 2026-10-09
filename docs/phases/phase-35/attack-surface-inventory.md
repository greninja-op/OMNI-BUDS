# Phase 35 — Attack Surface Inventory

## Externally reachable components

| Component | Exported | Guard |
|---|---|---|
| TileService | true (required) | BIND_QUICK_SETTINGS_TILE |
| Notification receiver | false | explicit intents only |
| Widget provider | false | launcher-bound |

## Data entry points

| Entry | Trust | Validation |
|---|---|---|
| Bluetooth transport bytes | untrusted | InputValidator bounds |
| Imported traces/fixtures | untrusted | size bounds, schema validation |
| Knowledge packages | untrusted until verified | evidence workflow |
| Intent extras (actions) | untrusted | type/length checks, session ownership |
| Persisted records | untrusted on load | schema validation, migrations |

## No network transmission

The app has no network code; verified by dependency review
(kotlinx-coroutines only) and source search (no HTTP clients).
