# Phase 1 — Repository Analysis

**Deliverable of:** Agent 1 — Repository / Build Auditor (Phase 1 execution prompt section 4).
**Method:** read-only inspection of the working tree plus the Gradle configuration, the source sets, the toolchain already present on this workstation, and the git history. One document was written: this one. No source file, build script or machine-level setting was modified by this analysis.
**Baselines compared:** Phase 0 close, as recorded in `docs/phases/phase-0/repository-audit.md`, and the tree as it stands after the Phase 1 implementation, the review-finding fixes and their verification (HEAD `a5f3bf2`).

---

## 1. Phase 0 close versus now

| Item | At Phase 0 close | After Phase 1 |
|---|---|---|
| Git repository | **None.** No `.git`, no branch, no commit, no remote, no tag | `main`, 5 commits, HEAD `a5f3bf2`; `.gitignore` + `.gitattributes` + `.editorconfig` committed |
| Source files | None — no `src/`, no `*.kt`, no `*.xml` | 71 Kotlin main files (5,196 lines) in `:core`, 108 files / 10,659 insertions in the domain commit `4c56e2d`, then 13 files / +819 −263 in the fix commit `a5f3bf2` |
| Tests | None; no test framework dependency | 41 test files (5,885 lines), 37 test classes, 302 tests, 0 failures / 0 errors / 0 skipped |
| Build files | None — no `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, wrapper | Full Gradle root + 2 modules + version catalog + wrapper |
| Modules | None | `:core` (Kotlin/JVM), `:platform:android` (AGP library, zero Kotlin sources) |
| Kotlin / AGP / Gradle versions | "Not declared anywhere — unknown, not assumed" | Declared and pinned: Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17 |
| Android SDK usage | None recorded | compileSdk 35 against a real installed SDK (see §4) |
| Documentation | `docs/MASTER-CONTEXT.md` + `docs/phases/phase-0/` (17 files, counted now) | unchanged, plus `docs/phases/phase-1/` and `docs/templates/` (8 templates) |
| CI | None | **Still none** (see §7) |
| README at root | None | A root `README.md` now exists and opens with the prompt §43 statement verbatim ("OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality"), listing devices supported as **None**. It is **uncommitted** — untracked at HEAD `a5f3bf2`, like the Phase 1 documents themselves, which no commit has taken in yet |
| Architecture enforcement | Prose rules only | `DependencyDirectionTest` — 11 machine-checked rules run on every build |

The audit trail of the jump is the commit history itself:
`ff850c3 docs(phase-0)` → `6dce44b build(phase-1): establish a reproducible Gradle foundation` → `28817b3 build(phase-1): track the Gradle wrapper scripts` → `4c56e2d feat(core): establish the platform-independent domain foundation` → `a5f3bf2 fix(core): give connection state one owner and close the review findings`. Scopes `build` and `deps` did not exist in Phase 0's list and were added by ADR-P1-017. The last commit is the one that answers the Phase 1 architecture review: it deletes `DeviceSession`'s competing connection state (ADR-P1-020), removes the unused production dependency (ADR-P1-021), fixes `FeatureId.parseOrNull`, rewrites `VendorExtension`'s stale defect narrative, adds `.editorconfig`, and adds the 26 tests that close the `common` and `session` coverage gaps (275 → 301).

---

## 2. Root build inventory

| Path | What it does | Notable |
|---|---|---|
| `settings.gradle.kts` | root project `OmniBuds`; `pluginManagement` = google/mavenCentral/gradlePluginPortal; `dependencyResolutionManagement` = `FAIL_ON_PROJECT_REPOS` with google + mavenCentral | includes `:core` and `:platform:android`, each with a comment naming the governance document that forbids the reverse dependency |
| `gradle.properties` | `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, `parallel=true`, `caching=true`, `kotlin.code.style=official`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true` | contains **no** machine-specific path and no `org.gradle.java.home` — deliberate, so the build cannot disturb other projects (ADR-P1-014). The two AndroidX flags are prophylactic: no Android source consumes them yet |
| `gradle/libs.versions.toml` | single version catalog: kotlin 2.0.21, kotlinxCoroutines 1.9.0, androidGradlePlugin 8.7.3, compileSdk 35, minSdk 26, targetSdk 35, junitJupiter 5.10.1 | every entry carries a recorded reason (prompt §34); nothing was added because it is popular |
| `gradle/wrapper/` | `gradle-wrapper.jar` + properties pinning `gradle-8.9-bin.zip`, `networkTimeout=10000`, `validateDistributionUrl=true` | jar force-included by `.gitignore` (`!gradle/wrapper/gradle-wrapper.jar`) |
| `gradlew`, `gradlew.bat` | wrapper scripts, tracked by commit `28817b3` | executable bit preserved on the Unix script |
| `.gitignore` | build outputs, `.gradle/`, `.kotlin/`, `local.properties`, IDE files, signing material, and OmniBuds-specific privacy artifacts (`*.pcap`, `*.btsnoop`, `**/captures/`, `**/diagnostics/export/`, `*.diag.json`) | device captures are personal data and hardware-sensitive (Phase 0 `security-governance.md`); no ignored file was committed |
| `.gitattributes` | `* text=auto eol=lf` plus explicit `eol=lf` for `.kt/.kts/.xml/.md/.properties/.toml`, `binary` for `.jar/.aar/.png/.webp/.pcap/.btsnoop` | a Windows workstation with autocrlf would otherwise rewrite every file it touches |
| `.editorconfig` | `root = true`; UTF-8, LF, final newline, trimmed trailing whitespace, 4-space indent, 120-column limit for all files; YAML/JSON and TOML at 2 spaces; Markdown exempt from trailing-whitespace trimming and from the line-length rule | tracked since `a5f3bf2`. It records the conventions `specs.md` §1 and §0 already describe; nothing in the Gradle build reads it, so it is an editor aid, not a second enforcement channel (ADR-P1-011) |
| `local.properties` | `sdk.dir=C:/Users/Athira Aswin/AppData/Local/Android/Sdk` | **git-ignored**, machine-local, documented as such in its own header comment |

`.editorconfig` now exists at the repository root, which is the change this section recorded as missing at first writing: ADR-P1-011's "`.editorconfig`-style convention set documented in `specs.md`" is no longer only prose — the file is committed and matches the conventions `specs.md` states (4-space Kotlin indent and 120 columns against `max_line_length = 120`, 2-space TOML/YAML, Markdown excused from trimming and line limits because tables and wrapped prose depend on both). It remains true that no formatter, linter or Gradle task enforces it: the machine-checked baseline is still the compiler with `allWarningsAsErrors` plus `DependencyDirectionTest`.

---

## 3. Per-module configuration, source sets and dependencies

**`:core`** (`core/build.gradle.kts`) — plugin `alias(libs.plugins.kotlin.jvm)`; `kotlin { compilerOptions { jvmTarget = JVM_17, allWarningsAsErrors = true, javaParameters = true } }`; `java { sourceCompatibility/targetCompatibility = VERSION_17 }`. Source sets: `core/src/main/kotlin` (71 files, 11 areas — see `design.md` §2) and `core/src/test/kotlin` (41 files). No `androidTest`, no resources, no assets. Dependencies: **no production dependency at all** — ADR-P1-021 deleted `api(libs.kotlinx.coroutines.core)`, since no main source imported `kotlinx.coroutines` and `suspend` needs no library; the `dependencies` block is now test-only: `testImplementation` of `kotlin-test`, `junit-jupiter`, `kotlinx-coroutines-test` and `testRuntimeOnly` of `junit-platform-launcher`. The build script's own comment records that the coroutines library returns with the first `Flow` surface, which belongs to the Phase 2 state engine. Test task config: `useJUnitPlatform()`, logging events `failed` and `skipped`, `TestExceptionFormat.FULL`. Build variants: none (a plain JVM library has `main`/`test` compilations only).

**`:platform:android`** (`platform/android/build.gradle.kts`) — plugins `android.library` + `kotlin.android`; `namespace = "com.omnibuds.android"`, compileSdk/minSdk read from the catalog and **hoisted to file-scope `val`s** because `libs` does not resolve inside the `android {}` receiver (see §5); `compileOptions` 17/17; `kotlin { compilerOptions { jvmTarget = JVM_17, allWarningsAsErrors = true } }`; one dependency, `api(project(":core"))`. Sources: `platform/android/src/main/AndroidManifest.xml` only — an empty `<manifest/>` with no permissions, components or intent filters. No `src/test`, no `src/androidTest`, no resource files, no flavours, no custom `buildTypes`, so the default AGP `debug`/`release` variants of a library are the only ones.

Note for Phase 2: `javaParameters = true` is set in `:core` but not in `:platform:android`. It is harmless today (no Android sources exist) and is the kind of asymmetry that becomes a real difference once reflection-based DI frameworks appear on the Android side.

Dependency inventory against prompt §34 (every dependency needs a reason) — four coordinates are declared and **every one of them is test-only**. The fifth coordinate this table carried at first writing, `kotlinx-coroutines-core` as `api` in `:core`, was deleted at `a5f3bf2` under ADR-P1-021: no main source imported `kotlinx.coroutines`, `suspend` is a language feature that needs no library, and no `Flow` exists yet, so the entry was a claim about the code rather than a fact about it. The catalog keeps `kotlinCoroutines = "1.9.0"` for the test artifact alone.

| Coordinate | Scope | Reason recorded | Churn risk |
|---|---|---|---|
| — none — | production (`:core`) | **Zero production dependencies** (ADR-P1-021): stdlib only, so nothing external has to be proven portable for another target | none — there is nothing to upgrade |
| `org.jetbrains.kotlin:kotlin-test:2.0.21` | `testImplementation` | assertion style already used across all 41 test files | none — no production exposure |
| `org.junit.jupiter:junit-jupiter:5.10.1` + `org.junit.platform:junit-platform-launcher:1.10.1` | `testImplementation` / `testRuntimeOnly` | JUnit 5 platform for `useJUnitPlatform()`; the launcher is required by Gradle's JUnit Platform integration | none |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` | `testImplementation` | `runTest` for the `suspend` contracts — used by exactly two classes, `persistence/DeviceRepositoryContractTest` and `testing/TestDoublesAreNotHardwareTest` — and `kotlinx.coroutines.yield` in `testing/ScriptedOutcome.kt` | none |

No transitive dependency was adopted as a side effect of another choice, no BOM is imported, and `:core`'s compile classpath is now the Kotlin stdlib and nothing else — nothing Android, nothing `kotlinx`.

---

## 4. Android SDK components found on this workstation

Inspected read-only at `C:/Users/Athira Aswin/AppData/Local/Android/Sdk`, the path `local.properties` points at:

| Component | Present |
|---|---|
| `platforms/android-35` | yes — the compileSdk the build pins |
| `build-tools` | `34.0.0` and `35.0.0` |
| `platform-tools` | yes |
| `cmdline-tools` | yes |
| `licenses` | accepted: `android-sdk-license`, `android-sdk-preview-license`, `android-googletv-license`, `android-googlexr-license`, `android-sdk-arm-dbt-license`, `google-gdk-license`, `mips-android-sysimage-license` |

No SDK component was installed, updated or removed during Phase 1. The build consumes what was already there, which is why compileSdk 35 was chosen rather than a newer number that would have required a download decision.

---

## 5. Toolchain discovery, and the two build failures that shaped the scripts

Discovery, as it actually happened:

1. `java` was **not** on `PATH`. `gradle` was **not** on `PATH` either, so no system Gradle existed to generate a wrapper with.
2. A user-level JDK 17 already existed at `C:\Users\Athira Aswin\AppData\Local\jdk-17`: Microsoft OpenJDK `17.0.20.1` (build `17.0.20.1+1-LTS`). This is what the build targets, via `JAVA_HOME` set per invocation.
3. `~\.gradle\wrapper\dists` already held **gradle-8.9-bin** and **gradle-9.2.0-bin**; the shared module cache already held AGP 8.7.3 and 8.5.2 plugin artifacts and Kotlin 2.0.21 compiler artifacts, and the network path to Maven Central and the Gradle plugin portal worked.
4. Gradle **8.9** was chosen over the cached 9.2.0 because AGP 8.7.3's supported Gradle range is met by 8.9, and pinning the lower distribution avoided an upgrade-everything change that prompt §33 forbids. Kotlin 2.0.21 and AGP 8.7.3 were chosen for the same reason: they were already resolved on this machine and are mutually compatible.

The deliberate constraint (ADR-P1-014): **no machine-level toolchain was installed, moved, reconfigured or modified.** Nothing was written to a user-level `gradle.properties`, no `org.gradle.java.home` was baked into any project or shared file, no SDK component was installed. The JDK is selected per invocation through `JAVA_HOME`; the SDK location lives in the git-ignored `local.properties`. The only writes outside the project directory were Gradle's own additive dependency-cache entries. This matters beyond tidiness — the user runs other projects on this workstation, and a shared Gradle change would have leaked into them.

Two failures came first and both left a permanent mark on the scripts:

| Failure | What it revealed | Fix now visible in the tree |
|---|---|---|
| Version-catalog accessor path error: `libs.versions.android.compileSdk` does not exist | Catalog keys become accessors **literally**; `androidCompileSdk` in `[versions]` is `libs.versions.androidCompileSdk`, not a dotted path. The dot notation is only for nested keys, and nothing in the catalog is nested | `gradle/libs.versions.toml` keys are referenced flat; `platform/android/build.gradle.kts:10-11` |
| `libs` did not resolve inside the `android { }` receiver | The AGP `android {}` block has its own receiver scope, so the generated version-catalog accessor is not visible at that point — a classic Kotlin-DSL scoping trap, not a missing dependency | hoisted to file scope with an explanatory comment: `val omniBudsCompileSdk = libs.versions.androidCompileSdk.get().toInt()` (`platform/android/build.gradle.kts:8-11`, consumed at lines 19 and 22) |

Both failures were build-configuration failures, not domain failures: neither indicated a problem with the module design, and both were fixed without changing any pinned version.

---

## 6. Repository and build state at verification

`./gradlew build` → BUILD SUCCESSFUL. 302 tests, 0 failures, 0 errors, 0 skipped. The working tree at HEAD `a5f3bf2` carries no source or build changes beyond the commit itself; what is uncommitted is documentation. `docs/phases/phase-1/decisions.md` and `execution-prompt.md` are tracked, and `a5f3bf2` landed the first 19 ADRs — but ADR-P1-020 and ADR-P1-021 are still working-copy additions. Untracked: this document set (`requirements.md`, `specs.md`, `design.md`, the six agent reviews, `test-plan.md`, `task-list.md`, `risk-register.md`), `docs/templates/`, and the root `README.md`; separately modified are `docs/MASTER-CONTEXT.md`, `docs/README.md` and `docs/decisions/README.md`. `validation.md` does not exist yet. The documentation commit that closes the phase belongs to the orchestrator, not to this auditor. Build outputs (`core/build/`, `.gradle/`, `.kotlin/`) exist on disk and are correctly ignored by git.

---

## 6.1 Limits of this analysis

Recorded so that nobody later reads an absence here as a passing check:

- **No remote exists.** `git remote -v` is empty, so branch protection, review gates and push history are unassessable; the git-workflow rules in `docs/phases/phase-0/git-workflow.md` are enforced by discipline alone until a remote and CI exist.
- **No device was connected and no hardware was observed.** Every hardware-facing statement in the tree is a contract or a rule at `VerificationLevel.INFERRED`; this audit verified the absence of implementations, not the presence of any behaviour.
- **Build reproducibility was verified on one machine.** The pinned versions resolve from caches that already existed here; a genuinely clean machine would exercise the download path, which nothing has tested yet.
- **Test count, not test quality.** 302 passing tests were counted from the run and from a `@Test` census across the twelve test packages — 37 classes in 41 files, the other four files being `testing/` doubles and helpers that hold no test. The zero-coverage finding this audit made against `session/DeviceState.kt` is now closed by `DeviceStateTest` (12), and `common` gained `FeatureIdTest`, `OmniBudsErrorCategoryTest` and `OperationOutcomeTest` (19). What the census still shows: `state/` has no test class of its own, and `EarbudProtocol`, `TransportContract`, the five optional reporting interfaces, `ProtocolParser`, `ProtocolEncoder` and `CapabilityDefinition` are named in no test — unavoidable for contracts Phase 1 refuses to implement, and worth stating so a later reader does not mistake a green run for coverage of the whole surface.

## 7. CI status

None, and still absent. No `.github/`, no `.gitlab-ci.yml`, no `azure-pipelines.yml`, no Fastlane, no Jenkinsfile, no CI badge in any document. Phase 0 recorded the same finding (`repository-audit.md` §2) and Phase 1 did not change it: adding a hosted CI service would have been a machine- and account-level side effect outside the phase's authority, and the prompt's Definition of Done (§50) does not require one. The consequence is that the only enforcement today is local: `./gradlew build` runs `DependencyDirectionTest` and the other 290 tests, and nothing runs it for you if you forget. That is a risk, recorded for `risk-register.md`, not a defect in the architecture.

---

## 8. What Phase 2 will find waiting

1. **A real module boundary with nothing behind it.** `:platform:android` compiles, has `api(project(":core"))`, and contains zero Kotlin sources. First Android source goes in `platform/android/src/main/kotlin/com/omnibuds/android/...`, and the test `platformAndroidModuleStillContainsNoSources` must then be retired by an ADR — it will fail on purpose the moment Phase 2 adds code, and that failure is the gate that makes the change deliberate (ADR-P1-001, ADR-P1-013).
2. **A manifest with no permissions.** Whatever Phase 2 actually implements is what earns its permission entry, with justification in that phase's security section (ADR-P0-001). minSdk 26 is explicitly provisional and expires at Phase 2 (ADR-P1-015).
3. **Three repository contracts with only test implementations.** `DeviceRepository`, `CapabilityRepository`, `ProtocolRecordAccess` and `TransportContract` have no production implementation, and `noProductionClassImplementsTheProtocolOrRepositoryContracts` will refuse a `:core` implementation. Real ones land in the platform module.
4. **A state architecture whose single owner is now settled — and tested.** `session/DeviceState` holds the only `ConnectionState` in the module and `attemptConnection` is the only way to move it, refusing an illegal move as `Failure(INVALID_STATE)`; `device/DeviceSession` answers only "which device, and did the user keep it" (ADR-P1-020). The gap this analysis recorded at first writing — `DeviceState` unexercised, the `revision`/`applyIfNewer` defense from prompt §30 unproven — is closed by `DeviceStateTest` (12 tests) at `a5f3bf2`. What Phase 2 inherits instead: publish this value from the state engine without re-introducing a second holder, because no mechanical guard forbids one — the layer test would catch an upward import, not a new field on `DeviceSession` (`design.md` §6, `architecture-review.md` Q8, REQ-P1-017).
5. **A concurrency model that is contracts only, with no coroutines artifact in sight.** No `Flow`, `StateFlow`, `SharedFlow`, `CoroutineScope`, `Dispatcher` or `withTimeout` exists in main sources, and `:core` declares **no production dependency** (ADR-P1-021): the 21 `suspend` operations compile against the stdlib alone. Phase 2 introduces the first publishing surface, and that is the moment `kotlinx-coroutines-core` comes back into `core/build.gradle.kts` and the catalog with a recorded reason — the deletion deliberately left the slot open rather than pretending the library was load-bearing.
6. **An empty `ProtocolRegistry`, no codec-per-endpoint model (ADR-P1-018), and a redaction obligation with no redactor behind it (ADR-P1-019).** None of these is a Phase 1 defect; they are Phase 1 claims kept honest by tests rather than by memory. The contracts with no implementation — `EarbudProtocol`, `TransportContract`, the five reporting interfaces, `ProtocolParser`, `ProtocolEncoder` — are named in no test at all, so Phase 2 is the first place their shapes get challenged by something that implements them.
7. **A reproducible toolchain contract.** Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 17 / compileSdk 35 are pinned in the catalog; a fresh machine needs only a JDK 17 reachable through `JAVA_HOME` and an `sdk.dir` in its own ignored `local.properties`. `.editorconfig` is committed, so an editor on that machine picks up the same conventions without any tool being installed. No CI means the first person to run the build on another machine is the one who finds out.
8. **An uncommitted paper trail.** `docs/phases/phase-1/requirements.md`, `specs.md`, `design.md`, `test-plan.md`, `task-list.md`, `risk-register.md` and the six agent reviews are untracked, as are `docs/templates/`, the root `README.md` and `docs/phases/phase-2/execution-prompt.md` (already present, untracked — Phase 2 has *not* started: `platform/android/src/main` still holds no Kotlin source and no Phase 2 code exists). `decisions.md` is tracked but carries uncommitted additions (ADR-P1-020, ADR-P1-021), and `docs/MASTER-CONTEXT.md`, `docs/README.md` and `docs/decisions/README.md` are modified. `validation.md` has not been written, so the phase is not closed in the history even though HEAD `a5f3bf2` closes its code findings. A `docs(phase-1)` commit should land before Phase 2 work starts (REQ-P1-022).
