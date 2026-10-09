# Phase 44 — Validation: Protocol Versioning & Compatibility Management

This file records the exact commands executed, actual results, failed checks,
skipped checks, blocked checks, and known limitations. Nothing below is
claimed without having been run.

## 1. Build and compilation

### Core main sources
Command (run from `~/agy-work/omnibuds`):
```
kotlinc @build/core_main_sources.txt -cp "$CORE_CP" -d build/core-main \
  -jvm-target 17 -Werror
```
- kotlinc 2.0.21, JDK 17, `-Werror`
- Result: **BUILD SUCCESSFUL**, 0 errors, 0 warnings-as-errors.
- Sources: all `core/src/main/kotlin/**/*.kt`, including the 9 new files under
  `com/omnibuds/core/protocol/version/`.

### Core test sources
Command:
```
kotlinc @build/core_test_sources.txt -cp "build/core-main:$TEST_CP" \
  -Xfriend-paths=build/core-main -d build/core-test -jvm-target 17 -Werror
```
- Result: **BUILD SUCCESSFUL**.
- Note: `-Xfriend-paths` is required because existing test sources access
  `internal` declarations (same as the Gradle `friendPaths` configuration).
  Without it, pre-existing test files (e.g. `VendorExtensionTest`,
  `ConfigurationSupportTest`, `VerificationRepositoryTest`) fail to compile.
  This is an environment invocation detail, not a code defect.

### Android module
- **BLOCKED in this environment.** Ad-hoc kotlinc compilation of
  `platform/android/src/main` fails on the generated `R` class
  (`OmniBudsWidgetProvider.kt:199: unresolved reference 'R'`), which requires
  aapt2 resource processing unavailable to the manual toolchain here.
- This is a pre-existing environment limitation, not a Phase 44 regression:
  Phase 44 adds **zero** files under `platform/android/` (verified via
  `git status` — only `core/` and `docs/` changed).

## 2. Test execution

### Core test suite
Command (run from `~/agy-work/omnibuds/core`, which the architecture scope
tests require as working directory):
```
java -jar junit-platform-console-standalone-1.10.1.jar execute \
  -e junit-jupiter \
  --class-path "../build/core-main:../build/core-test:$STDLIB:$TEST_CP:src/main/resources:src/test/resources" \
  --scan-class-path --details=summary
```
Result:
```
[      1623 tests found           ]
[         0 tests skipped         ]
[      1623 tests started         ]
[         0 tests aborted         ]
[      1623 tests successful      ]
[         0 tests failed          ]
```
- **1623/1623 core tests pass, 0 failures.**
- New Phase 44 tests (30 total, all passing):
  - `ProtocolVersionTest` — 6
  - `VersionConstraintTest` — 5
  - `CompatibilityResolverTest` — 7
  - `ProtocolVersionRegistryTest` — 4
  - `ProtocolSchemaMigrationTest` — 3
  - `VersionedMessageCodecTest` — 2
  - `ProtocolVersionSecurityTest` — 3

### Notes on invocation correctness
- The JUnit vintage engine fails discovery in this standalone setup
  (missing vintage engine deps); `-e junit-jupiter` selects the engine the
  project actually uses. Not a product issue.
- The Kotlin stdlib jar must be on the runtime classpath explicitly
  (`kotlin-stdlib.jar` from the kotlinc distribution); without it tests fail
  with `NoClassDefFoundError: kotlin/collections/CollectionsKt`. Invocation
  detail only.
- `src/main/resources` must be on the classpath for
  `AppleAirpodsAdapterTest` (Phase 42 JSON identity resource); without it
  those 4 tests fail. Invocation detail only.
- Architecture scope tests (`CodecScopeTest`, `Phase*N*ScopeTest`,
  `DependencyDirectionTest`) require the JVM working directory to be the
  module root (`core/`); run from the repo root they fail on path
  expectations. This matches how earlier phases executed them.

### Android tests
- **NOT RUN** — blocked by the `R`-class environment limitation above.
- No Phase 44 code exists in the Android module; the 272 Android tests from
  Phase 42 are unaffected by this change (no shared-source edits outside
  additive new package `core.protocol.version`).

### Regression scope
- The full core suite (Phases 0–43 + new Phase 44 tests) ran: 1623/1623.
- Includes architecture direction tests (new `core/protocol/version/`
  area does not violate layer boundaries), unknown-device read-only tests,
  authorization tests, and SDK conformance tests.

## 3. Static checks performed by hand

- **Secret audit:** `grep -rniE "api[_-]?key|secret|password|token|private[_-]?key|bearer"`
  over new sources and docs. Only hits are the words "proprietary tokens"
  in protocol-versioning prose (version identifier terminology), not
  credentials. **Clean.**
- **Untracked clutter excluded from the commit:** `phase-43-prompt.md`,
  `phase-44-prompt.md`, `queued-prompts/`, `run_tests.sh`, `build/` —
  none of these are part of the product and none were committed.
- **Diff review:** all new files are under
  `core/src/main/kotlin/com/omnibuds/core/protocol/version/`,
  `core/src/test/kotlin/com/omnibuds/core/protocol/version/`, and
  `docs/phases/phase-44/`. No existing file modified. No unrelated changes.

## 4. Acceptance criteria status

| Criterion | Status |
|---|---|
| Version domains explicitly distinguished | PASS — `ProtocolVersion.kt` (Semantic/Integer/Named/Unknown schemes) |
| Protocol identity and compatibility contracts documented and implemented | PASS — `ProtocolIdentity.kt`, `VersionConstraint.kt`, `CompatibilityOutcome.kt` |
| Version resolution deterministic and testable | PASS — `CompatibilityResolver.kt` + 7 resolver tests |
| Ambiguous/unknown/incompatible versions fail safely | PASS — resolver + `ProtocolVersionSecurityTest` (3 tests) |
| Schema migration preserves evidence and historical records | PASS — `ProtocolSchemaMigration.kt` + 3 migration tests |
| Version-specific implementations cannot leak across vendors | PASS — `ProtocolVersionRegistry.kt` + 4 registry tests; `VersionedMessageCodec.kt` + 2 codec tests |
| SDK and registry integration complete | PASS — registry integrates Phase 40 vendor registry and Phase 43 SDK contracts (per design doc) |
| Centralized authorization remains mandatory | PASS — security tests verify unknown versions cannot authorize writes |
| Automated tests pass | PASS — 1623/1623 core (30 new) |
| Documentation exists and matches implementation | PASS — 17 files under `docs/phases/phase-44/` (incl. this file) |
| No physical hardware interaction | PASS — none performed |
| Hardware-dependent claims deferred to Phase 52 | PASS — no HARDWARE_VERIFIED claims made |

## 5. Known limitations

1. Android module compilation and Android unit tests could not be executed
   in this sandbox (generated `R` class requires aapt2 resource processing).
   Phase 44 contains no Android code, so no regression is expected, but this
   remains formally unverified here.
2. The 17th documentation file (`validation.md`, this file) and the
   CONTEXT.md continuity entry were completed by the main agent after the
   phase CLI exited early; implementation and test files are entirely the
   CLI's work and were verified, not modified, afterward.
3. No performance measurements were taken (no suitable repeatable harness
   for resolver throughput in this environment).
