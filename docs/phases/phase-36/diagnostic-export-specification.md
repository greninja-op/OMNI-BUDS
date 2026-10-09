# Phase 36 — Diagnostic Export Specification

## Format

Versioned JSON (schemaVersion 1). See diagnostic-event-schema.md.

## Safety rules

- Explicit invocation only; never automatic.
- Never uploaded anywhere.
- Redacted messages only.
- No payloads, secrets, addresses, or device names.
- Max 512 events, 256 KiB total.
- Newest events first; stops at the cap.
- Empty store → refused export (not an empty file).
- Oversized single events truncated at the store layer.

## Not implemented

File writing and sharing UI are out of scope; the exporter produces
the content string. The caller owns safe file handling.
