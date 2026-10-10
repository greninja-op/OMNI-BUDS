# Phase 51 — Validation and Test Results

## 1. Environment & Toolchains Executed

- **Linux Kernel**: Linux 7.0.0-39-generic x86_64
- **Java Runtime**: Eclipse Adoptium OpenJDK 17.0.20.1 (`/home/hatch/jdk/jdk-17.0.20.1+1` and `/home/hatch/workspace/.toolchain/jdk-17.0.20.1+1-jre`)
- **Kotlin Compiler**: `kotlinc` 2.0.21 (`/home/hatch/workspace/.toolchain/kotlinc/bin/kotlinc`)
- **Android SDK Platforms**: Android API 35 SDK (`/home/hatch/android-sdk/platforms/android-35/android.jar`)
- **Android Build Tools**: Version 35.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`)
- **JUnit Platform**: ConsoleLauncher 1.10.1 (`junit-platform-console-standalone-1.10.1.jar`)

---

## 2. Exact Commands and Actual Outcomes

### Command 1: Baseline Test Suite Run
```bash
bash /home/hatch/agy-work/p50-verify.sh
```
**Outcome**:
```text
=== SUMMARY ===
      3 [         0 tests aborted         ]
      3 [         0 tests failed          ]
      3 [         0 tests skipped         ]
      1 [        42 tests successful      ]
      1 [       321 tests successful      ]
      1 [      1688 tests successful      ]
Total: 2,051 tests passed, 0 failed, 0 skipped. Exit code: 0.
```

### Command 2: Release Architecture & Version Tests
```bash
$JAVA -cp "$OUT/core-test:$OUT/core-main:src/main/resources:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$JUNIT_CONSOLE" \
    org.junit.platform.console.ConsoleLauncher \
    --select-class=com.omnibuds.core.architecture.DependencyDirectionTest \
    --select-class=com.omnibuds.core.release.ApplicationVersionTest
```
**Outcome**:
```text
Test run finished after 2344 ms
[         5 containers found      ]
[         5 containers successful ]
[        22 tests found           ]
[        22 tests successful      ]
[         0 tests failed          ]
Exit code: 0.
```

### Command 3: Full Release Build & Packaging Pipeline
```bash
bash scripts/release_build.sh
```
**Outcome**:
```text
=== [OmniBuds Release Build] Initializing directories ===
=== [1/5] Compiling :core (JVM 17 release) ===
=== [2/5] Packaging :core library JAR ===
=== [3/5] Compiling :platform:desktop and packaging desktop application JAR ===
=== [4/5] Building Android Library (.aar) ===
=== [5/5] Building Release Verification Companion Shell APK ===
NO_SIGNING_KEYS: RELEASE_KEYSTORE_PATH not provided or not found. Signed APK generation is safely BLOCKED.
=== [6/6] Generating Checksums and Release Manifest ===
=== Release build complete! Generated artifacts: ===
total 12M
-rw-rw---- 1 root nogroup  492 Oct 10 21:36 CHECKSUMS.sha256
-rw-rw---- 1 root nogroup 678K Oct 10 21:36 omnibuds-android-1.0.0.aar
-rw-rw---- 1 root nogroup 5.1K Oct 10 21:36 omnibuds-companion-shell-1.0.0-unsigned.apk
-rw-rw---- 1 root nogroup 2.8M Oct 10 21:34 omnibuds-core-1.0.0.jar
-rw-rw---- 1 root nogroup 5.6M Oct 10 21:35 omnibuds-desktop-1.0.0-linux-x64.tar.gz
-rw-rw---- 1 root nogroup 3.0M Oct 10 21:35 omnibuds-desktop-1.0.0.jar
-rw-rw---- 1 root nogroup 3.1K Oct 10 21:36 release-manifest.json
Exit code: 0.
```

### Command 4: Checksum Verification
```bash
cd build/release-dist && sha256sum -c CHECKSUMS.sha256
```
**Outcome**:
```text
omnibuds-android-1.0.0.aar: OK
omnibuds-companion-shell-1.0.0-unsigned.apk: OK
omnibuds-core-1.0.0.jar: OK
omnibuds-desktop-1.0.0-linux-x64.tar.gz: OK
omnibuds-desktop-1.0.0.jar: OK
All checksums verified bit-for-bit.
```

---

## 3. Real Generated Artifact Inventory

| Artifact Filename | Size (Bytes) | SHA-256 Digest |
|---|---|---|
| `omnibuds-core-1.0.0.jar` | 2,836,659 | `beaa941dce9b1e41d534a47eb01ec9e3114602d0b673611ec669e6164ac626e4` |
| `omnibuds-desktop-1.0.0.jar` | 3,086,902 | `ec6dc2791946b43c26e8a73ead853ba143b809cb24eaf7b661e9395816986b81` |
| `omnibuds-desktop-1.0.0-linux-x64.tar.gz` | 5,787,386 | `5f443b5764ed0ba76ac5e322600ef0c89257d2e04aa90d349d871c69b9238a10` |
| `omnibuds-android-1.0.0.aar` | 693,541 | `23cddb98323e0dc11ad06efc0994aab7b685ba81c0027368700b5dc323609731` |
| `omnibuds-companion-shell-1.0.0-unsigned.apk` | 5,149 | `8e5b9bd31309284753d4c7e9c2e5b8bbdfefa5d314f14860af09d812dff407d0` |
| `release-manifest.json` | 3,143 | (Validated v1 JSON schema) |
| `CHECKSUMS.sha256` | 492 | (Standard Unix format) |

---

## 4. Verification Gate Summary

- **Tests Passed**: 2,051 unit and architecture tests passed (100%).
- **Build Status**: All targets compiled with `-jvm-target 17` and `-Werror`.
- **Packaging Status**: All 5 expected distribution packages generated with valid non-zero sizes.
- **Signing Status**: Safely blocked in the absence of private production keys; unsigned artifacts clearly marked.
- **Hardware Principle**: Strictly maintained; zero fake states or mocks in release code.
- **Physical Verification**: Explicitly deferred to Phase 52.
