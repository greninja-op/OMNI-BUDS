# Rebuilding OmniBuds

This document details the environment requirements and procedures to cleanly rebuild the OmniBuds platform, its subprojects, and test suites from source.

---

## 1. Prerequisites

| Tool | Version Requirement | Purpose |
|---|---|---|
| **JDK (Java Development Kit)** | JDK 17 (e.g. OpenJDK 17, Eclipse Temurin 17) | Compiles Kotlin/JVM `:core`, `:platform:android`, and `:tools:companion-shell` |
| **Android SDK** | API Platform 35, Build-Tools 35.0.0+ | Required for Android platform module and companion shell |
| **Python** | Python 3.9+ (Standard Library only) | Host test harness and ADB device bridge in `tools/device-bridge` |
| **Gradle** | Handled via `./gradlew` / `gradlew.bat` (Gradle 8.9 pinned) | Build automation and dependency resolution |

---

## 2. Environment Configuration

### Android SDK (`local.properties`)
Create or edit `local.properties` in the repository root (git-ignored, never committed). A template is provided in `local.properties.example`:

```properties
sdk.dir=C:/Users/<Username>/AppData/Local/Android/Sdk
```
*(On macOS: `/Users/<Username>/Library/Android/sdk`, on Linux: `/home/<Username>/Android/Sdk`)*

### Java Configuration (`JAVA_HOME`)
Ensure `JAVA_HOME` points to your JDK 17 installation:

- **Windows (Command Prompt / PowerShell):**
  ```cmd
  set JAVA_HOME=C:\Users\<Username>\AppData\Local\jdk-17
  set PATH=%JAVA_HOME%\bin;%PATH%
  ```
- **macOS / Linux:**
  ```bash
  export JAVA_HOME=/path/to/jdk-17
  export PATH=$JAVA_HOME/bin:$PATH
  ```

---

## 3. Quick One-Step Automated Rebuild

To verify prerequisites, compile all modules, and execute both Gradle and Python test suites in one step:

- **Windows:**
  ```cmd
  rebuild.bat
  ```
- **macOS / Linux:**
  ```bash
  chmod +x rebuild.sh
  ./rebuild.sh
  ```

---

## 4. Manual Step-by-Step Build & Test Commands

### 4.1. Clean Build Cache
Remove previous build outputs and caches across all modules:
```bash
./gradlew clean
```

### 4.2. Compile and Assemble All Modules
```bash
# Compile and build all project artifacts
./gradlew build

# Or assemble specific modules
./gradlew :core:assemble
./gradlew :platform:android:assembleDebug
./gradlew :tools:companion-shell:assembleDebug
```

### 4.3. Run Test Suites
```bash
# Run all JVM test suites
./gradlew test

# Run domain core tests only
./gradlew :core:test

# Run Android platform mechanism tests (JVM seam tests)
./gradlew :platform:android:testDebugUnitTest
```

### 4.4. Static Analysis & Linting
```bash
# Run Android lint checks
./gradlew :platform:android:lintDebug
```

### 4.5. Run Device Bridge Offline Self-Check (Python)
The device bridge operates strictly on Python standard library without third-party dependencies:
```bash
# Run offline verification suite
python tools/device-bridge/verify_suite.py

# Or discover and run via unittest runner
python -m unittest discover -s tools/device-bridge/tests
```

---

## 5. Cleaning Disk Space (Rebuildable Artifacts)

To free disk space safely without touching tracked files or local configuration:
- Clean Gradle build outputs:
  ```cmd
  rmdir /s /q .gradle
  rmdir /s /q .kotlin
  rmdir /s /q core\build
  rmdir /s /q platform\android\build
  rmdir /s /q tools\companion-shell\build
  ```
- Clean Python cache:
  ```cmd
  del /s /q /f *.pyc
  Get-ChildItem -Path . -Recurse -Directory -Filter "__pycache__" | Remove-Item -Recurse -Force
  ```
- Tracked sources, `local.properties`, and any `.env` files are never touched by this cleanup.
