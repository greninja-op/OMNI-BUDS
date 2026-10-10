# Phase 52 — Task List: Final QA Execution & Release Verification

## Task Breakdown

| Task ID | Title | Dependencies | Target Files / Scope | Acceptance Criteria | Status |
|---|---|---|---|---|---|
| **T-52-01** | Repository & Working Tree Audit | None | Git status, `scripts/release_build.sh` | Confirm branch `main` is aligned with `origin/main`, audit file mode on `scripts/release_build.sh`, audit untracked `.github/workflows/`. | **COMPLETED** |
| **T-52-02** | Phase 51 Artifact & Manifest Verification | T-52-01 | `build/release-dist/*` | 5 packages exist, sizes nonzero, SHA-256 bit-for-bit match in `CHECKSUMS.sha256`, manifest schema valid. | **COMPLETED** |
| **T-52-03** | Core Regression Suite Execution | Toolchain | `core/src/test/kotlin` | Kotlinc compile with `-Werror`, JUnit ConsoleLauncher runs all 1,693 core tests with 0 failures. | **COMPLETED** |
| **T-52-04** | Android Platform Suite Execution | T-52-03 | `platform/android/src/test/kotlin` | AAPT2 compile + link, Kotlinc compile with `-Werror`, JUnit ConsoleLauncher runs all 321 Android tests with 0 failures. | **IN_PROGRESS** |
| **T-52-05** | Desktop Platform Suite Execution | T-52-03 | `platform/desktop/src/test/kotlin` | Kotlinc compile with `-Werror`, JUnit ConsoleLauncher runs all 42 Desktop tests with 0 failures. | **PENDING** |
| **T-52-06** | Security, Privacy & Redaction Audit | T-52-03 | `core/security`, `core/access`, `build/release-dist` | Verify `LogRedactor`, default-deny authorization on unknown devices, and verify zero secrets in release jars. | **COMPLETED** |
| **T-52-07** | Hardware Environment Reality Audit | Phase 38 Plan | Headless Linux container | Inspect physical RF, Bluetooth devices, and Android handsets. Confirm 0 physical devices available. | **COMPLETED** |
| **T-52-08** | Defect Register & Triage | T-52-03..07 | `docs/qa/defect-register.md` | Catalog any verified defects or environmental gaps (DEF-52-001 GitHub 403, DEF-52-002 No Hardware). | **COMPLETED** |
| **T-52-09** | 15 Release Gates Evaluation | T-52-01..08 | `docs/release/release-readiness.md` | Evaluate each gate explicitly: 13 software gates PASSED, 1 signing BLOCKED_NO_KEYS, 1 hardware NOT_VERIFIED. | **COMPLETED** |
| **T-52-10** | Release Readiness & Final Recommendation | T-52-09 | `docs/release/*`, `docs/phases/phase-52/*` | Document CONDITIONAL GO recommendation for software candidate RC1, document known limitations and next steps. | **COMPLETED** |
