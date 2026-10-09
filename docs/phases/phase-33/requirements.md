# Phase 33 — Requirements

**ID scheme:** `A33-REQ-001` … `A33-REQ-016`.

## A33-REQ-001 — Audio architecture audit
- **Description:** Document the actual audio ownership model: the app
  manages settings and observes platform state; it never processes
  media samples.
- **Rationale:** The framework must match reality.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md records the finding.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-002 — Non-interference tests
- **Description:** Architecture tests proving no capture permissions,
  no AudioRecord/MediaRecorder in production, no media pipeline.
- **Rationale:** The app must not touch the user's audio path.
- **Dependencies:** A33-REQ-001. **Priority:** Must.
- **Acceptance:** NonInterferenceTest passes.
- **Verification:** NonInterferenceTest. **Status:** VERIFIED.

## A33-REQ-003 — Codec-state consistency
- **Description:** Tests that SUPPORTED/AVAILABLE/ENABLED/NEGOTIATED/
  ACTIVE are never confused; active-codec observation stays
  NOT_OBSERVABLE/UNKNOWN on Android.
- **Rationale:** Phase 11–14 ladder discipline.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** CodecStateConsistencyTest passes.
- **Verification:** CodecStateConsistencyTest. **Status:** VERIFIED.

## A33-REQ-004 — Test-only fixture framework
- **Description:** Deterministic synthetic PCM fixtures (silence, sine,
  left-only, clipped, discontinuity, impulse) in test sources only.
- **Rationale:** Offline verification of the analysis utilities.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** AudioFixtureTest passes; no production references.
- **Verification:** AudioFixtureTest. **Status:** VERIFIED.

## A33-REQ-005 — Signal-integrity utilities
- **Description:** peak, RMS, silence, clipping count/ratio,
  discontinuity, channel identity, mismatch count — documented
  formulas, ranges, tolerances, empty/invalid handling.
- **Rationale:** Minimal defensible metric set.
- **Dependencies:** A33-REQ-004. **Priority:** Must.
- **Acceptance:** SignalAnalysisTest passes.
- **Verification:** SignalAnalysisTest. **Status:** VERIFIED.

## A33-REQ-006 — Timing abstraction
- **Description:** MonotonicClock, SystemMonotonicClock, FakeClock,
  TimingMeasurer with per-category measurements; no acoustic-latency
  category.
- **Rationale:** Application timings ≠ acoustic latency.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** TimingMeasurementTest passes.
- **Verification:** TimingMeasurementTest. **Status:** VERIFIED.

## A33-REQ-007 — No fake capabilities
- **Description:** No fake ANC/transparency/spatial/DSP; no codec name
  presented as quality.
- **Rationale:** Core product principle.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Code review; nothing added.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-008 — Evidence classifications
- **Description:** UNIT_TESTED / DIGITAL_FIXTURE_VERIFIED /
  DEFERRED_TO_HARDWARE_TESTING among others; never assign hardware
  evidence to synthetic tests.
- **Rationale:** Honest evidence.
- **Dependencies:** Phase 31 model. **Priority:** Must.
- **Acceptance:** audio-evidence-and-limitations.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-009 — Audio quality matrix
- **Description:** audio-quality-matrix.md mapping requirements to
  fixtures, tolerances, layers, evidence, results, limitations.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Document exists and matches tests.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A33-REQ-010 — Fixture specification
- **Description:** audio-fixture-specification.md: format, fields,
  generation, provenance rules.
- **Rationale:** Reproducibility.
- **Dependencies:** A33-REQ-004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-011 — Latency specification
- **Description:** latency-measurement-specification.md: categories,
  clock rules, validity, limitations.
- **Rationale:** Timing discipline.
- **Dependencies:** A33-REQ-006. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-012 — Failure injection
- **Description:** Invalid fixtures rejected; empty input handled;
  incomplete measurements marked.
- **Rationale:** Expected errors classified.
- **Dependencies:** A33-REQ-004/006. **Priority:** Should.
- **Acceptance:** Covered in tests.
- **Verification:** AudioFixtureTest, TimingMeasurementTest. **Status:** VERIFIED.

## A33-REQ-013 — No user-audio capture
- **Description:** No microphone, loopback, or media capture added for
  testing.
- **Rationale:** Privacy and scope.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** NonInterferenceTest.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-014 — Hardware metrics deferred
- **Description:** Acoustic latency, frequency response, distortion,
  ANC effectiveness marked DEFERRED_TO_HARDWARE_TESTING.
- **Rationale:** Cannot be measured in software.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** audio-quality-matrix.md lists them.
- **Verification:** Review. **Status:** VERIFIED.

## A33-REQ-015 — Documentation
- **Description:** All 13 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A33-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
