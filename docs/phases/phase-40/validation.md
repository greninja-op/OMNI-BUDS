# Phase 40 — Validation

**Date:** 2026-10-09
**Result:** PASS WITH LIMITATIONS

## Compilation
- Core main: 323 files (321 + 2 vendor), kotlinc 2.0.21,
  JVM 17, `-Werror` — clean.
- Core tests: 180 files (179 + vendor expansion), `-Werror` — clean.
- Android: unchanged; compiles clean, API 35, `-Werror`.

## Tests
- Core: **1551/1551 passed** (14 new vendor-expansion tests).
- Android: **272/272 passed**.
- Total: **1823/1823**, 0 failures.

## Test-only semantics fixed during this phase
- Scripted adapters: a different company's manufacturer ID is a
  definite non-match (NotMatched), not ambiguity — ambiguity is
  reserved for genuinely partial evidence.
- VendorRegistry behavior unchanged.

## Limitations
- No real vendor protocol implemented (no evidence).
- No physical hardware interaction (deferred to Phase 52).
- Performance assessed by inspection only.
