# Phase 27 — Home-Screen Widget: Requirements

**Status:** Authoritative for Phase 27 execution.
**Scope:** Android home-screen widget using public AppWidget APIs,
integrating with the Global Device State Engine, feature engine, and
access policy. No background lifecycle, no full UI.
**Requirement ID scheme:** `OB-P27-REQ-001` … `OB-P27-REQ-030`.

## OB-P27-REQ-001 — Widget architecture
- **Description:** `OmniBudsWidgetProvider`, `WidgetCoordinator`,
  `WidgetStateMapper`, `WidgetRenderer`, `WidgetActionDispatcher`,
  `WidgetTargetResolver`, documented refresh policy.
- **Priority:** Must | **Verification:** Review + tests.

## OB-P27-REQ-002 — State from the global engine
- **Description:** Widget renders `GlobalDeviceStateRepository` snapshots;
  no competing state authority.
- **Priority:** Must | **Verification:** `WidgetStateMapperTest`.

## OB-P27-REQ-003 — State mapping
- **Description:** Deterministic mapping for: loading, no device,
  disconnected, unidentified, discovering, ready ± controls, operation
  pending, stale/unavailable, failed. Honest representation.
- **Priority:** Must | **Verification:** `WidgetStateMapperTest`.

## OB-P27-REQ-004 — Battery honesty
- **Description:** Left/right/case separate; unknown stays unknown; never
  zero for missing; stale never shown as current.
- **Priority:** Must | **Verification:** `WidgetStateMapperTest`.

## OB-P27-REQ-005 — Verified actions only
- **Description:** Actions exposed only when: unambiguous target, valid
  widget instance + session, genuine support, policy permitted, fresh
  state, valid for current state.
- **Priority:** Must | **Verification:** `WidgetActionDispatcherTest`.

## OB-P27-REQ-006 — Action lifecycle
- **Description:** 11-step lifecycle (§10): validate, resolve instance+
  device, verify session, confirm support, validate operation, recheck
  auth, submit, track, observe, refresh, record.
- **Priority:** Must | **Verification:** `WidgetActionDispatcherTest`.

## OB-P27-REQ-007 — Multi-device + instance safety
- **Description:** Explicit target policy; per-instance device association;
  no cross-instance leakage; stale/ambiguous → safe rejection.
- **Priority:** Must | **Verification:** `WidgetTargetResolverTest`.

## OB-P27-REQ-008 — Refresh policy
- **Description:** Documented policy: creation, config changes, state
  changes while active, app-initiated refresh, bounded scheduled refresh
  only if justified. No polling, no permanent service.
- **Priority:** Must | **Verification:** `widget-refresh-policy.md` + tests.

## OB-P27-REQ-009 — PendingIntent security
- **Description:** Explicit intents, immutable flags, per-instance/device/
  action request codes, strict ID validation, no payload extras.
- **Priority:** Must | **Verification:** `WidgetSecurityTest`.

## OB-P27-REQ-010 — Privacy
- **Description:** Conservative defaults; minimal device info; content
  descriptions; status not color-only.
- **Priority:** Must | **Verification:** `privacy-and-interactions.md` + tests.

## OB-P27-REQ-011 — Responsive layouts
- **Description:** Compact + standard layouts; readable at compact sizes;
  empty/loading/error states; no unsupported RemoteViews.
- **Priority:** Must | **Verification:** Review + layout XML.

## OB-P27-REQ-012 — Manifest + metadata
- **Description:** AppWidgetProvider declared; widget metadata XML;
  manifest tests updated.
- **Priority:** Must | **Verification:** Architecture tests.

## OB-P27-REQ-013 — Quick Settings/notification consistency
- **Description:** Shared authoritative state and command contracts.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-014 — Error handling
- **Description:** Typed handling for all §13 cases; no silent swallowing;
  no state corruption.
- **Priority:** Must | **Verification:** `WidgetErrorTest`.

## OB-P27-REQ-015 — No scope creep
- **Description:** No background lifecycle, full UI, or new notification features.
- **Priority:** Must | **Verification:** Scope review.

## OB-P27-REQ-016 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-017 — Duplicate actions
- **Description:** Duplicate/out-of-order requests handled safely.
- **Priority:** Must | **Verification:** `WidgetActionDispatcherTest`.

## OB-P27-REQ-018 — Requested vs observed
- **Description:** Pending/accepted/observed distinct; accepted-unconfirmed
  shown as pending.
- **Priority:** Must | **Verification:** `WidgetStateMapperTest`.

## OB-P27-REQ-019 — No auto-retry
- **Description:** No arbitrary retries for unsafe writes.
- **Priority:** Must | **Verification:** `WidgetActionDispatcherTest`.

## OB-P27-REQ-020 — Widget removal cleanup
- **Description:** Instance removal cleans up associated state/work.
- **Priority:** Must | **Verification:** `WidgetCoordinatorTest`.

## OB-P27-REQ-021 — Process recreation
- **Description:** State rebuilt from authoritative sources.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-022 — Deduplication
- **Description:** Identical updates not re-rendered.
- **Priority:** Must | **Verification:** `WidgetCoordinatorTest`.

## OB-P27-REQ-023 — Concise widget
- **Description:** Small relevant action set; not a full-app replacement.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-024 — Main-thread safety
- **Description:** No heavy processing on the main thread.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-025 — Accessibility
- **Description:** Content descriptions; status not color-only; clear labels.
- **Priority:** Must | **Verification:** Review + layout XML.

## OB-P27-REQ-026 — Documentation
- **Description:** 12 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P27-REQ-027 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.

## OB-P27-REQ-028 — Stale actions
- **Description:** Stale widget actions rejected safely.
- **Priority:** Must | **Verification:** `WidgetActionDispatcherTest`.

## OB-P27-REQ-029 — Local-first
- **Description:** No telemetry, no upload.
- **Priority:** Must | **Verification:** Scope review.

## OB-P27-REQ-030 — Refresh honesty
- **Description:** No claim of guaranteed real-time updates.
- **Priority:** Must | **Verification:** `widget-refresh-policy.md`.
