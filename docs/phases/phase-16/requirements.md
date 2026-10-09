# Phase 16 — Battery & Power State: Requirements

**Status:** Authoritative for Phase 16 execution.
**Scope:** Battery & Power State Engine — collect, normalize, validate, publish
battery/charging information. No invented readings, no UI, no firmware
updates, no charging control.
**Requirement ID scheme:** `OB-P16-REQ-001` … `OB-P16-REQ-026`.

---

## OB-P16-REQ-001 — Battery component model

- **Description:** Extensible `BatteryComponent`: LEFT_EARBUD, RIGHT_EARBUD,
  CHARGING_CASE, HEADPHONES, DEVICE, UNKNOWN. Future components without
  redesign.
- **Rationale:** Devices expose subsets; the model must not assume full sets.
- **Priority:** Must
- **Acceptance criteria:** Enum exists; UNKNOWN default.
- **Verification:** `BatteryComponentTest`.

## OB-P16-REQ-002 — Battery level value object

- **Description:** `BatteryLevel(percentage: Int?, observedAt, evidence)`.
  Valid 0–100 inclusive. 0 is valid. Missing is null, never zero. Invalid
  values rejected at the boundary, never silently zeroed.
- **Rationale:** Zero ≠ unknown is the phase's core honesty rule.
- **Priority:** Must
- **Acceptance criteria:** 0/100 valid; -1/101 rejected; null preserved.
- **Verification:** `BatteryLevelTest`.

## OB-P16-REQ-003 — Charging state

- **Description:** CHARGING / NOT_CHARGING / FULL / UNKNOWN. Independent of
  percentage. 100% ≠ FULL unless source semantics justify it.
- **Rationale:** Charging is observed, not inferred.
- **Priority:** Must
- **Acceptance criteria:** Four states; no percentage→charging inference.
- **Verification:** `ChargingStateTest`.

## OB-P16-REQ-004 — Battery state model

- **Description:** Immutable per-component state: device, session/generation,
  component, level, charging, availability, timestamp, freshness, evidence,
  verification, limitations.
- **Rationale:** One component's truth in one place.
- **Priority:** Must
- **Acceptance criteria:** All fields present; immutable.
- **Verification:** `BatteryStateTest`.

## OB-P16-REQ-005 — Composite snapshot

- **Description:** `BatterySnapshot`: id, device, session, timestamp, only
  components actually observed, freshness, evidence, observability,
  limitations, validation warnings. Partial information is normal.
- **Rationale:** Never manufacture components for completeness.
- **Priority:** Must
- **Acceptance criteria:** Partial snapshots supported; no fabrication.
- **Verification:** `BatterySnapshotTest`.

## OB-P16-REQ-006 — Capability integration

- **Description:** Reuse Phase 8 capability discovery. Separate: reporting
  supported / level observable / charging observable / per-component /
  case / refresh supported / mechanism / verification.
- **Rationale:** Capability ≠ observation.
- **Priority:** Must
- **Acceptance criteria:** Four states distinct (unknown/unsupported/
  supported/unavailable).
- **Verification:** `BatteryCapabilityTest`.

## OB-P16-REQ-007 — Android adapter

- **Description:** Read battery via legitimate Android APIs behind platform
  boundary. API-level guards. No minSdk raise. No mic permission. Missing
  values stay unknown. Permission denial handled gracefully.
- **Rationale:** Platform truth, honestly reported.
- **Priority:** Must
- **Acceptance criteria:** Adapter exists; guards tested.
- **Verification:** `AndroidBatteryAdapterTest`.

## OB-P16-REQ-008 — Protocol integration

- **Description:** Verified vendor protocols may supply left/right/case/
  charging via existing abstractions. No invented UUIDs/opcodes. Combined
  levels stay combined — never split.
- **Rationale:** Abstraction boundaries.
- **Priority:** Should
- **Acceptance criteria:** No packet literals (scope test).
- **Verification:** `ProtocolBatteryTest`.

## OB-P16-REQ-009 — Evidence and provenance

- **Description:** Every observation retains evidence. Reuse existing model.
  Runtime observations ≠ static capability claims. No confidence upgrades
  in normalization.
- **Rationale:** Provenance.
- **Priority:** Must
- **Acceptance criteria:** Evidence attached; no inflation.
- **Verification:** `BatteryEvidenceTest`.

## OB-P16-REQ-010 — Freshness

- **Description:** CURRENT / STALE / UNKNOWN. Documented policy on
  timestamps/events/lifecycle. Stale retained for diagnostics, never
  presented as current. Disconnect invalidates charging assumptions;
  battery ≠ zero on disconnect.
- **Rationale:** Time matters.
- **Priority:** Must
- **Acceptance criteria:** Policy documented; transitions tested.
- **Verification:** `BatteryFreshnessTest`.

## OB-P16-REQ-011 — Observation repository

- **Description:** Latest snapshot, change observation, refresh where
  legitimate, unsupported refresh reported, session invalidation.
  Event-driven; no aggressive polling.
- **Rationale:** One source of battery truth.
- **Priority:** Must
- **Acceptance criteria:** Repository exists; flows per device.
- **Verification:** `BatteryRepositoryTest`.

## OB-P16-REQ-012 — Partial updates

- **Description:** Explicit update semantics: omitted ≠ explicit unknown ≠
  zero ≠ not-charging. One component's update never erases another's valid
  state.
- **Rationale:** Ambiguous merging corrupts state.
- **Priority:** Must
- **Acceptance criteria:** Four-way distinction tested.
- **Verification:** `PartialUpdateTest`.

## OB-P16-REQ-013 — Multi-device isolation

- **Description:** Per-device/session snapshots. No global battery state.
- **Rationale:** Devices are independent.
- **Priority:** Must
- **Acceptance criteria:** Isolation tested.
- **Verification:** `MultiDeviceBatteryTest`.

## OB-P16-REQ-014 — Conflict resolution

- **Description:** Documented source precedence + freshness. Conflicts
  preserved or reported, never silently resolved to an arbitrary value.
- **Rationale:** Honesty under disagreement.
- **Priority:** Must
- **Acceptance criteria:** Policy documented; conflicts surfaced.
- **Verification:** `ConflictResolutionTest`.

## OB-P16-REQ-015 — Error model

- **Description:** Reuse structured errors. Expected unavailability →
  structured state, not exception. Failures never become zero-percent.
- **Rationale:** Unknown is normal.
- **Priority:** Must
- **Acceptance criteria:** New categories only if unmappable.
- **Verification:** Review.

## OB-P16-REQ-016 — Persistence boundaries

- **Description:** Current vs cached vs preferences vs firmware distinguished.
  No separate persistence system. Cached values keep timestamp+freshness.
  Restart ≠ current.
- **Rationale:** Time-travel is lying.
- **Priority:** Should
- **Acceptance criteria:** Boundaries documented.
- **Verification:** `PersistenceTest`.

## OB-P16-REQ-017 — Security and privacy

- **Description:** No address logging, no payload logging, minimal
  identifiers, no new permissions, no network/analytics.
- **Rationale:** Boundaries.
- **Priority:** Must
- **Acceptance criteria:** Scope test; review.
- **Verification:** `BatteryPrivacyTest`.

## OB-P16-REQ-018 — No fabrication

- **Description:** No fake production readings. Test fixtures clearly
  labeled. Zero test code in production paths.
- **Rationale:** The phase's hard boundary.
- **Priority:** Must
- **Acceptance criteria:** Scope test bans fixture vocabulary in main.
- **Verification:** `NoFabricationTest`.

## OB-P16-REQ-019 — Flow/state publication

- **Description:** Coroutines/Flow per repo conventions. Initial unknown,
  first observation, updates, dedup, disconnect/reconnect, stale
  publication, independent device streams.
- **Rationale:** Reactive truth.
- **Priority:** Must
- **Acceptance criteria:** Flow tests.
- **Verification:** `BatteryFlowTest`.

## OB-P16-REQ-020 — Architecture

- **Description:** Domain models Android-independent; Android classes inside
  the boundary.
- **Rationale:** Testable core.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P16-REQ-021 — Single vs multi-component

- **Description:** Headphones (single) and earbuds (left/right/case)
  supported. Combined readings never split.
- **Rationale:** Form factors differ.
- **Priority:** Must
- **Acceptance criteria:** Both modeled.
- **Verification:** `ComponentModelTest`.

## OB-P16-REQ-022 — No inference rules

- **Description:** No charging-from-percentage, no case-from-earbud, no
  left/right-from-combined, no charging-from-connected, no FULL-from-100%.
- **Rationale:** Inference is fabrication.
- **Priority:** Must
- **Acceptance criteria:** Each rule has a negative test.
- **Verification:** `NoInferenceTest`.

## OB-P16-REQ-023 — Performance

- **Description:** Lightweight; event-driven; bounded history; no main-thread
  blocking; no uncontrolled retries.
- **Rationale:** A battery engine shouldn't drain the battery.
- **Priority:** Must
- **Acceptance criteria:** Review; no polling in core.
- **Verification:** Review.

## OB-P16-REQ-024 — Documentation

- **Description:** Eight mandatory records.
- **Rationale:** Understandability.
- **Priority:** Must
- **Acceptance criteria:** All present and accurate.
- **Verification:** Review.

## OB-P16-REQ-025 — Regression

- **Description:** All Phase 0–15 tests pass.
- **Rationale:** No regressions.
- **Priority:** Must
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.

## OB-P16-REQ-026 — Stop condition

- **Description:** Phase 17 not started; no UI; no physical device; no
  firmware-update/charging-control functionality.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Final audit.
- **Verification:** Scope tests.
