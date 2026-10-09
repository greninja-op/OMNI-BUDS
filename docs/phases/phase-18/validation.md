# Phase 18 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Compilation
- Core main: 242 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 133 files — clean.
- Android main + tests — clean.

## Tests
- Core: **1092/1092 passed** (45 new Phase 18 tests).
- Android: **142/142 passed**.
- Total: **1234/1234**, 0 failures.

## Issues found and fixed
1. **Scope ordering bug** — `strongerThan()` used `<` instead of `>`; fixed.
2. **Scope test false positive** — "decode(" banned term hit JSON codec; narrowed.
3. **Sideways layer import** — `VerificationRecordCodec` imported from
   `configuration` (5). Fixed by moving `ConfigurationValueJson` to
   `core.config` (layer 2).

## Known limitations
- State machine is event-driven; actual port driving is caller's responsibility.
- Evidence history is bounded (count persisted, not full items).
- No Gradle/Lint (sandbox limitation).
