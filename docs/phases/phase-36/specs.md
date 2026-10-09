# Phase 36 — Specifications

## DiagnosticStore

- capacity (default 512); MAX_MESSAGE_CHARS = 2048.
- StoredEvent(event, sequence).
- record(event): Boolean — validates, truncates, evicts oldest,
  counts dropped/invalid (saturating).
- snapshot(): List<StoredEvent>; snapshot(severity) filters.
- clear() resets.

## DiagnosticHealth

- DiagnosticHealth(sinkAvailable, saturation, droppedEvents,
  invalidEvents, persistenceFailures, lastPersistenceEpochMillis).
- isDegraded: !sinkAvailable || persistenceFailures > 0 || saturation >= 1.0.
- DiagnosticHealthTracker: synchronized record/set/health.

## DiagnosticExporter

- SCHEMA_VERSION = 1; MAX_EXPORT_CHARS = 256 KiB;
  MAX_EXPORT_EVENTS = 512.
- export(stored, createdEpochMillis): Exported(content, eventCount) |
  Refused(reason).
- Newest first; redacted; stops at caps.
