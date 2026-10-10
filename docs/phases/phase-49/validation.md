# Phase 49 — Android UI Validation & Test Report

## 1. Toolchain & Environment Specifications

- **Operating System**: Linux 6.6.137+
- **Kotlin Compiler**: `kotlinc` 2.0.21 (`~/workspace/.toolchain/kotlinc/bin/kotlinc`)
- **Java Runtime**: OpenJDK 17.0.20.1 (`~/workspace/.toolchain/jdk-17.0.20.1+1-jre/bin/java`)
- **Android Target**: Android API 35 SDK (`android.jar` at `~/android-sdk/platforms/android-35/android.jar`)
- **AAPT2 Binary**: Version 35.0.0 (`~/android-sdk/build-tools/35.0.0/aapt2`)
- **Bytecode Target**: `-jvm-target 17`
- **Compiler Flags**: `-Werror` (warnings treated as errors)
- **JUnit Platform**: ConsoleLauncher 1.10.1 (`junit-platform-console-standalone-1.10.1.jar`)
- **Coroutines Core & Test**: `kotlinx-coroutines-core-jvm-1.9.0.jar`, `kotlinx-coroutines-test-jvm-1.9.0.jar`

---

## 2. Exact Execution Commands

### Core Target (Main, Tests & Regression)
```bash
kotlinc $(find core/src/main/kotlin -name "*.kt") -d "$OUT/core-main" -jvm-target 17 -Werror -cp "$STDLIB:$COROUTINES"
kotlinc $(find core/src/test/kotlin -name "*.kt") -d "$OUT/core-test" -jvm-target 17 -Xfriend-paths="$OUT/core-main" \
  -cp "$OUT/core-main:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$JUNIT_CONSOLE"

(
  cd core
  java -cp "$OUT/core-test:$OUT/core-main:src/main/resources:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$JUNIT_CONSOLE" \
    org.junit.platform.console.ConsoleLauncher --scan-classpath="$OUT/core-test" --include-engine=junit-jupiter --fail-if-no-tests
)
```

### Android Target (Main, Resources & Tests)
```bash
# Link Android Resources
aapt2 compile --dir platform/android/src/main/res -o "$OUT/res.zip"
aapt2 link -o "$OUT/linked.apk" -I "$ANDROID_JAR" --manifest "$OUT/manifest-tmp/AndroidManifest.xml" --java "$OUT/rgen" "$OUT/res.zip"

# Compile Android Main (excluding compose files due to offline plugin version)
MAIN_SOURCES=$(find platform/android/src/main/kotlin -name "*.kt" ! -path "*/ui/compose/*")
R_JAVA=$(find "$OUT/rgen" -name "R.java")
kotlinc $MAIN_SOURCES $R_JAVA -d "$OUT/android-main" \
  -jvm-target 17 -Werror -no-stdlib -cp "$STDLIB:$COROUTINES:$ANDROID_JAR:$OUT/core-main"

# Compile Android Tests
kotlinc $(find platform/android/src/test/kotlin -name "*.kt") -d "$OUT/android-test" -jvm-target 17 -Xfriend-paths="$OUT/android-main" \
  -cp "$OUT/android-main:$OUT/core-main:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$ANDROID_JAR:$JUNIT_CONSOLE"

# Execute Android Tests
java -cp "$OUT/android-test:$OUT/android-main:$OUT/core-main:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$ANDROID_JAR:$JUNIT_CONSOLE" \
  org.junit.platform.console.ConsoleLauncher --scan-classpath="$OUT/android-test" --include-engine=junit-jupiter --fail-if-no-tests
```

### Desktop Target (Regression)
```bash
kotlinc $(find platform/desktop/src/main/kotlin -name "*.kt") -d "$OUT/desktop-main" -jvm-target 17 -Werror \
  -cp "$STDLIB:$COROUTINES:$OUT/core-main"
kotlinc $(find platform/desktop/src/test/kotlin -name "*.kt") -d "$OUT/desktop-test" -jvm-target 17 \
  -Xfriend-paths="$OUT/desktop-main" -cp "$OUT/desktop-main:$OUT/core-main:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$JUNIT_CONSOLE"

java -cp "$OUT/desktop-test:$OUT/desktop-main:$OUT/core-test:$OUT/core-main:core/src/main/resources:$STDLIB:$COROUTINES:$COROUTINES_TEST:$KOTLIN_TEST:$JUNIT_CONSOLE" \
  org.junit.platform.console.ConsoleLauncher --scan-classpath="$OUT/desktop-test" --include-engine=junit-jupiter --fail-if-no-tests
```

---

## 3. Test Pass / Fail / Skip Metrics

| Target Suite | Tests Found | Tests Passed | Tests Failed | Tests Skipped | Duration | Status |
|---|---|---|---|---|---|---|
| **Core Suite** (`:core`) | 1,674 | 1,674 | 0 | 0 | ~15.2s | **PASS** |
| **Android Suite** (`:platform:android`) | 314 | 314 | 0 | 0 | ~2.1s | **PASS** |
| **Desktop Suite** (`:platform:desktop`) | 37 | 37 | 0 | 0 | ~2.3s | **PASS** |
| **Total Unified Regression** | **2,025** | **2,025** | **0** | **0** | **~19.6s** | **ALL PASS (100%)** |

---

## 4. Architectural Verification

- **Dependency Inversion**: Core architecture test `DependencyDirectionTest` passed with 0 violations. Core maintains 0 direct dependencies on Android UI packages.
- **Strict Compilation**: Both `:core`, `:platform:android`, and `:platform:desktop` compiled with `-Werror` (warnings treated as errors).
- **Offline Integrity**: Executed entirely within local container without external network connections or running display server.
