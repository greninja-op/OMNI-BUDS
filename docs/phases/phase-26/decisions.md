# Phase 26 — Decisions

## D-26-01: platform Notification.Builder
**Decision:** Use platform APIs (minSdk 26 → channels native), no androidx.
**Rationale:** No androidx.core dependency exists; API 26 guarantees channels.

## D-26-02: manifest-declared receiver
**Decision:** `OmniBudsNotificationReceiver` manifest-declared, exported=false.
**Rationale:** Notification PendingIntents need a manifest target when the
app may not be running. Exported=false limits delivery to the system.

## D-26-03: no POST_NOTIFICATIONS declaration
**Decision:** Library does not declare the runtime permission.
**Rationale:** On API 33+ the host requests it; declaring moves the library
into a prompt group for a permission the host may never grant.

## D-26-04: nonce replay guard
**Decision:** 1000-entry nonce window; replays rejected as duplicates.
**Rationale:** PendingIntents can be redelivered; replays must not re-dispatch.

## D-26-05: no foreground service
**Decision:** `goAsync()` for dispatch only; no persistent service.
**Rationale:** Phase explicitly forbids services solely for notifications.
