# Phase 51 — Test & Verification Plan

## 1. Test Objectives & Boundaries

1. **Verify Baseline Test Suite**: Ensure all 2,051 previously passing tests across core, Android, and Desktop modules remain 100% green without regressions.
2. **Verify Architecture Compliance**: Ensure no Android dependencies, JVM-only libraries, or unauthorized capabilities leak into `:core`, and that `release` is correctly positioned at Layer 0.
3. **Verify Version Domain Models**: Verify parsing, formatting, validation constraints, and monotonic version code generation.
4. **Verify Artifact Packaging**: Verify compilation, packaging, artifact sizes, and checksum integrity across all supported targets.
5. **Verify Security Safeguards**: Ensure signing gracefully blocks when secrets are absent, secrets never leak to logs, and no simulated hardware behavior is packaged.
6. **Hardware Verification Boundary**: Strictly defer all physical Bluetooth hardware verification to Phase 52.

---

## 2. Test Execution Matrix

| Test Suite | Execution Command | Target Scope | Pass Criteria | Result |
|---|---|---|---|---|
| **Baseline Regression Suite** | `bash ~/agy-work/p50-verify.sh` | Core, Android, Desktop tests | 2,051 tests passed, 0 failed, 0 skipped | **PASS** |
| **Release Architecture Tests** | JUnit `ConsoleLauncher --select-class=com.omnibuds.core.architecture.DependencyDirectionTest` | Layer rules in `:core` | 0 architecture violations, release registered at layer 0 | **PASS** |
| **Version Domain Tests** | JUnit `ConsoleLauncher --select-class=com.omnibuds.core.release.ApplicationVersionTest` | Version parsing & codes | 5/5 unit tests pass | **PASS** |
| **Release Packaging Pipeline** | `bash scripts/release_build.sh` | All supported build targets | 5 artifacts generated with valid checksums & manifest | **PASS** |
| **Checksum Verification** | `sha256sum -c CHECKSUMS.sha256` | Artifact integrity | All digests match bit-for-bit | **PASS** |
| **Manifest Schema Validation** | Python JSON parse & assertion | `release-manifest.json` | Valid v1 schema, correct gate states | **PASS** |

---

## 3. Deferred Verification Items

- **Physical Headset Connection**: Deferred to Phase 52 hardware QA rig.
- **Production Secret Signing**: Deferred to maintainer CI configuration with genuine production keys.
- **App Store / Distribution Upload**: Out of scope for Phase 51.
