# Phase 35 — Design

## Modules

`core/security/`:

- `InputValidator` — bounded validation, overflow-safe arithmetic.
- `LogRedactor` — pure redaction boundary.

## Reuse

DeviceAccessPolicy (authorization), OmniBudsLogger (sink redaction),
RecoveryPolicy (auth gate), InputValidator used by future parser
hardening. No competing authorization systems.

## Data flow

Untrusted input → validate → typed Valid/Invalid → safe failure.
Diagnostic text → redact → sink.

## Concurrency

Both objects are stateless and thread-safe.

## Security boundaries

- Validation before allocation.
- Redaction before persistence/export.
- No secrets in the model.
