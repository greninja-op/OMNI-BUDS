# Phase 24 — Global Device State Engine: Requirements

**Status:** Authoritative for Phase 24 execution.
**Scope:** Unified reactive per-device state engine coordinating Phases 5–23
subsystems through typed contracts. No UI, no Phase 25, no hardware.
**Requirement ID scheme:** `OB-P24-REQ-001` … `OB-P24-REQ-030`.

## OB-P24-REQ-001 — Typed per-device snapshot
- **Description:** Immutable `GlobalDeviceState` with typed submodels for
  identity, connection, protocol, capabilities, features, battery, audio,
  configuration, persistence, vendor features. No giant weakly-typed map.
- **Priority:** Must | **Verification:** `GlobalStateModelTest`.

## OB-P24-REQ-002 — Partial state
- **Description:** Unknown/unavailable fields represented explicitly; no
  fabricated defaults to feign completeness.
- **Priority:** Must | **Verification:** `GlobalStateModelTest`.

## OB-P24-REQ-003 — Multi-device isolation
- **Description:** Per-device records; no cross-device leakage; concurrent
  observation of multiple devices.
- **Priority:** Must | **Verification:** `MultiDeviceTest`.

## OB-P24-REQ-004 — State ownership
- **Description:** Every property documents its authoritative source; the
  engine aggregates, never invents.
- **Priority:** Must | **Verification:** `state-source-precedence.md` + review.

## OB-P24-REQ-005 — Source precedence
- **Description:** Property-specific precedence; conflicting equally-credible
  sources preserved as typed uncertainty.
- **Priority:** Must | **Verification:** `PrecedenceTest`.

## OB-P24-REQ-006 — Freshness and provenance
- **Description:** Observations carry source, timestamps, session ID,
  generation, freshness classification, verification status. Property-
  specific thresholds; Current/Recent/Stale/Expired/Unknown.
- **Priority:** Must | **Verification:** `FreshnessTest`.

## OB-P24-REQ-007 — Event aggregation
- **Description:** Typed events → validate → resolve identity → ordering
  check → merge → recompute derived → publish immutable snapshot.
- **Priority:** Must | **Verification:** `AggregationTest`.

## OB-P24-REQ-008 — Ordering and deduplication
- **Description:** Session IDs, generations, sequence numbers; stale events
  rejected; duplicates safe; no cross-clock comparison.
- **Priority:** Must | **Verification:** `AggregationTest`.

## OB-P24-REQ-009 — Atomic publication
- **Description:** One event → one coherent snapshot; no half-applied
  updates exposed.
- **Priority:** Must | **Verification:** `AggregationTest`.

## OB-P24-REQ-010 — Requested/executing/observed/persisted separation
- **Description:** Desired, operation status, acknowledgement, observed
  value, freshness, verification status kept distinct.
- **Priority:** Must | **Verification:** `StateSeparationTest`.

## OB-P24-REQ-011 — Disconnection semantics
- **Description:** Disconnect marks session ended; historical observations
  become stale per policy; never auto-erased; reconnect starts new session.
- **Priority:** Must | **Verification:** `LifecycleTest`.

## OB-P24-REQ-012 — Derived state
- **Description:** Deterministic derived properties (connected, identity
  complete, protocol compatible, capabilities ready, control permitted,
  operation pending, desired≠observed, stale, conflicting, per-capability
  readiness). No single "READY" implying everything.
- **Priority:** Must | **Verification:** `DerivedStateTest`.

## OB-P24-REQ-013 — Observation APIs
- **Description:** `observeAllDevices`, `observeDevice`, `getDevice`,
  `removeDeviceState`; immutable snapshots; cancellation; backpressure
  documented; no mutable maps exposed.
- **Priority:** Must | **Verification:** `RepositoryApiTest`.

## OB-P24-REQ-014 — Retention and recovery
- **Description:** Transient vs observed vs config vs verification
  distinguished; no second persistence system; recovery never fabricates
  live connections.
- **Priority:** Must | **Verification:** `RecoveryTest`.

## OB-P24-REQ-015 — Concurrency
- **Description:** Serialized per-device updates; no lost updates; structured
  concurrency; device removal racing updates handled; source failure
  isolates to affected device.
- **Priority:** Must | **Verification:** `ConcurrencyTest`.

## OB-P24-REQ-016 — Diagnostics
- **Description:** Typed reason codes for rejected/duplicate/conflicting
  events, session mismatches, stream failures. No raw payloads.
- **Priority:** Must | **Verification:** `DiagnosticsTest`.

## OB-P24-REQ-017 — Consistency validation
- **Description:** Validator detects impossible combinations; reports;
  applies only documented deterministic corrections; never silently
  rewrites evidence.
- **Priority:** Must | **Verification:** `ConsistencyTest`.

## OB-P24-REQ-018 — Architecture
- **Description:** `core.globalstate` at layer 5; imports only common(0),
  state(0), device(2), audio(2), session(3), codec(3). No sideways imports.
- **Priority:** Must | **Verification:** Architecture test.

## OB-P24-REQ-019 — No duplicate sources of truth
- **Description:** Reuse existing repositories via adapters; never duplicate
  ownership.
- **Priority:** Must | **Verification:** Review.

## OB-P24-REQ-020 — Documentation
- **Description:** 12 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P24-REQ-021 — Regression
- **Description:** Phases 5–23 compatible; read-only restrictions intact.
- **Priority:** Must | **Verification:** Full test run.

## OB-P24-REQ-022 — No UI / Phase 25
- **Description:** No UI, Phase 25 not started.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P24-REQ-023 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P24-REQ-024 — No automatic writes
- **Description:** The engine never initiates hardware writes; desired≠
  observed does not trigger execution.
- **Priority:** Must | **Verification:** `StateSeparationTest`.

## OB-P24-REQ-025 — Wall-clock-free
- **Description:** Time via injected `TimeProvider`; no direct clock reads.
- **Priority:** Must | **Verification:** Review.

## OB-P24-REQ-026 — Bounded resources
- **Description:** Bounded event buffers and diagnostic histories; no
  unbounded growth.
- **Priority:** Must | **Verification:** `ConcurrencyTest`.

## OB-P24-REQ-027 — Late/old-session events
- **Description:** Late events from old sessions rejected with typed reason;
  never overwrite new-session state.
- **Priority:** Must | **Verification:** `AggregationTest`.

## OB-P24-REQ-028 — Unknown vs disconnected vs removed
- **Description:** `observeDevice` distinguishes never-observed,
  disconnected, and removed.
- **Priority:** Must | **Verification:** `RepositoryApiTest`.

## OB-P24-REQ-029 — Backpressure
- **Description:** StateFlow-based (conflated); documented behavior; no
  unbounded buffering.
- **Priority:** Must | **Verification:** `RepositoryApiTest`.

## OB-P24-REQ-030 — Local-first
- **Description:** No telemetry, no upload, no cloud.
- **Priority:** Must | **Verification:** Scope tests.
