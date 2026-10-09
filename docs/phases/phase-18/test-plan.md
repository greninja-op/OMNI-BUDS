# Phase 18 — Test Plan

## Domain (`VerificationModelTest`, 8 tests)
ID uniqueness, blank rejection, stage/outcome separation, scope ordering,
status count, record creation, terminal invariants, plan validation.

## State machine (`VerificationStateMachineTest`, 12 tests)
Happy path; ineligible→UNSUPPORTED; rejected→FAILED; mismatch→NOT_VERIFIED;
timeout→INCONCLUSIVE; disconnect→INCONCLUSIVE; disconnect after confirm;
reconnect match→CONNECTION_PERSISTENT; reconnect mismatch→NOT_VERIFIED;
cancel; terminal immutability; malformed read-back.

## Evaluator (`EvidenceEvaluatorTest`, 10 tests)
Empty→UNKNOWN; local pref ignored; ack→SESSION_ONLY; read-back→SESSION_ONLY;
reconnect→CONNECTION; stale filtered; cross-session filtered; conflicts
flagged; power-cycle→REBOOT.

## Repository (`VerificationRepositoryTest`, 8 tests)
Round-trip; missing→null; corrupt→null; device index; recovery filters
terminal; delete; schema rejection; terminal codec round-trip.

## Scope (`VerificationScopeTest`, 6 tests)
No UI; no audio; no vendor commands; no hidden APIs; no sideways imports;
no fabrication vocabulary.

## Regression
Full suite: 1092 core + 142 android, 0 failures.
