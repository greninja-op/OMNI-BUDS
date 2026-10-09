# Phase 31 — Regression Analysis

## Method

Compare campaign results against an immutable baseline:
source revision, profile versions, fixture versions, runner version,
expected categories per test.

## Detections

- NewFailure: passed → failed (regression).
- NewPass: failed → passed (improvement; investigate).
- NewSkipOrBlock: newly skipped/blocked.
- Unbaselined: no baseline entry.

## Policy

- Baselines never auto-updated.
- Deliberate expectation changes need documented decisions.
- Invalid baselines fail loudly.
- Infrastructure failures distinguished from product failures.
