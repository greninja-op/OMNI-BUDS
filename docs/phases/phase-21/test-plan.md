# Phase 21 — Test Plan

## Classification (`DeviceClassificationTest`, 9 tests)
Unknown, partially identified, ambiguous, multi-match ambiguity, known
unverified, known supported, determinism, write-auth invariant, confidence
separation.

## Access policy (`AccessPolicyTest`, 14 tests)
Unknown/ambiguous/unsupported/unverified write denial; read-only write
denial without verified path; raw/config-reset/firmware hard denials;
firmware incompatibility; stale evidence; read≠write; unknown observations
allowed; typed denial content; verified write allowed.

## Observation (`ObservationEngineTest`, 6 tests)
Valid metadata; unknown battery/ANC/codec stay null; staleness on
disconnect; multi-device isolation.

## Capability (`CapabilityStateTest`, 7 tests)
Unknown preserved; unknown-support access invariant; read-only vs
writable; write-path requirement; unsupported-not-writable; verified
write; firmware incompatibility.

## Lifecycle (`LifecycleTest`, 8 tests)
Unknown→partial; conflicting→ambiguous+cancel; stale→unknown;
protocol invalidation; version change; disconnect; device isolation;
out-of-order evidence determinism.

## Diagnostics (`DiagnosticsTest`, 4 tests)
Classification events; denial reason codes; no sensitive data; bounded
emission.

## Regression
Full suite: core + android, 0 failures.
