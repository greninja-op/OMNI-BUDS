# Phase 17 — Test Plan

## Repository (`ConfigurationEngineTest`)
Save/read/update/reset; NotFound; isolation; reset scoping; invalid
rejected; write failure; corruption → Invalid; future schema protected;
global round-trip; blank key refused; concurrent isolation.

## Codec (`ConfigurationCodecTest`)
All 9 ConfigurationValue types round-trip; malformed → null; unknown
type → null; config envelope round-trips.

## Validation (`ConfigurationValidationTest`)
Valid passes; too many prefs; oversized strings.

## Migration (`ConfigurationMigrationTest`)
Current version no-op; future throws; non-contiguous chain rejected.

## Eligibility (`ApplicationEligibilityTest`)
App-only eligible; unknown capability blocked; unsupported blocked.

## Scope (`ConfigurationScopeTest`)
No UI; no hardware claims; no hidden APIs; no secrets.

## Regression
Full suite: 1047 core + 142 android, 0 failures.
