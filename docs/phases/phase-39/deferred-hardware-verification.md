# Phase 39 — Deferred Hardware Verification (Phase 52)

## Requirements

When Phase 52 runs, it must verify against a real device:

1. Actual device identification (fingerprint vs. expected).
2. Supported capability reads.
3. Safe feature operation (explicit consent only).
4. Command acknowledgement and read-back.
5. Disconnect/reconnect behavior.
6. Hardware persistence (only when safely testable).
7. Firmware compatibility.
8. Battery reporting where exposed.
9. Audio-path non-interference.
10. Evidence capture and redaction.

## Method

Use the Phase 38 HIL harness (`core/hil/`) and hardware profile
schema. Physical checks are already typed as
DEFERRED_TO_FINAL_HARDWARE_VERIFICATION.

## Constraints

- No verification without the device in hand.
- Simulated results never become hardware evidence.
- Consent and safety gates apply to every mutating operation.
