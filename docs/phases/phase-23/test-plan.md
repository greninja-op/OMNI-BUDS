# Phase 23 — Test Plan

## Contract (`ExtensionContractTest`, 6 tests)
Valid/invalid feature IDs; ID from parts; extension ID validation;
descriptor requirements; trust-level ordering.

## Schema (`FeatureSchemaTest`, 10 tests)
Int bounds/step; type coercion rejection; enum constraints; structured
required/allowed fields; list bounds; default-value validation; float
bounds.

## Registry (`ExtensionRegistryTest`, 11 tests)
Valid registration; duplicates; DISABLED rejection; circular extension
deps; feature ownership; duplicate features; namespace declaration;
circular feature deps; candidate resolution; deprecation; unknown lookup.

## Compatibility (`CompatibilityTest`, 10 tests)
Exact model; sibling rejection; manufacturer mismatch; unknown firmware;
firmware mismatch; protocol/transport mismatch; ambiguous identity flag;
inactive extension; reason+evidence on all results.

## Dependencies (`DependencyTest`, 4 tests)
Satisfied; missing prerequisites; unknown deps; conflicts.

## Execution (`ExecutionContractTest`, 5 tests)
Timeout validation; typed read results; ambiguous write outcomes;
read-back mismatch; explicit ambiguity.

## Security (`ExtensionSecurityTest`, 5 tests)
No transport handles; metadata ≠ authorization; oversized lists;
identifier injection; no dynamic code.

## Regression
Full suite: core + android, 0 failures.
