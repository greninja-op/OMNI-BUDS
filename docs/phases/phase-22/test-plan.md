# Phase 22 — Test Plan

## Model (`KnowledgeModelTest`, 12 tests)
Manufacturer brand/legal distinction; blank-name rejection; sibling models
not merged; explicit unknown firmware; null field meaning; record version
invariant; synthetic-evidence ceiling; contradicting evidence preserved;
codec round-trips; malformed rejection; deterministic encoding.

## Repository (`KnowledgeRepositoryTest`, 6 tests)
Put/get; update; deterministic ordering; save/load round-trip; corrupt
record blocks load and is reported; save failure reported.

## Query (`KnowledgeQueryTest`, 8 tests)
Manufacturer alias lookup; ambiguity-preserving model candidates;
protocol resolution with firmware; unknown-firmware safety; incomplete
firmware detection; affected-by-change; pagination; unverified claims.

## Import/export (`KnowledgeImportExportTest`, 9 tests)
Deterministic export; valid package; malformed/unsupported-version/
unresolved-reference/duplicate-ID rejection; import applies; failed import
writes nothing; oversized rejected.

## Lifecycle (`KnowledgeLifecycleTest`, 6 tests)
Legal/illegal transitions; superseded terminal; activation guards;
event bus.

## Security (`KnowledgeSecurityTest`, 4 tests)
No write/execute surface on queries; unknown fields tolerated without
execution; deep nesting rejected.

## Quality (`KnowledgeQualityTest`, 4 tests)
Duplicate aliases; orphaned references; cyclic dependencies; conflicting
identities.

## Regression
Full suite: core + android, 0 failures.
