# Phase 18 — Specifications

## VerificationId
Value class; `new()` mints unique IDs; `of()` validates non-blank.

## VerificationStage (12)
CREATED, ELIGIBILITY_CHECK, BASELINE_CAPTURE, APPLY_REQUESTED,
APPLY_ACKNOWLEDGED, INITIAL_READ_BACK, SESSION_BOUNDARY_CHECK,
RECONNECT_CHECK, APPLICATION_RESTART_CHECK, DEVICE_POWER_CYCLE_CHECK,
EVALUATING_EVIDENCE, COMPLETED.

## VerificationOutcome (7)
VERIFIED, PARTIALLY_VERIFIED, NOT_VERIFIED, INCONCLUSIVE, UNSUPPORTED,
CANCELLED, FAILED. All terminal; no transition out.

## PersistenceScope (7, ordered)
SESSION_ONLY(0) < CONTROL_SESSION_PERSISTENT(1) < CONNECTION_PERSISTENT(2)
< APPLICATION_RESTART_PERSISTENT(3) < DEVICE_REBOOT_PERSISTENT(4)
< FIRMWARE_PERSISTENT(5); UNKNOWN(6) valid, never stronger.
`strongerThan()` implements ordering.

## ApplicationStatus (6)
NOT_ATTEMPTED, REQUESTED, ACKNOWLEDGED, READ_BACK_CONFIRMED, REJECTED, UNKNOWN.

## VerificationEvidence
evidenceId: String (non-blank), verificationId: VerificationId,
deviceKey: String (fingerprint-derived, non-blank),
targetKey: String, expectedValue: ConfigurationValue?,
observedValue: ConfigurationValue?, evidenceType: EvidenceType,
source: String, protocolVersion: String?, timestampMillis: Long (>=0),
sessionId: String?, connectionGeneration: Long?, isStale: Boolean,
correlationId: String?, limitations: List<String>.

## EvidenceType (10)
COMMAND_ACKNOWLEDGEMENT, DEVICE_READ_BACK, RECONNECT_READ_BACK,
RESTART_READ_BACK, POWER_CYCLE_READ_BACK, BASELINE_OBSERVATION,
LOCAL_PREFERENCE_STORED, LIFECYCLE_OBSERVATION, REJECTION, TIMEOUT_MARKER.

## VerificationPlan
targetFeature: FeatureId, expectedValue: ConfigurationValue,
requestedScope: PersistenceScope, stages: List<VerificationStage> (non-empty),
supportsReadBack/ReconnectCheck/RestartCheck/PowerCycleCheck: Boolean,
ackTimeoutMillis: Long (>0), readBackTimeoutMillis: Long (>0),
maxRetries: Int (>=0), retrySafe: Boolean.
Invariant: read-back stages require supportsReadBack.
`writeOnly()` factory for ack-only protocols.

## VerificationEvent (sealed)
EligibilityDetermined, BaselineCaptured, ApplyRequested, Acknowledged,
Rejected, ReadBackReceived, LifecycleBoundaryObserved,
PostBoundaryReadBack, TimedOut, Cancelled, DeviceDisconnected.

## LifecycleBoundary (4)
CONTROL_SESSION_END, DISCONNECT_RECONNECT, APPLICATION_RESTART,
DEVICE_POWER_CYCLE. Observed only, never manufactured.

## VerificationRecord
Immutable; terminal requires COMPLETED stage + completedAtMillis.
`create()` factory; `isTerminal` derived.

## State machine
Pure `transition(record, event, now) -> record`.
Timeout → INCONCLUSIVE. Disconnect after confirmation → unchanged.
Terminal → IllegalStateException on further events.

## ComparisonResult
Match / Mismatch(reason). Exact structural equality; extension point
for feature-specific normalization.

## EvidenceEvaluator
`evaluate(evidence, currentSessionId) -> Evaluation(strongestScope,
hasConflicts, reason)`. Stale/cross-session filtered. Local preference
ignored. Conflicts detected via distinct observed values.

## VerificationFailure (23)
Maps to OmniBudsErrorCategory. Covers device, protocol, capability,
read-back, scope, session, command, observation, conflict, config,
timeout, cancel, storage, schema, internal.

## VerificationStorage
read/write/delete String key-values. Bridge to Phase 17 storage.

## VerificationRepository
SCHEMA_VERSION=1. save/load/index/listForDevice/recoverInterrupted/delete.
Mutex-serialized. Corrupt → null.

## VerificationRecordCodec
Versioned JSON envelope. Wrong version → null. Never silent repair.
