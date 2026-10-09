# Phase 27 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
285 core main + 162 core test files; 53 android main + 20 android test files.
No pre-existing failures.

## Compilation
- Core main: 285 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 162 files — clean (architecture tests updated).
- Android main: 61 files, API 35, `-Werror` — clean.
- Android tests: 28 files — clean.
- Resources: aapt2 compile+link clean; R.java generated from XML.

## Tests
- Core: **1302/1302 passed**.
- Android: **253/253 passed** (41 new Phase 27 tests).
- Total: **1555/1555**, 0 failures.

## Issues found and fixed
1. **Companion in standalone object** — moved `MAX_ACTIONS` to a const.
2. **R import** — widget code imports `com.omnibuds.android.R`.
3. **Coordinator reconcile arity** — failure path records without reconcile.
4. **Architecture tests** — `platformModuleContainsNoUnauthorisedCapabilities`
   (AppWidgetProvider authorised for Phase 27),
   `platformManifestDeclaresNothingUnjustified` (widget provider earned,
   exported=false enforced),
   `platformBroadcastUseIsConfinedToListeningForAdapterState` (widget/
   package authorised),
   `platformSourcesLiveOnlyUnderTheAuthorisedPackages` (widget/ package).

## Environment notes
- No Gradle/Lint (sandbox limitation).
- Provider lifecycle verified through unit-testable boundaries.
