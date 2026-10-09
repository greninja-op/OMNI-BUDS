# Phase 31 — Specifications

## DeviceProfile

profileId, schemaVersion (1), manufacturer?, model?, hardwareRevision?,
firmwareVersion?, transportKind?, protocolFamily?, protocolVersion?,
capabilities, evidence, fixtures, limitations?.

## Validation

Supported versions 1..1. Hardware-verified profiles must identify
manufacturer + model. Unknowns preserved.

## CampaignPlanner

Deterministic (sorted by id). Capability prerequisites via
`requires-capability:<id>` tags. Missing fixtures → BLOCKED.

## CompatibilityEvaluator

Classifications: VERIFIED_COMPATIBLE / SYNTHETIC_ONLY /
PARTIALLY_VALIDATED / INCOMPATIBLE / UNKNOWN / BLOCKED / NOT_APPLICABLE.
Scope string: manufacturer/model/fw/proto. Evidence mapped from
profile evidence.

## RegressionComparator

Baseline: id, sourceRevision, profile/fixture/runner versions,
expected categories. Findings: NewFailure / NewPass / NewSkipOrBlock /
Unbaselined. Baselines never auto-updated.
