# Phase 34 — Specifications

## FailureClassification

- FailureCategory: 20 categories.
- ClassifiedFailure: code, category, deviceId?, sessionId?,
  operationId?, retryClass, mayHaveExecuted,
  reconciliationRequired, displayMessage, correlationId.
- FailureClassifier.retryClassFor: category → RetryClass.
- classify: builds a ClassifiedFailure with reconciliationRequired =
  (retryClass == RETRY_AFTER_REREAD || mayHaveExecuted).

## RecoveryPolicy

- RecoveryDecision: 11 values.
- RecoveryContext: failure, attemptCount, maxAttempts,
  bluetoothEnabled?, permissionGranted?, backgroundRestricted,
  cancelled, sessionSuperseded, authorizationValid.
- decide: deterministic; cancellation and supersession first;
  ambiguous writes reconcile; budget exhaustion → user intervention.

## RecoveryStateMachine

- 12 states; transition(to) returns Boolean; reset() only from
  terminal states.

## RecoveryEvents

- RecoveryEventType: 12 types.
- RecoveryEvent: eventId, correlationId, type, deviceId?,
  sessionId?, operationId?, attemptNumber?, elapsedMillis?,
  decision?.
- RecoveryEventSink(capacity=256): oldest-first eviction; never blocks.
