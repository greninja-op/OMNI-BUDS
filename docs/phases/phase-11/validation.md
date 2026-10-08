# Phase 11 — Codec Capability Engine: Validation

## 1. Result

**944 tests found, 944 passing, 0 failed, 0 skipped** — 816 in `:core`, 128 in
`:platform:android` unit tests.

## 2. How validation was performed

The Gradle daemon cannot run in this sandbox: the environment intercepts Java
loopback TCP, corrupting Gradle daemon IPC (same blocker as Phases 9–10).
`./gradlew test`, Android Lint, and full Gradle static analysis therefore did
**not** run.

Manual Kotlin/JUnit toolchain:
- `kotlinc-jvm` 2.0.21 (matches `gradle/libs.versions.toml`), JVM target 17, `-Werror`
- Production compiled clean (core; android against `android.jar` API 35 + core)
- Tests compiled clean; JUnit Platform Console Standalone 1.10.1
- Deps: `kotlinx-coroutines-core/test-jvm` 1.9.0, `kotlin-test` 2.0.21
- Friend paths configured for `internal` access, matching Gradle test setup

## 3. What was verified

- All 25 requirements have covering tests or explicit review notes.
- The 7 capability states are pairwise-distinct per §39 (ladder + orthogonal configurable).
- Unknown metadata invariants: no 0/16/stereo/bitrate defaults (§40).
- Multi-device isolation, staleness, reconnect (§35, §36).
- Transport separation: LC3→LE_AUDIO, never A2DP (§34).
- API degradation: empty read → UNKNOWN/NOT_OBSERVABLE, never a throw (§37).
- Evidence preserved; confidence never inflates (§38).
- Architecture: `DependencyDirectionTest` passes with `codec` at layer 3;
  `CodecScopeTest` bans control/interception vocabulary and android imports.
- Error categories: 3 new categories in all exhaustive tables.

## 4. Known limitations (honest)

- **Gradle/Lint/static analysis did not run** (sandbox daemon IPC blocker).
- **Physical-device verification deferred**: `CodecApi35` binder path, real
  `getSupportedCodecTypes()` values, API-35 class-loading — all hardware-only.
- **No public API exposes the active codec.** This is a platform fact, verified
  by android.jar reflection + api-versions.xml — not a gap in the implementation.
  The architecture records it as NOT_OBSERVABLE rather than working around it
  with hidden APIs.

## 5. Requirement coverage

| Requirement | Verified by |
|---|---|
| OB-P11-REQ-001, 002 | `CodecDomainTest`, `CodecMappingTest` |
| OB-P11-REQ-003, 004 | `CodecCapabilityStateTest` |
| OB-P11-REQ-005 | `CodecMetadataTest` |
| OB-P11-REQ-006, 007 | `CodecEvidenceTest`, `AndroidCodecObservationSourceTest` |
| OB-P11-REQ-008–012 | `CodecCapabilityEngineTest` |
| OB-P11-REQ-013 | `OmniBudsErrorCategoryTest` |
| OB-P11-REQ-014–018 | `AndroidCodecObservationSourceTest`, `CodecMappingTest`, `CodecScopeTest` |
| OB-P11-REQ-019, 020 | `CodecScopeTest` |
| OB-P11-REQ-021 | `DependencyDirectionTest`, diff review |
| OB-P11-REQ-022–024 | `CodecMappingTest`, `CodecDomainTest`, `CodecMetadataTest` |
| OB-P11-REQ-025 | This document set |
