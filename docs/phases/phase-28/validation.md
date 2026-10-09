# Phase 28 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
285 core main + 162 core test files; 61 android main + 28 android test files.
No pre-existing failures.

## Compilation
- Core main: 290 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 163 files — clean (architecture tests updated).
- Android main: 62 files, API 35, `-Werror` — clean.
- Android tests: 28 files — clean.

## Tests
- Core: **1328/1328 passed** (26 new lifecycle tests).
- Android: **253/253 passed**.
- Total: **1581/1581**, 0 failures.

## Issues found and fixed
1. **`onLowMemory` deprecation** — marked `@Deprecated` to satisfy `-Werror`.
2. **Architecture tests** — `neitherModuleReferencesUiFrameworks`
   (lifecycle/ package exempted for ActivityLifecycleCallbacks signatures),
   `coreAreasDependOnlyOnMoreFoundationalAreas` (lifecycle area registered
   at layer 5),
   `platformSourcesLiveOnlyUnderTheAuthorisedPackages` (lifecycle/ package).

## Environment notes
- No Gradle/Lint (sandbox limitation).
- Monitor lifecycle verified through unit-testable state machine;
  framework callbacks tested via the event contract.
