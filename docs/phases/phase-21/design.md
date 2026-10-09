# Phase 21 — Design

## Architecture

New package `com.omnibuds.core.access` at layer 5:

- **DeviceClassification.kt** — `DeviceClassification` (7 states),
  `IdentityConfidence`, `DeviceAccessState` (three separate facts:
  classification, confidence, protocolVerified, writeAuthorized).
- **DeviceClassifier.kt** — deterministic pure function of fingerprint +
  registry state. Ambiguity is sticky and never resolved by picking a candidate.
- **DeviceAccessPolicy.kt** — centralized default-deny evaluation at the
  domain/control boundary. `evaluate(state, operation, capabilityId,
  capabilityWriteVerified, firmwareCompatible, evidenceFresh)`.
- **CapabilityAccess.kt** — `CapabilityAccess` (support vs access split),
  `CapabilityAccessRecord`, `CapabilityAccessEvaluator`.
- **ObservationEngine.kt** — read-only observations with full metadata.
  Unknown values stay null.
- **DeviceLifecycle.kt** — per-device lifecycle manager, deterministic
  transitions, stale-evidence invalidation, in-flight cancellation signals.
- **AccessDiagnostics.kt** — bounded, privacy-conscious reason-code logging.

## Device classification

Seven states (UNKNOWN_DEVICE → KNOWN_DEVICE_SUPPORTED). Identity
confidence, protocol support, and write authorization are separate fields
in `DeviceAccessState`; a write grant requires a supported classification
*and* a verified protocol (enforced by the `init` block).

## State transitions

`DeviceLifecycleManager` handles: identity evidence arrival, conflicting
evidence (→ AMBIGUOUS_IDENTITY, sticky), evidence staleness (→ UNKNOWN,
never reused), protocol registration changes (invalidation revokes),
version changes (revoke until re-verified), disconnect (→ UNKNOWN).

Every transition returns whether in-flight restricted operations must be
cancelled and whether re-evaluation is required. Previously denied writes
are never auto-executed when a device becomes identified.

## Transport boundaries

The access package holds no transport handles. It never imports `feature`
or `protocol`. Observation sources are Android public APIs, verified
vendor read-back, manufacturer metadata, local information, and inference
(lowest confidence).

## Capability restrictions

Support (`CapabilityState`) and access (`CapabilityAccess`) are separate.
`CapabilityAccessRecord.init` enforces: READ_WRITE requires a verified
write path; UNKNOWN support cannot carry a positive grant; UNSUPPORTED
cannot be writable.

## Data flow

Fingerprint → Classifier → DeviceAccessState → AccessPolicy.evaluate →
AccessDecision (typed). Observations flow through ObservationEngine with
freshness/confidence metadata. Lifecycle events mutate per-device state
and emit cancellation signals.

## Security

Default-deny. Raw transport writes, firmware updates, and config resets
are hard-denied regardless of classification. Typed denials carry no
sensitive payloads. Diagnostics bounded at 256 events, address-free.

## Concurrency

Per-device state maps; no shared mutable state across devices. Lifecycle
events are handled sequentially per device. Cancellation is signalled,
not forced — callers must honor `cancelInFlight`.

## Error handling

Denials are structured (`AccessDecision`), never exceptions. Malformed
observations are recorded as UNAVAILABLE with a limitation, never
fabricated.

## Extension points

- New operation categories: add to `OperationCategory` + rule in policy.
- New denial reasons: add to `DenialReason`.
- New observation sources: add to `ObservationSource`.
