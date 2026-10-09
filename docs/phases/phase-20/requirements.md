# Phase 20 — Protocol Laboratory: Requirements

**Status:** Authoritative for Phase 20 execution.
**Scope:** Offline protocol research subsystem — trace import/analysis, parser
framework, correlation, fixtures, test runner, evidence workflow. Passive
analysis only; unknown data never becomes executable commands. No UI, no
Phase 21, no vendor integration, no physical hardware.
**Requirement ID scheme:** `OB-P20-REQ-001` … `OB-P20-REQ-030`.

## OB-P20-REQ-001 — Versioned trace format
- **Description:** `TraceFormat` v1 with id, version, timestamps, source type,
  transport, protocol/device/firmware refs, session, ordered events, redaction
  metadata, provenance.
- **Priority:** Must | **Verification:** `TraceFormatTest`.

## OB-P20-REQ-002 — Event model
- **Description:** Events with id, ordering, direction
  (HOST_TO_DEVICE/DEVICE_TO_HOST/OBSERVATION/UNKNOWN), category, payload,
  lengths, correlation IDs, parser/redaction status.
- **Priority:** Must | **Verification:** `TraceFormatTest`.

## OB-P20-REQ-003 — Source classification
- **Description:** SYNTHETIC_FIXTURE / DOCUMENTED_EXAMPLE / SANITIZED_CAPTURE /
  IMPORTED_TRACE / GENERATED_TEST_CASE. Never mislabel synthetic as captured.
- **Priority:** Must | **Verification:** `TraceProvenanceTest`.

## OB-P20-REQ-004 — Trace validation
- **Description:** Reject/quarantine: bad versions, missing fields, invalid
  directions, length mismatches, bad encodings, duplicate IDs, bad ordering,
  oversized payloads. Structured errors with field paths.
- **Priority:** Must | **Verification:** `TraceValidationTest`.

## OB-P20-REQ-005 — Schema migration
- **Description:** Version migration only when defined and tested; unknown
  future versions rejected safely.
- **Priority:** Must | **Verification:** `TraceMigrationTest`.

## OB-P20-REQ-006 — Import pipeline
- **Description:** Size/format checks → decode → validate → normalize →
  sensitive-data detection → redaction → validated record. Bounded inputs.
- **Priority:** Must | **Verification:** `TraceImportTest`.

## OB-P20-REQ-007 — Redaction
- **Description:** Typed redaction metadata; masks addresses/identifiers/
  credentials. Records structural changes; sanitized ≠ byte-identical.
- **Priority:** Must | **Verification:** `RedactionTest`.

## OB-P20-REQ-008 — Parser contract
- **Description:** `LabParser`: protocol id, version compat, input format,
  size bounds, framing, fields, encoding, validation, errors, partial/multi
  support.
- **Priority:** Must | **Verification:** `ParserFrameworkTest`.

## OB-P20-REQ-009 — Framing support
- **Description:** Fixed-length, length-prefixed, delimiter, header/body,
  sequence-numbered, checksum, fragmented input, multi-message buffers,
  incomplete handling.
- **Priority:** Must | **Verification:** `FramingTest`.

## OB-P20-REQ-010 — Typed parser outcomes
- **Description:** Parsed / Incomplete / UnsupportedFormat / Malformed /
  UnsupportedVersion / LimitExceeded. Never default objects for malformed.
- **Priority:** Must | **Verification:** `ParserFrameworkTest`.

## OB-P20-REQ-011 — Unknown messages stay unknown
- **Description:** Bounded hex display allowed; no guessed semantics, no
  executable operations from unknown data.
- **Priority:** Must | **Verification:** `UnknownMessageTest`.

## OB-P20-REQ-012 — Structured inspection
- **Description:** Typed message model: type, version, direction, header/
  payload fields, lengths, sequence, checksum, status, errors, evidence,
  confidence, unknown fields.
- **Priority:** Must | **Verification:** `MessageInspectorTest`.

## OB-P20-REQ-013 — Correlation
- **Description:** Pair requests/responses on sequence/txn/command/session
  evidence. Preserve unmatched; detect duplicates/ambiguity; no cross-session
  pairing; explicit confidence.
- **Priority:** Must | **Verification:** `CorrelationTest`.

## OB-P20-REQ-014 — Session timeline
- **Description:** Ordered domain timeline: connections, sessions, messages,
  notifications, timeouts, failures, redactions, annotations.
- **Priority:** Must | **Verification:** `TimelineTest`.

## OB-P20-REQ-015 — Differential analysis
- **Description:** Deterministic trace comparison (ordering, direction, type,
  fields, lengths, parser outcomes). Differences ≠ semantic proof.
  Observation/hypothesis/confirmed/implemented/verified kept distinct.
- **Priority:** Must | **Verification:** `DifferentialAnalysisTest`.

## OB-P20-REQ-016 — Schema registry
- **Description:** Versioned parser/schema definitions with provenance,
  firmware constraints, validation/framing/correlation rules, verification
  status. No silent overwrites; conflicts rejected.
- **Priority:** Must | **Verification:** `SchemaRegistryTest`.

## OB-P20-REQ-017 — Fixture generation
- **Description:** Deterministic fixtures from validated traces with id,
  origin, classification, redaction status, expected results, versions,
  evidence refs. Provenance preserved.
- **Priority:** Must | **Verification:** `FixtureGeneratorTest`.

## OB-P20-REQ-018 — Test runner
- **Description:** Headless deterministic runner: test id, parser version,
  fixture, expected/actual, diagnostics, duration, failure classification.
  Categories: valid/incomplete/malformed/unknown/version/length/checksum/
  duplicate/corrupt/oversized/conflict.
- **Priority:** Must | **Verification:** `ParserTestRunnerTest`.

## OB-P20-REQ-019 — Evidence workflow
- **Description:** INFERRED → IMPLEMENTED → LAB_TESTED → HARDWARE_VERIFIED →
  PERSISTENCE_VERIFIED with explicit evidence requirements per transition.
  Synthetic fixtures can never produce hardware/persistence claims.
  Auditable transitions.
- **Priority:** Must | **Verification:** `EvidenceWorkflowTest`.

## OB-P20-REQ-020 — Safety boundary
- **Description:** No packet transmission, no raw writes, no replay, no
  fuzzing, no random commands, no setting changes, no flashing, no pairing/
  auth bypass, no hidden APIs, no trace auto-execution.
- **Priority:** Must | **Verification:** `LabSafetyTest`.

## OB-P20-REQ-021 — Resource limits
- **Description:** Configurable conservative bounds: file size, event count,
  payload size, total bytes, message length, concurrent traces, diagnostics.
  Documented in specs.md. Violations reported explicitly.
- **Priority:** Must | **Verification:** `ResourceLimitTest`.

## OB-P20-REQ-022 — Security/privacy
- **Description:** Trust boundaries, no script execution from imports, no
  trusted metadata identity, redaction correctness, no diagnostic leakage,
  trace isolation per device, cancellation cleanup.
- **Priority:** Must | **Verification:** `LabSecurityTest`.

## OB-P20-REQ-023 — Integration
- **Description:** Reuses ProtocolDefinition, TransportKind, VerificationLevel,
  error categories. `core.lab` at layer 5; no sideways imports.
- **Priority:** Must | **Verification:** Architecture test.

## OB-P20-REQ-024 — Documentation
- **Description:** 12 required documents, accurate to implementation.
- **Priority:** Must | **Verification:** Review.

## OB-P20-REQ-025 — Regression
- **Description:** Phases 6–19 unchanged.
- **Priority:** Must | **Verification:** Full test run.

## OB-P20-REQ-026 — No UI / Phase 21
- **Description:** No UI, Phase 21 not started, no vendor integration.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P20-REQ-027 — Deterministic
- **Description:** All analysis reproducible; no hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P20-REQ-028 — Local by default
- **Description:** No cloud, no uploads, no external services.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P20-REQ-029 — Parser/test separation from control
- **Description:** Lab never obtains device-control write capability.
- **Priority:** Must | **Verification:** `LabSafetyTest`.

## OB-P20-REQ-030 — Bounded parsing
- **Description:** No unbounded buffering, no pathological regex, no
  uncontrolled recursion, temp buffers released, cancellation supported.
- **Priority:** Must | **Verification:** `ResourceLimitTest`.
