# Phase 26 — Notification Controls: Requirements

**Status:** Authoritative for Phase 26 execution.
**Scope:** Android notification controls using public APIs, integrating
with the Global Device State Engine, feature engine, and access policy.
No widget, background lifecycle, or full UI.
**Requirement ID scheme:** `OB-P26-REQ-001` … `OB-P26-REQ-030`.

## OB-P26-REQ-001 — Notification architecture
- **Description:** `NotificationCoordinator`, `NotificationFactory`,
  `NotificationChannelManager`, `NotificationActionDispatcher`,
  `NotificationStateMapper`, `NotificationPermissionChecker`,
  `NotificationLifecycleHandler` (adapted to repo conventions).
- **Priority:** Must | **Verification:** Review + tests.

## OB-P26-REQ-002 — State from the global engine
- **Description:** Notifications derive from `GlobalDeviceStateRepository`;
  no competing state authority.
- **Priority:** Must | **Verification:** `NotificationStateMapperTest`.

## OB-P26-REQ-003 — State mapping
- **Description:** Deterministic mapping covering: no device, unidentified,
  discovering, ready ± controls, operation pending, unavailable,
  disconnected, unknown/stale, failed. Honest representation rules.
- **Priority:** Must | **Verification:** `NotificationStateMapperTest`.

## OB-P26-REQ-004 — Verified actions only
- **Description:** Actions exposed only when: unambiguous target, valid
  session, genuine support, access-policy permitted, fresh state, valid
  for current state, platform permits.
- **Priority:** Must | **Verification:** `NotificationActionDispatcherTest`.

## OB-P26-REQ-005 — Action lifecycle
- **Description:** 10-step lifecycle (§7): parse, resolve, verify, check
  policy, validate, submit, track, observe, reconcile, record outcome.
- **Priority:** Must | **Verification:** `NotificationActionDispatcherTest`.

## OB-P26-REQ-006 — Multi-device safety
- **Description:** Explicit target policy; stable device+session reference
  in every actionable notification; revalidate at execution; stale/
  ambiguous → safe rejection, no redirection.
- **Priority:** Must | **Verification:** `NotificationTargetTest`.

## OB-P26-REQ-007 — Channels
- **Description:** Stable channel IDs; Android 8+ channel requirements;
  user-disabled channels degrade gracefully.
- **Priority:** Must | **Verification:** `NotificationChannelTest`.

## OB-P26-REQ-008 — Permissions
- **Description:** Android 13+ POST_NOTIFICATIONS runtime permission;
  denial → graceful degradation; never a prerequisite for core function.
- **Priority:** Must | **Verification:** `NotificationPermissionTest`.

## OB-P26-REQ-009 — PendingIntent security
- **Description:** Explicit intents, immutable flags, correct request codes,
  strict ID validation, replay/stale rejection, no raw payloads in extras.
- **Priority:** Must | **Verification:** `NotificationSecurityTest`.

## OB-P26-REQ-010 — Lock-screen privacy
- **Description:** Conservative defaults; sensitive content redacted;
  visibility controls; documented policy.
- **Priority:** Must | **Verification:** `privacy-and-permissions.md` + tests.

## OB-P26-REQ-011 — Freshness and ordering
- **Description:** Reuse engine provenance/freshness; stale rejected;
  idempotent reconciliation; no older-over-newer.
- **Priority:** Must | **Verification:** `NotificationStateMapperTest`.

## OB-P26-REQ-012 — Quick Settings consistency
- **Description:** Shared authoritative state and command contracts with
  Phase 25; no direct presentation coupling.
- **Priority:** Must | **Verification:** `SharedContractTest`.

## OB-P26-REQ-013 — Error handling
- **Description:** Typed handling for all 14 cases (§14); no silent
  swallowing; no corruption of persistent state.
- **Priority:** Must | **Verification:** `NotificationErrorTest`.

## OB-P26-REQ-014 — No permanent service
- **Description:** No foreground service solely for notifications; no
  polling loop.
- **Priority:** Must | **Verification:** Review.

## OB-P26-REQ-015 — Battery honesty
- **Description:** Unknown stays unknown; left/right/case separate; no
  fabricated values; no stale-as-current.
- **Priority:** Must | **Verification:** `NotificationStateMapperTest`.

## OB-P26-REQ-016 — Deduplication
- **Description:** Identical updates deduplicated; no alert spam.
- **Priority:** Must | **Verification:** `NotificationCoordinatorTest`.

## OB-P26-REQ-017 — Disconnect cleanup
- **Description:** Stale controls removed on disconnect; in-flight ops
  reported as unknown.
- **Priority:** Must | **Verification:** `NotificationCoordinatorTest`.

## OB-P26-REQ-018 — Process recreation
- **Description:** State rebuilt from authoritative sources; no fabricated
  restoration.
- **Priority:** Must | **Verification:** Review.

## OB-P26-REQ-019 — Manifest
- **Description:** Action receiver declared with justification; manifest
  tests updated.
- **Priority:** Must | **Verification:** Architecture tests.

## OB-P26-REQ-020 — Documentation
- **Description:** 11 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P26-REQ-021 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.

## OB-P26-REQ-022 — No scope creep
- **Description:** No widget, background lifecycle, or full UI.
- **Priority:** Must | **Verification:** Scope review.

## OB-P26-REQ-023 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P26-REQ-024 — Duplicate actions
- **Description:** Duplicate/out-of-order action requests handled safely.
- **Priority:** Must | **Verification:** `NotificationActionDispatcherTest`.

## OB-P26-REQ-025 — Timeouts
- **Description:** Bounded operation timeouts; timeout → typed outcome.
- **Priority:** Must | **Verification:** `NotificationActionDispatcherTest`.

## OB-P26-REQ-026 — Cancellation
- **Description:** Lifecycle cancellation; no leaks.
- **Priority:** Must | **Verification:** `NotificationCoordinatorTest`.

## OB-P26-REQ-027 — Requested vs observed
- **Description:** Pending/accepted/observed/persisted stay distinct;
  accepted-but-unconfirmed shown as pending.
- **Priority:** Must | **Verification:** `NotificationStateMapperTest`.

## OB-P26-REQ-028 — No auto-retry
- **Description:** No arbitrary retries for unsafe writes.
- **Priority:** Must | **Verification:** `NotificationActionDispatcherTest`.

## OB-P26-REQ-029 — Concise notifications
- **Description:** Small relevant action set; no notification per feature.
- **Priority:** Must | **Verification:** Review.

## OB-P26-REQ-030 — Local-first
- **Description:** No telemetry, no upload.
- **Priority:** Must | **Verification:** Scope review.
