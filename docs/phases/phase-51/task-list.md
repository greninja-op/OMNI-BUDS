# Phase 51 — Executable Task List

| Task ID | Task Description | Affected Files | Dependencies | Required Verification | Status |
|---|---|---|---|---|---|
| `TASK-P51-01` | Repository & Toolchain Audit | `rebuild.sh`, `gradle/*`, environment | None | Git status, toolchain path discovery, Gradle daemon analysis | **COMPLETED** |
| `TASK-P51-02` | Baseline Test Suite Verification | `core`, `platform/android`, `platform/desktop` | Toolchains | Execute 2,051 unit & architecture tests via `p50-verify.sh` | **COMPLETED** |
| `TASK-P51-03` | Authoritative Version Domain Implementation | `core/src/main/kotlin/com/omnibuds/core/release/ApplicationVersion.kt` | None | Unit test `ApplicationVersionTest`, architecture test | **COMPLETED** |
| `TASK-P51-04` | Register Release Area in Core Architecture | `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` | `TASK-P51-03` | `DependencyDirectionTest` JUnit execution | **COMPLETED** |
| `TASK-P51-05` | Release Packaging Script Implementation | `scripts/release_build.sh` | Toolchains, AAPT2 | Successful generation of `.jar`, `.aar`, `.tar.gz`, `.apk` | **COMPLETED** |
| `TASK-P51-06` | Secure Signing Logic & Fallback | `scripts/release_build.sh` | `apksigner` | Block signed APK safely when credentials absent | **COMPLETED** |
| `TASK-P51-07` | Checksum & Release Manifest Generation | `scripts/release_build.sh` | `sha256sum`, Python 3 | `CHECKSUMS.sha256` validation, `release-manifest.json` parse | **COMPLETED** |
| `TASK-P51-08` | Release Verification CI Workflow | `.github/workflows/release-verification.yml` | None | GitHub Actions YAML schema validation | **COMPLETED** |
| `TASK-P51-09` | Dependency & License Audit | `gradle/libs.versions.toml`, `build.gradle.kts` | None | Documentation of pinned dependencies and license notices | **COMPLETED** |
| `TASK-P51-10` | Rollback & Failure Recovery Procedures | `docs/phases/phase-51/decisions.md` | None | Documented failure modes and recovery runbooks | **COMPLETED** |
| `TASK-P51-11` | Documentation Suite Generation | `docs/phases/phase-51/*.md` (8 files) | All tasks | Full phase documentation review | **COMPLETED** |
