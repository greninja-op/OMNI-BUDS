# Phase 26 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
285 core main + 162 core test files; 39 android main + 16 android test files.
No pre-existing failures.

## Compilation
- Core main: 285 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 162 files — clean (architecture tests updated).
- Android main: 53 files, API 35, `-Werror` — clean.
- Android tests: 24 files — clean.

## Tests
- Core: **1302/1302 passed**.
- Android: **212/212 passed** (40 new Phase 26 tests).
- Total: **1514/1514**, 0 failures.

## Issues found and fixed
1. **Smart-cast across modules** — `ObservedValue` properties can't be
   smart-cast across module boundaries. Bound to locals.
2. **Companion in standalone object** — moved `MAX_ACTIONS` to a const.
3. **Deprecated `Notification.Action.Builder`** — switched to the
   Icon-based constructor.
4. **Real engine shapes** — tests adapted to `ObservedValue`,
   `ObservationProvenance`, `IdentityState.Identified(4 args)`,
   `ConnectionState.Connected(3 args)`, `ProtocolState.Resolved(3 args)`,
   full `GlobalDeviceState` constructor.
5. **Architecture tests** — `neitherModuleReferencesUiFrameworks`
   (PendingIntent authorised for Phase 26),
   `platformBroadcastUseIsConfinedToListeningForAdapterState`
   (notification/ package authorised, PendingIntent removed from never
   list), `platformManifestDeclaresNothingUnjustified` (receiver earned,
   exported=false enforced),
   `platformSourcesLiveOnlyUnderTheAuthorisedPackages` (notification/
   package added).

## Environment notes
- No Gradle/Lint (sandbox limitation).
- Receiver lifecycle verified through unit-testable boundaries;
  framework-driven `onReceive` tested via the dispatcher contract.
