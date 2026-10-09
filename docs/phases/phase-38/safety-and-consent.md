# Phase 38 — Safety and Consent

## Defaults (executable)

- Physical execution: disabled.
- Hardware writes: disabled.
- Allowlist: read-only observations only.
- Firmware update / factory reset: never routine.

## Mutating operations require

1. Supported device and capability.
2. Verified protocol and operation compatibility.
3. Explicit operator consent.
4. Rollback or safe recovery strategy.
5. Bounded timeout.
6. Read-back or reconciliation strategy.
7. Auditable evidence record.

All seven are checked in `HilSafetyGate.authorizeMutatingOperation`.

## Invariants

- Hardware writes require physical execution.
- Unknown devices stay read-only.
- No unattended destructive operations.
- Safety is code, not just documentation.
