# Phase 31 — Design

## Modules

`core/testkit/crossdevice/` (intra-area of testkit):

- `DeviceProfile` — versioned profile schema + validator.
- `CampaignPlanner` — deterministic test selection.
- `CompatibilityEvaluator` — evidence-aware classification.
- `RegressionComparator` — baseline comparison.

## Data flow

Profiles (validated) → planner → Phase 30 runner → results →
evaluator → verdicts; results vs baseline → regression findings.

## Lifecycle

Profiles versioned; baselines immutable and versioned; campaigns
repeatable.

## Concurrency

Planning is pure; execution reuses Phase 30 isolation.

## Security boundaries

- No fabricated profiles; synthetic marked.
- No cross-profile leakage.
- Reports carry no sensitive data.
