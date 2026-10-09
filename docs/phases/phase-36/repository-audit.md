# Phase 36 — Repository Audit

**Date:** 2026-10-09

## Existing diagnostics infrastructure (verified)

| Component | Location | Notes |
|---|---|---|
| `DiagnosticEvent` | `core/diagnostics/DiagnosticEvent.kt` | Immutable; timestamp, severity, category, pre-redaction message, operationId, structured error. No address/name/payload fields by design |
| `DiagnosticSeverity` | `core/diagnostics/` | TRACE/PACKET opt-in gated; DEBUG/INFO/WARN/ERROR always available |
| `DiagnosticCategory` | `core/diagnostics/` | Typed categories |
| `OmniBudsLogger` | `core/diagnostics/` | Minimal seam; redaction at emission; no upload; no persistence widening |
| `DiagnosticSnapshot`/`SnapshotBuilder` | `core/diagnostics/` | Structured snapshots |
| Recovery events | `core/recovery/RecoveryEvents.kt` | Bounded 256-event sink (Phase 34) |
| LogRedactor | `core/security/LogRedactor.kt` | Pure redaction boundary (Phase 35) |

## Gaps

1. No bounded persistent/in-memory diagnostic store with retention.
2. No diagnostic health/self-observability model.
3. No safe local export path (sanitized, bounded, versioned).
4. No sink-failure isolation tests.
5. No overflow/retention tests.

## Implementation strategy

New in `core/diagnostics/`:

- `DiagnosticStore` — bounded ring buffer (capacity 512), dropped
  counters, retention policy, corrupt-record handling.
- `DiagnosticHealth` — sink availability, saturation, dropped/persist
  failure counts, last success time.
- `DiagnosticExporter` — versioned sanitized export, size-bounded,
  never automatic.

Reuse DiagnosticEvent, LogRedactor, OmniBudsLogger. No competing
schemas; no cloud telemetry.
