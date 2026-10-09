# Phase 36 — Redaction Policy

## Where redaction happens

1. **Sink emission** (existing): OmniBudsLogger implementations redact
   at the point of emission.
2. **Export** (new): DiagnosticExporter redacts via LogRedactor.
3. **Pure boundary** (Phase 35): LogRedactor for tests and callers.

## What is redacted

- Bluetooth MAC addresses.
- Token/secret/password patterns.
- (Existing sink contract): device names bound to addresses,
  manufacturer data.

## Known gap

Free-text device names not matching MAC patterns are not caught by
the pure redactor. The sink-side contract ("do not put it in")
remains the primary control. Do not claim perfect redaction.

## Failure behavior

Redaction never throws; on failure the event is dropped or replaced
with a safe placeholder — never stored raw.
