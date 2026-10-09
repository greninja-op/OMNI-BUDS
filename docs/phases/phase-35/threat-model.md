# Phase 35 — Threat Model

## Method

STRIDE, grounded in actual components. Only threats with a real
entry point in this codebase are listed.

## Trust boundaries

1. Android system ↔ app code (permissions, intents, services).
2. Bluetooth stack ↔ transport (untrusted device data).
3. Imported traces/fixtures ↔ lab tools (untrusted files).
4. Per-device sessions ↔ each other (isolation).
5. Persistent storage ↔ runtime (tampering).

## Threats

### T-35-01: Unauthorized hardware write (Spoofing/Elevation)
- **Entry:** Notification/widget/tile action with forged extras.
- **Controls:** DeviceAccessPolicy default-deny; PendingIntents
  immutable with explicit device/session identity; recovery
  authorization gate.
- **Residual:** Low. **Verified:** existing action tests.

### T-35-02: Malformed protocol response (Tampering/DoS)
- **Entry:** Bluetooth transport bytes; imported traces.
- **Controls:** InputValidator bounds; typed parse failures; no raw
  payload logging.
- **Residual:** Low. **Verified:** InputValidatorTest.

### T-35-03: Cross-device state leakage (Information disclosure)
- **Entry:** Shared caches keyed incorrectly.
- **Controls:** Per-device keyed state; session ownership by explicit
  identifier; recovery per-device contexts.
- **Residual:** Low. **Verified:** DeviceIsolationSecurityTest.

### T-35-04: Stale session write (Tampering)
- **Entry:** Delayed callback after session supersession.
- **Controls:** RecoveryPolicy aborts superseded sessions; session
  identity revalidation.
- **Residual:** Low. **Verified:** Phase 34 tests.

### T-35-05: Oversized import DoS (Denial of service)
- **Entry:** Imported trace/fixture files.
- **Controls:** 16 MiB import bound; 64 KiB message bound.
- **Residual:** Low. **Verified:** InputValidatorTest.

### T-35-06: Sensitive data in logs (Information disclosure)
- **Entry:** Diagnostic messages with device identifiers.
- **Controls:** Sink-side redaction; LogRedactor pure boundary.
- **Residual:** Low. **Verified:** LogRedactorTest.

### T-35-07: Exported component abuse (Elevation)
- **Entry:** Tile service (exported, required by Android).
- **Controls:** BIND_QUICK_SETTINGS_TILE permission; receiver and
  provider exported=false.
- **Residual:** Low. **Verified:** manifest review.

### T-35-08: Dependency compromise (Supply chain)
- **Entry:** Maven Central artifacts.
- **Controls:** Pinned versions; minimal dependency set; no dynamic
  versions.
- **Residual:** Low-Medium (no vulnerability scanner in this
  environment). **Verified:** review only.

## Not claimed

No security certification. Residual risks above are honest
estimates, not guarantees.
