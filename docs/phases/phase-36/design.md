# Phase 36 — Design

## Modules (all in `core/diagnostics/`)

- `DiagnosticStore` — bounded ring buffer + retention.
- `DiagnosticHealth` / `DiagnosticHealthTracker` — self-observability.
- `DiagnosticExporter` — safe local export.

## Reuse

DiagnosticEvent, DiagnosticSeverity, DiagnosticCategory,
OmniBudsLogger seam, LogRedactor. No competing schemas.

## Data flow

Event → store.record (validate/truncate) → sink/health/export.
Export: stored events → redact → versioned JSON.

## Concurrency

Store and tracker are synchronized; exporter is pure.

## Security boundaries

- Redaction before export and at sink emission.
- No telemetry, no uploads, no audio.
- Health never generates events (no recursion).
