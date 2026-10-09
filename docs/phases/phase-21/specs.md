# Phase 21 — Specifications

## Classification model

`DeviceClassification`: UNKNOWN_DEVICE, PARTIALLY_IDENTIFIED,
IDENTIFIED_UNSUPPORTED, AMBIGUOUS_IDENTITY, KNOWN_PROTOCOL_UNVERIFIED,
KNOWN_PROTOCOL_SUPPORTED, KNOWN_DEVICE_SUPPORTED.

`DeviceAccessState(classification, identityConfidence, protocolVerified,
writeAuthorized)`. Invariant: writeAuthorized ⇒ supported classification
∧ protocolVerified.

## Read-only policy

`OperationCategory`: 11 categories. Read-only: discovery, identity,
connection, metadata, parsing, capability inspection, hardware read.
Write: hardware write, config reset, firmware update, raw transport write.

Rules:
- Read-only operations allowed when evidence is fresh; stale → deny.
- Writes: default-deny. Raw writes, firmware updates, config resets are
  hard-denied always.
- Unknown/partial → UNKNOWN_DEVICE_WRITE_DENIED.
- Ambiguous → AMBIGUOUS_IDENTITY_WRITE_DENIED.
- Identified-unsupported → UNSUPPORTED_DEVICE_WRITE_DENIED.
- Unverified protocol → UNVERIFIED_PROTOCOL_WRITE_DENIED.
- Supported device → allowed only with `capabilityWriteVerified`.
- Firmware incompatible → FIRMWARE_INCOMPATIBLE.
- Stale evidence → STALE_EVIDENCE (fail-closed).

## Capability access rules

`CapabilityAccess`: UNKNOWN, UNSUPPORTED, READ_ONLY, READ_WRITE, WRITE_ONLY.
`CapabilityAccessRecord` init invariants enforce rules 1, 9, and the
unsupported-not-writable constraint.

## Observation contracts

`DeviceObservation`: observationId, deviceKey (address-free), propertyId?,
value (null = unknown), source, observedAtMillis, freshness, confidence,
channel?, sessionId?, limitations.

Value types: Text, Number, BooleanValue, EnumValue. No fabricated defaults.

## Transition rules

| Event | Result |
|---|---|
| IdentityEvidenceArrived | re-classify; preserve ambiguity |
| ConflictingEvidence | → AMBIGUOUS_IDENTITY; cancel in-flight |
| EvidenceStale | → UNKNOWN_DEVICE; cancel in-flight |
| ProtocolRegistrationChanged(verified=false) | revoke protocolVerified/writeAuthorized; cancel |
| VersionChanged | revoke protocolVerified/writeAuthorized; cancel |
| Disconnected | → UNKNOWN_DEVICE; cancel |

## Errors

`DenialReason`: 17 typed codes. `AccessDecision` carries classification,
operation, capabilityId, missingEvidence, isReadOnly, reEvaluateAfterChange.

## Timeouts

Evidence freshness is caller-supplied (`evidenceFresh`); the policy does
not define timeouts — the session layer owns freshness policy.

## Concurrency semantics

Per-device maps; events handled sequentially per device; ambiguity sticky;
state never shared across devices.
