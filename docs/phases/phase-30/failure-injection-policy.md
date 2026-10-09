# Phase 30 — Failure Injection Policy

## Rules

- Test-only interfaces; no production backdoors.
- Scoped to the current test; reset between tests.
- Seeded randomness only; seeds recorded.
- No uncontrolled sleeps; virtual clocks preferred.
- No real hardware writes in ordinary suites.
- No automatic replay of unsafe operations to make a test pass.

## Failure classification

- Transport-level: timeout, disconnect, malformed frame.
- Protocol-level: invalid sequence, unknown type, bad checksum.
- Application-level: rejected operation, stale plan.
- Platform-level: permission denied, adapter off.

## ScriptedTransport

Failures are scripted outcomes (Fail/Disconnect), not hidden hooks.
Exhausted scripts fail closed (TIMEOUT), never invent responses.
