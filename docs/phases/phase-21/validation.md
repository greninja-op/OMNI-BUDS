# Phase 21 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Baseline
138 core + 16 android test files; 254 core main files. No pre-existing failures.

## Compilation
- Core main: 261 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 144 files — clean.
- Android main + tests — clean (command TBD).

## Tests
- Core: **1177/1177 passed** (48 new Phase 21 tests).
- Android: **142/142 passed**.
- Total: **1319/1319**, 0 failures.

## Commands
- `kotlinc @main-sources -cp coroutines-core-jvm:1.9.0 -d out-main -jvm-target 17 -Werror` → clean
- `kotlinc @test-sources -cp ... -d out-test -jvm-target 17 -Werror` → clean
- `java -jar junit-platform-console-standalone-1.10.1.jar execute --scan-class-path out-test` → 1177/1177
- Android main compiled against API 35 (android.jar) with `-Werror` → clean
- Android tests → 142/142

## Issues found and fixed
1. **Over-strict diagnostics test** — the "no sensitive data" test flagged
   colons in reason-code text; fixed to check MAC/token/password patterns.

## Environment notes
- No Gradle/Lint (sandbox limitation).
