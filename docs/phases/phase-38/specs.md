# Phase 38 — Specifications

## HilEnvironment

6 values; involvesPhysicalHardware() false for SIMULATED/EMULATOR.

## HardwareProfile

- SUPPORTED_SCHEMA_VERSION = 1.
- Validator: rejects blank IDs, bad versions, blank appVersion/
  gitCommit, apiLevel outside 26–99, blank prerequisites.

## HilSafetyPolicy

- Defaults: physicalExecutionAllowed=false,
  hardwareWritesAllowed=false,
  allowlistedOperations={READ_ONLY_OBSERVATION}.
- Invariants enforced in init.

## HilSafetyGate

- decide(environment, category): Denied when physical and not
  allowed; denied when write and not allowed; denied when not
  allowlisted.
- authorizeMutatingOperation: 7 booleans; denies with missing list.

## HilRig / DryRunHilRig

- initialize/cleanup idempotent; records requestedOperations;
  performedPhysicalOperation flag.

## HilCampaign / HilCampaignExecutor

- 9 stages; typed StageOutcome (cancellation recorded); physical
  checks in non-physical environments deferred to Phase 52;
  report with counts() and clean flag.
