# Phase 36 — Test Plan

## New tests (4 classes, 19 tests)
- DiagnosticStoreTest (6): bounds, truncation, sequencing, severity
  filter, clear, counter saturation.
- DiagnosticHealthTest (5): health, persistence-failure degradation,
  sink degradation, saturation, timestamps.
- DiagnosticExporterTest (6): versioning, redaction, empty refusal,
  event cap, size cap, newest-first.
- SinkFailureIsolationTest (2): failing sink isolation, hostile input.

## Regression
Full suite: core + android, 0 failures.
