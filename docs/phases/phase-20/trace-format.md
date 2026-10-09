# Phase 20 — Trace Format

## Version 1

A trace is a JSON-serializable record (serialization not yet implemented;
the domain model is defined):

```json
{
  "traceId": "string",
  "formatVersion": 1,
  "createdAtMillis": 0,
  "sourceType": "SYNTHETIC_FIXTURE",
  "transport": "RFCOMM",
  "protocolId": null,
  "events": [
    {
      "eventId": "e1",
      "sequence": 0,
      "relativeMillis": 0,
      "direction": "HOST_TO_DEVICE",
      "category": "command",
      "payloadHex": "00030102",
      "declaredLength": 3,
      "correlationId": "c1",
      "redacted": false
    }
  ],
  "redaction": { "redactedFields": [], "structureAltered": false },
  "provenance": "synthetic test fixture"
}
```

## Validation rules

- `formatVersion` must equal 1 (only supported version).
- Event IDs unique; sequences strictly increasing.
- `declaredLength` must match actual payload length when both present.
- Payloads ≤ 64 KiB; events ≤ 10,000; total file ≤ 4 MiB.
- Redaction: `structureAltered=true` requires non-empty `redactedFields`.

## Compatibility

Unknown future versions are rejected. Migration defined only when a
transformation is specified and tested (none yet — v1 is the only version).
