# Phase 17 — Validation

**Date:** 2026-10-09
**Result:** PASS

## Compilation
- Core main: 230 files, kotlinc 2.0.21, JVM 17, `-Werror` — clean.
- Core tests: 129 files — clean.
- Android: 39 main + 16 test files — clean.

## Tests
- Core: **1047/1047 passed** (28 new Phase 17 tests).
- Android: **142/142 passed**.
- Total: **1189/1189**, 0 failures.

## Issues found and fixed during validation
1. **StructuredValue JSON round-trip failed** — parser captured nested
   objects as raw strings but the decoder expected parsed Maps. Fixed by
   parsing item JSON strings recursively.
2. **JVM-only import in core** — `FileConfigurationStorage` used
   `java.io.File`, violating the core architecture rule. Moved to
   `com.omnibuds.android.bluetooth.storage` (authorized package).
3. **Unauthorized Android package** — initial `com.omnibuds.android.storage`
   violated the platform boundary test; moved under `bluetooth/`.

## Known limitations
- No Gradle/Lint (sandbox daemon IPC issue).
- File storage tested via unit tests; on-device file behavior unverified.
- Migration chain currently has one version (v1); mechanism tested.
