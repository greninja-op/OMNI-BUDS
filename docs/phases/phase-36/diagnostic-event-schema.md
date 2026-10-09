# Phase 36 — Diagnostic Event Schema

## Event (existing, reused)

`DiagnosticEvent`: timestampEpochMillis, severity, category,
message (pre-redaction), operationId?, error?.

## Export schema (new, version 1)

```json
{
  "schemaVersion": 1,
  "createdEpochMillis": 1234567890,
  "events": [
    {"seq": 42, "ts": 1234567890, "sev": "ERROR", "cat": "BLUETOOTH",
     "msg": "redacted text", "op": "op-1"}
  ]
}
```

- `op` omitted when null.
- Messages redacted at export.
- No address, device name, payload, or secret fields exist.

## Severity

TRACE/PACKET (opt-in), DEBUG, INFO, WARN, ERROR.

## Schema evolution

Version bump on any field change. Consumers must tolerate unknown
future fields by ignoring them.
