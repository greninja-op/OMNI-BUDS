# Phase 30 — Test Evidence Model

## Result fields

testId, suiteId, category, requirementRefs, fixtureVersion,
assertionsPassed/Failed, errorCategory, diagnostics (≤2000 chars),
durationMillis, determinism, evidenceLevel, timestampMillis.

## Categories

PASSED, FAILED, SKIPPED, BLOCKED, CANCELLED, INVALID_DEFINITION,
INFRASTRUCTURE_ERROR. Skipped/blocked are never passes.

## Evidence levels

- SIMULATED: logic validated against scripted/fake inputs.
- DOCUMENTED: validated against specifications.
- HARDWARE_OBSERVED: validated against real hardware (future).

A SIMULATED pass never upgrades a capability's verification status.

## Reporting

TestReport.summary() lists non-passing results with error categories.
Reports carry no credentials or raw payloads.
