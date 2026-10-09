# Phase 13 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Compilation
- Core main: 194 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 122 files (incl. 6 new quality test files) — clean.
- Android main: 37 files — clean.
- Android tests: 15 files (incl. bridge test) — clean.

## Tests
- Core: **926/926 passed** (49 new Phase 13 tests).
- Android: **139/139 passed** (5 new bridge tests).
- Total: **1065/1065**, 0 failures.

## Scope verification
- `AudioQualityScopeTest`: no quality scores, no subjective claims, no
  media-path vocabulary, no polling, no hidden APIs — all pass.
- `DependencyDirectionTest`: `quality` at layer 4 — passes.

## Known limitations
- Negotiation states are *derived* from observations, not protocol events
  (the platform exposes none). Documented, not hidden.
- Active codec remains NOT_OBSERVABLE on most Android versions (no public
  API); the engine reports UNKNOWN honestly.
- No Gradle/Lint (sandbox daemon IPC issue); manual kotlinc toolchain used.
