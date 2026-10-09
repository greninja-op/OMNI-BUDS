# Phase 30 — Specifications

## TestCase

id (stable), name, category, requirementRefs, preconditions, fixtures,
timeoutMillis (>0), tags, determinism.

## Fixtures

id, schema, version ≥1, provenance (SYNTHETIC/DOCUMENTED/REAL_CAPTURE),
payload. Validation: known schema, supported version, consent for real
captures. Invalid → fail before execution.

## ScriptedTransport

Implements TransportContract. Scripted outcomes in order: Respond, Fail,
Disconnect. Exhausted script → TIMEOUT (fail closed). Closed channel →
DEVICE_DISCONNECTED. Exchanges recorded for assertions.

## FailureInjector

seed; shouldFail(probability, label); injectedFailures; reset().
Deterministic per seed.

## TestResult / TestReport

Categories: PASSED/FAILED/SKIPPED/BLOCKED/CANCELLED/INVALID_DEFINITION/
INFRASTRUCTURE_ERROR. Evidence: SIMULATED/DOCUMENTED/HARDWARE_OBSERVED.
allPassed requires every result PASSED. Diagnostics ≤2000 chars.
