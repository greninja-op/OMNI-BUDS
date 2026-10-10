# Phase 51 — Release Engineering Requirements

## Metadata
- **Phase**: 51
- **Domain**: Release Engineering, Kotlin Multiplatform Build, Packaging, Verification, and Distribution
- **Status**: Implemented / Ready for Review

---

### REQ-P51-001: Centralized Application Versioning
- **Description**: Establish a single authoritative definition of application versioning (`ApplicationVersion.kt` at `com.omnibuds.core.release`) across core, Android, and Desktop modules.
- **Rationale**: Prevents version divergence across platforms and separates application semantic version from protocol/knowledge/schema version domains.
- **Dependencies**: None.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - `ApplicationVersion.CURRENT` provides `major`, `minor`, `patch`, `versionName` ("1.0.0"), and `versionCode` (1000000).
  - Version codes increase monotonically across releases.
  - Core domain layer registers `release` at layer 0 in architecture test without cyclic or upward dependencies.
- **Verification Method**: `ApplicationVersionTest` and `DependencyDirectionTest`.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-002: Reproducible Build and Release Artifact Generation
- **Description**: Implement a deterministic release build and packaging pipeline (`scripts/release_build.sh`) supporting all verified toolchain targets: `:core` (JVM JAR), `:platform:desktop` (Desktop Application JAR + standalone Linux x64 distribution tarball), `:platform:android` (AAR), and companion verification shell (APK).
- **Rationale**: Releases must be built predictably from source revisions without relying on undeclared dynamic dependencies or fragile local configurations.
- **Dependencies**: OpenJDK 17, `kotlinc` 2.0.21, AAPT2 35.0.0, D8 35.0.0, ZipAlign 35.0.0.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - Compiles `:core` with `-jvm-target 17` and `-Werror`.
  - Compiles `:platform:desktop` and packages runnable JAR with `Main-Class: com.omnibuds.desktop.DesktopApplicationMainKt` and launcher script.
  - Builds `:platform:android` library `.aar` with compiled resources and classes.jar.
  - Builds companion shell APK with classes.dex and zipalign.
  - Generates real non-zero-sized artifacts in `build/release-dist/`.
- **Verification Method**: Automated execution of `scripts/release_build.sh`.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-003: Secure Signing Configuration & Secret Handling
- **Description**: Provide secure Android signing support driven entirely by environment variables (`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`) without embedding credentials in source, logs, or configuration.
- **Rationale**: Protects private keys and guarantees that release artifacts cannot be signed with unauthorized local keys while preventing unhandled build crashes when secrets are absent.
- **Dependencies**: `apksigner`.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - If keys are missing, the pipeline safely reports `NO_SIGNING_KEYS` and leaves artifacts marked as unsigned/blocked.
  - No secrets, keystores, or passwords appear in repository files or logs.
  - No fake production identity is fabricated.
- **Verification Method**: Script execution audit and output inspection.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-004: Artifact Integrity, Checksums & Release Manifest Generation
- **Description**: Automatically compute SHA-256 checksums and a structured `release-manifest.json` describing every generated artifact.
- **Rationale**: Maintainers and consumers must be able to verify supply-chain integrity and artifact provenance.
- **Dependencies**: `sha256sum`, Python 3.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - `CHECKSUMS.sha256` contains valid SHA-256 hashes matching all generated files.
  - `release-manifest.json` records product name, version, git revision, timestamp, artifact sizes, hashes, signing status, and release gate statuses.
- **Verification Method**: `sha256sum -c CHECKSUMS.sha256` and JSON validation.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-005: Release Verification CI Workflow Integration
- **Description**: Establish an automated CI workflow (`.github/workflows/release-verification.yml`) executing release packaging, checksum verification, and manifest integrity checks.
- **Rationale**: Ensures continuous release pipeline validation on push and pull request without exposing artifacts to public download registries.
- **Dependencies**: GitHub Actions runners with Java 17 and Android SDK.
- **Priority**: P1 (Important)
- **Acceptance Criteria**:
  - Workflow file is valid YAML.
  - Targets `main` branch pushes and PRs.
  - Uploads artifacts as private internal CI workflow artifacts with retention limits.
  - Does NOT publish public releases or deploy to app stores.
- **Verification Method**: Static inspection and schema check.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-006: Preservation of Hardware Truth & Non-Simulation Principles
- **Description**: Release configuration must never alter OmniBuds' core architectural rule: hardware capabilities are only reported when verified from the device, never simulated.
- **Rationale**: Prevents release builds from introducing mock states or fake hardware connections.
- **Dependencies**: Architecture rules in `:core`.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - All 22 `DependencyDirectionTest` and architecture rules pass.
  - Production build does not contain fake hardware drivers or simulated protocol ports.
- **Verification Method**: JUnit architecture suite execution.
- **Implementation Status**: Implemented & Verified.

---

### REQ-P51-007: Release Readiness Gates & Deferred Hardware Verification
- **Description**: Define explicit release gates distinguishing verified software artifacts from deferred physical hardware testing.
- **Rationale**: Phase 51 produces packaging infrastructure; Phase 52 is responsible for final physical device QA and production release authorization.
- **Dependencies**: Phase 50 test results, Phase 51 release artifacts.
- **Priority**: P0 (Mandatory)
- **Acceptance Criteria**:
  - Release readiness gates clearly record physical hardware verification as `DEFERRED_PHASE_52`.
  - Production signing marked `BLOCKED_NO_KEYS` when private secrets are absent.
- **Verification Method**: `release-manifest.json` gate inspection.
- **Implementation Status**: Implemented & Verified.
