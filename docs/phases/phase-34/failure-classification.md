# Phase 34 — Failure Classification

## Categories (20)

PERMISSION_DENIED, BLUETOOTH_DISABLED, BLUETOOTH_UNAVAILABLE,
DEVICE_DISCONNECTED, CONNECTION_TIMEOUT, TRANSPORT_UNAVAILABLE,
TRANSPORT_FAILURE, PROTOCOL_TIMEOUT, PROTOCOL_MALFORMED_RESPONSE,
PROTOCOL_UNSUPPORTED, DEVICE_BUSY, OPERATION_REJECTED,
OPERATION_OUTCOME_UNKNOWN, CAPABILITY_UNAVAILABLE, STALE_STATE,
PERSISTENCE_FAILURE, RESOURCE_EXHAUSTED, CANCELLED,
BACKGROUND_RESTRICTED, INTERNAL_ERROR.

## Default retry classes

- SAFE_TO_RETRY: TRANSPORT_FAILURE, CONNECTION_TIMEOUT,
  PROTOCOL_TIMEOUT, DEVICE_BUSY, RESOURCE_EXHAUSTED.
- RETRY_AFTER_REREAD: OPERATION_OUTCOME_UNKNOWN, STALE_STATE.
- NEVER_RETRY: everything else.

## Metadata

code, category, deviceId?, sessionId?, operationId?, retryClass,
mayHaveExecuted, reconciliationRequired, displayMessage,
correlationId. No raw payloads, secrets, or credentials.

## Rules

- Cancellation is cancellation.
- Malformed ≠ unsupported.
- Rejected ≠ unknown outcome.
- Timeout ≠ unsupported hardware.
- Permission error ≠ disconnection.
- Retry eligibility never inferred from "an exception occurred".
