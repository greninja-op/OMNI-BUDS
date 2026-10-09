# Phase 31 — Cross-Device Test Matrix

## Dimensions

| Dimension | Variations |
|---|---|
| Device identity | manufacturer/model matching, fingerprint stability, ambiguous identity, similar models, hardware revisions |
| Firmware | known versions, version-scoped rules, capability changes, unknown firmware, out-of-scope firmware |
| Protocol | versions, framing variants, command availability, correlation, timeouts |
| Features | supported/unsupported, mode sets, read-only, volatile/persistent, dependency differences, confirmation semantics |
| Lifecycle | connect/disconnect, rediscovery, invalidation, freshness, recovery, cancellation |

## Profile categories

SYNTHETIC → FIXTURE_DERIVED → LAB_TESTED → HARDWARE_VERIFIED →
PERSISTENCE_VERIFIED. Never auto-promoted.

## Classification outcomes

Verified-compatible (scoped), synthetic-only, partial, incompatible,
unknown, blocked, not-applicable.
