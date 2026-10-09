# Phase 36 — Diagnostic Test Matrix

| Test class | Tests | Coverage | Evidence |
|---|---|---|---|
| DiagnosticStoreTest | 6 | bounds, truncation, sequencing, severity filter, clear | UNIT_TESTED |
| DiagnosticHealthTest | 5 | health, degradation, saturation, timestamps | UNIT_TESTED |
| DiagnosticExporterTest | 6 | versioning, redaction, empty refusal, event/size caps, ordering | REDACTION_VERIFIED |
| SinkFailureIsolationTest | 2 | sink failure isolation, hostile input | UNIT_TESTED |

## Not covered

- Persistent disk store (no disk layer in this phase).
- Android logcat sink (platform implementation deferred).
- Real failure-burst load (unit bounds only).
