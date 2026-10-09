# Phase 21 — Device Classification

## Deterministic rules

`DeviceClassifier.classify(fingerprint, matchedAdapters, ambiguous,
protocolVerified)`:

1. `ambiguous == true` → AMBIGUOUS_IDENTITY. Never overridden by matches.
2. Fingerprint entirely unobserved → UNKNOWN_DEVICE.
3. `matchedAdapters.size > 1` → AMBIGUOUS_IDENTITY (safest).
4. No matches + identity signals → PARTIALLY_IDENTIFIED.
5. No matches + no signals → UNKNOWN_DEVICE.
6. Exactly one match + protocolVerified → KNOWN_DEVICE_SUPPORTED.
7. Exactly one match + !protocolVerified → KNOWN_PROTOCOL_UNVERIFIED.

## Identity evidence and access

| Classification | Identity confidence | Writes |
|---|---|---|
| UNKNOWN_DEVICE | NONE/LOW | denied |
| PARTIALLY_IDENTIFIED | MEDIUM | denied |
| IDENTIFIED_UNSUPPORTED | HIGH | denied |
| AMBIGUOUS_IDENTITY | LOW | denied |
| KNOWN_PROTOCOL_UNVERIFIED | HIGH | denied |
| KNOWN_PROTOCOL_SUPPORTED | HIGH | per-capability |
| KNOWN_DEVICE_SUPPORTED | HIGH | per-capability |

Write authorization additionally requires: verified protocol,
capability-level write verification, firmware compatibility, fresh evidence.

## Staleness

Stale evidence is discarded, never reused. Disconnect and staleness
downgrade to UNKNOWN_DEVICE. Ambiguity is sticky across re-classification
until explicitly resolved with adequate evidence.
