# Phase 14 — Test Plan

## Model (§21)
`ValidationModelTest`: 7 statuses, 4 severities, 7 categories; INVALID≠INFO;
INCONCLUSIVE≠INVALID; blank id/reason rejected.

## Aggregation (§21H)
`AggregationPolicyTest`: all-valid→VALID; hard violation→INVALID;
warning→INCONCLUSIVE; conflict visible; stale reported; valid+unknown→
INCONCLUSIVE.

## Rules (§21A–G)
`ValidationRuleTest`: 24 cases covering device association, transport,
codec (LC3/LE Audio, LC3-as-A2DP, LDAC/LE Audio, AAC/HFP), route,
parameters (domain, uncommon-legal, missing, impossible), freshness.

## Engine (§21I–J)
`ValidationEngineTest`: snapshot production; obsolete generation
discarded; dedup; multi-device isolation; LC3/LE Audio; coherence.

## Scope (§21K)
`ValidationScopeTest`: no signal-path claims, no media interception, no
polling, no scores, no hidden APIs.
