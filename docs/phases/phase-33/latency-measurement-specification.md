# Phase 33 — Latency Measurement Specification

## Categories

COMMAND_DISPATCH, VENDOR_CONTROL_RESPONSE, STATE_OBSERVATION_DELAY,
CONNECTION_SETUP, CODEC_STATE_UPDATE. Never combined into one
"latency" value.

## Rules

- Monotonic clock only (System.nanoTime in production).
- Units: milliseconds, explicit.
- Start/end events defined per measurement.
- Missing start or end → incomplete (elapsedMillis null).
- Cancellation/timeout → incomplete, marked.
- Deterministic tests use FakeClock; no tight real-time thresholds.

## Limitations

Real end-to-end acoustic latency requires external measurement
equipment or a valid loopback arrangement. This framework never
claims it. No physical latency tests in this phase.
