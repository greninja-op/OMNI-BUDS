# Phase 29 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
290 core main + 163 core test files; 62 android main + 28 android test files.
No pre-existing failures.

## Compilation
- Core main: 295 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 164 files — clean (architecture tests unchanged; the new
  `feature/dependency/` subpackage is intra-area).
- Android main: 62 files, API 35, `-Werror` — clean.
- Android tests: 28 files — clean.

## Tests
- Core: **1351/1351 passed** (23 new dependency tests).
- Android: **253/253 passed**.
- Total: **1604/1604**, 0 failures.

## Issues found and fixed
1. **FeatureRelation field names** — RequiresOneOf uses `options`,
   ConflictsWith uses `other` + `reason`, MutuallyExclusive uses
   `feature`/`others` + `reason`.
2. **Layer map** — the new code lives in `feature/dependency/` (area
   `feature`, layer 5); no sideways imports, no test changes needed.
3. **Stale build classes** — cleaned `/tmp/p29build` after the package
   move to avoid double-counted tests.

## Environment notes
- No Gradle/Lint (sandbox limitation).
- No property-based testing library in the repo; equivalent
  deterministic tests implemented (determinism, invariants).
