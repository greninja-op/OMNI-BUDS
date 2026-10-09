# Phase 34 — Requirements

**ID scheme:** `A34-REQ-001` … `A34-REQ-016`.

## A34-REQ-001 — Failure classification model
- **Description:** Typed `FailureCategory` (20 categories) with
  `ClassifiedFailure` metadata and `FailureClassifier` mapping to
  existing `RetryClass`.
- **Rationale:** Distinct failures need distinct handling.
- **Dependencies:** `core/common/RetryClass`. **Priority:** Must.
- **Acceptance:** All categories mapped; boundaries tested.
- **Verification:** FailureClassifierTest. **Status:** VERIFIED.

## A34-REQ-002 — No false inferences
- **Description:** Timeouts never imply unsupported hardware;
  permission errors never imply disconnection.
- **Rationale:** Honest state.
- **Dependencies:** A34-REQ-001. **Priority:** Must.
- **Acceptance:** Boundary tests pass.
- **Verification:** FailureClassifierTest. **Status:** VERIFIED.

## A34-REQ-003 — Recovery policy engine
- **Description:** Deterministic `RecoveryPolicy.decide(context)` →
  11 decisions, explicit inputs only.
- **Rationale:** Predictable recovery.
- **Dependencies:** A34-REQ-001. **Priority:** Must.
- **Acceptance:** All rules tested.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-004 — Bounded retries
- **Description:** attemptCount/maxAttempts in context; exhaustion →
  REQUIRE_USER_INTERVENTION.
- **Rationale:** No endless loops.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Exhaustion test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-005 — Ambiguous-write protection
- **Description:** mayHaveExecuted + non-idempotent → RECONCILE, never
  blind replay.
- **Rationale:** Prevent duplicated hardware writes.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-006 — Cancellation wins
- **Description:** Cancelled context or CANCELLED category → ABORT.
- **Rationale:** Cancellation is cancellation.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-007 — Superseded sessions
- **Description:** Superseded session → ABORT; stale callbacks cannot
  mutate current state.
- **Rationale:** Session isolation.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-008 — Recovery state machine
- **Description:** 12-state machine with guarded transitions;
  terminal states only reset to IDLE.
- **Rationale:** Explicit lifecycle.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Transition tests pass.
- **Verification:** RecoveryStateMachineTest. **Status:** VERIFIED.

## A34-REQ-009 — Adapter/permission recovery
- **Description:** Bluetooth off → WAIT_FOR_ADAPTER; permission denied →
  REVALIDATE_PERMISSIONS; no reconnect while off.
- **Rationale:** Respect platform state.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Tests pass.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-010 — Background restrictions
- **Description:** backgroundRestricted → MARK_SESSION_UNAVAILABLE.
- **Rationale:** No forbidden background work.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-011 — Diagnostic events
- **Description:** Bounded (256) RecoveryEventSink; no payloads/secrets;
  never blocks recovery.
- **Rationale:** Observable, privacy-conscious.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Bound and privacy tests pass.
- **Verification:** RecoveryEventSinkTest. **Status:** VERIFIED.

## A34-REQ-012 — Authorization revalidation
- **Description:** Retry requires authorizationValid; otherwise ABORT.
- **Rationale:** Recovery never bypasses capability policy.
- **Dependencies:** A34-REQ-003. **Priority:** Must.
- **Acceptance:** Test passes.
- **Verification:** RecoveryPolicyTest. **Status:** VERIFIED.

## A34-REQ-013 — Reuse, not duplication
- **Description:** Reuses RetryClass, ReconnectPolicy, FailureInjector;
  no competing state machines or retry loops.
- **Rationale:** One recovery model.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Code review.
- **Verification:** Review. **Status:** VERIFIED.

## A34-REQ-014 — Documentation
- **Description:** All 14 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A34-REQ-015 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.

## A34-REQ-016 — No hardware claims
- **Description:** No physical-device testing; no new capabilities.
- **Rationale:** Scope.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Nothing added.
- **Verification:** Review. **Status:** VERIFIED.
