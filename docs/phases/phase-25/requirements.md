# Phase 25 — Quick Settings Integration: Requirements

**Status:** Authoritative for Phase 25 execution.
**Scope:** Android Quick Settings tile using public TileService APIs,
integrating with the Global Device State Engine, feature engine, and
access policy. No notification controls, widget, background lifecycle,
or full UI.
**Requirement ID scheme:** `OB-P25-REQ-001` … `OB-P25-REQ-028`.

## OB-P25-REQ-001 — TileService implementation
- **Description:** `OmniBudsTileService` implements the public TileService
  contract: listening, clicks, add/remove, unavailability. Registered in
  the manifest with `BIND_QUICK_SETTINGS_TILE` permission and QS_TILE
  intent filter.
- **Priority:** Must | **Verification:** Manifest test + `TileServiceTest`.

## OB-P25-REQ-002 — State from the global engine
- **Description:** Tile state derives from `GlobalDeviceStateRepository`;
  the tile owns no second state authority and no second Bluetooth
  connection.
- **Priority:** Must | **Verification:** `TileStateMapperTest`.

## OB-P25-REQ-003 — Deterministic state mapping
- **Description:** Pure `TileStateMapper` maps every engine state to a tile
  state: no device, unidentified, discovering, ready without controllable
  feature, ready with feature, operation pending, unavailable, unknown/
  stale, failure. Unknown is never rendered as a concrete value.
- **Priority:** Must | **Verification:** `TileStateMapperTest`.

## OB-P25-REQ-004 — Verified actions only
- **Description:** Only verified, supported, authorized hardware capabilities
  are actionable. Actions dispatch through the existing feature engine.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-005 — No blind toggles
- **Description:** Clicks read the latest verified mode, compute the valid
  next mode from the capability, reject ambiguous/stale states, dispatch,
  and reflect the observed result. Unknown mode → documented safe behavior.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-006 — Pre-execution checks
- **Description:** Before dispatch: resolve target, confirm session ready,
  confirm support, confirm freshness, check access policy, then dispatch.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-007 — Multi-device resolution
- **Description:** Explicit target-resolution policy: prefer explicit
  selection; verify eligibility; never silently first-match; ambiguous →
  refuse hardware action with honest unavailable state.
- **Priority:** Must | **Verification:** `TileTargetResolverTest`.

## OB-P25-REQ-008 — No device redirection
- **Description:** A command cannot be redirected to another device after
  the operation begins (target bound at dispatch).
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-009 — Lifecycle
- **Description:** Read latest state on start-listening; update while
  permitted; cancel on stop; no duplicate collectors; no permanent
  background service.
- **Priority:** Must | **Verification:** `QuickSettingsCoordinatorTest`.

## OB-P25-REQ-010 — API compatibility
- **Description:** minSdk 26; guarded code paths for API 29+ (subtitle)
  and API 33+ (tile-add request); compatibility matrix documented.
- **Priority:** Must | **Verification:** `android-compatibility.md` + review.

## OB-P25-REQ-011 — Privacy and lock screen
- **Description:** No sensitive details on lock screen by default; no
  credentials/payloads/identifiers in labels or logs; immutable
  PendingIntents; explicit scoped intents.
- **Priority:** Must | **Verification:** `TileSecurityTest`.

## OB-P25-REQ-012 — Authorization
- **Description:** Centralized access policy checked immediately before
  every execution; tile clicks never bypass it.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-013 — Error handling
- **Description:** Typed errors for all 15 failure modes (§10); no silent
  swallowing; no crashing the system tile service.
- **Priority:** Must | **Verification:** `TileErrorTest`.

## OB-P25-REQ-014 — Duplicate clicks
- **Description:** Duplicate/rapid clicks coalesced; no duplicate hardware
  operations.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-015 — Disconnection during command
- **Description:** Disconnection invalidates controls; in-flight operation
  reported as unknown, not success.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-016 — Manifest test update
- **Description:** `platformManifestDeclaresNothingUnjustified` gains the
  TileService with Phase 25 justification; no other new entries.
- **Priority:** Must | **Verification:** Architecture test run.

## OB-P25-REQ-017 — Documentation
- **Description:** 11 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P25-REQ-018 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.

## OB-P25-REQ-019 — No scope creep
- **Description:** No notifications, widget, background lifecycle, or full UI.
- **Priority:** Must | **Verification:** Scope review.

## OB-P25-REQ-020 — Deterministic tests
- **Description:** No hardware required; fakes for state, engine, policy.
- **Priority:** Must | **Verification:** Review.

## OB-P25-REQ-021 — Tile updates only when appropriate
- **Description:** `requestListeningState` / `updateTile` used within
  lifecycle constraints; no update spam.
- **Priority:** Must | **Verification:** `QuickSettingsCoordinatorTest`.

## OB-P25-REQ-022 — Out-of-order updates
- **Description:** State updates carry ordering; older never overwrites newer.
- **Priority:** Must | **Verification:** `TileStateMapperTest`.

## OB-P25-REQ-023 — Operation timeout
- **Description:** Commands time out; timeout surfaces as typed error.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-024 — Cancellation
- **Description:** Lifecycle end cancels in-flight work; no leaks.
- **Priority:** Must | **Verification:** `QuickSettingsCoordinatorTest`.

## OB-P25-REQ-025 — Intent security
- **Description:** No untrusted extras selecting devices/operations.
- **Priority:** Must | **Verification:** `TileSecurityTest`.

## OB-P25-REQ-026 — Failure honesty
- **Description:** Failed operations never display false success.
- **Priority:** Must | **Verification:** `TileActionDispatcherTest`.

## OB-P25-REQ-027 — Reconnection
- **Description:** Controls restore only after readiness checks pass.
- **Priority:** Must | **Verification:** `TileStateMapperTest`.

## OB-P25-REQ-028 — Local-first
- **Description:** No telemetry, no upload.
- **Priority:** Must | **Verification:** Scope review.
