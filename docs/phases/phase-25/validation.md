# Phase 25 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
162 core + 16 android test files; 285 core main + 39 android main files.
No pre-existing failures.

## Compilation
- Core main: 285 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 162 files — clean (includes updated manifest test).
- Android main: 45 files, API 35, `-Werror` — clean.
- Android tests: 20 files — clean.

## Tests
- Core: **1302/1302 passed**.
- Android: **172/172 passed** (30 new Phase 25 tests).
- Total: **1474/1474**, 0 failures.

## Issues found and fixed
1. **`requestListeningState` signature** — API 35 takes
   `(Context, ComponentName)`, not `(Context, Class)`. Fixed.
2. **Manifest test** — updated `platformManifestDeclaresNothingUnjustified`
   to earn exactly the TileService with Phase 25 justification.
3. **Capability scan** — removed `TileService` from the forbidden list in
   `platformModuleContainsNoUnauthorisedCapabilities` (Phase 25 authorizes
   it).
4. **Package authorization** — added `com/omnibuds/android/tile/` to the
   allowed roots in `platformSourcesLiveOnlyUnderTheAuthorisedPackages`.

## Environment notes
- No Gradle/Lint (sandbox limitation).
- TileService lifecycle verified through unit-testable boundaries;
  framework-driven callbacks not directly unit-testable.
