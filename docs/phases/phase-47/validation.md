# Phase 47 — Validation & Test Execution Report

## 1. Execution Summary

All automated validations were executed against the offline toolchain in `/home/hatch/agy-work/omnibuds`.

- **Compiler**: `kotlinc 2.0.21` at `/home/hatch/workspace/.toolchain/kotlinc/bin/kotlinc`
- **Compiler Flags**: `-jvm-target 17 -Werror`
- **Java Runtime**: JDK 17 (`/home/hatch/workspace/.toolchain/jdk-17.0.20.1+1-jre/bin/java`)
- **Test Runner**: JUnit Platform Console Standalone 1.10.1 (`-e junit-jupiter`)
- **Total Tests Executed**: 1949
- **Total Tests Passed**: 1949
- **Failed / Aborted / Skipped**: 0 / 0 / 0

---

## 2. Test Execution Breakdown

### 2.1 Core Module (`:core`)
- Working Directory: `/home/hatch/agy-work/omnibuds/core`
- Containers: 229 found, 229 started, 229 successful
- Tests: **1674 found, 1674 started, 1674 successful (0 failed)**
- Net Delta: **+12 new unit tests** in `DesktopBluetoothLayerTest` over 1662 baseline.

### 2.2 Android Platform Module (`:platform:android`)
- Working Directory: `/home/hatch/agy-work/omnibuds/platform/android`
- Containers: 33 found, 33 started, 33 successful
- Tests: **275 found, 275 started, 275 successful (0 failed)**
- Net Delta: 0 (no regressions, all existing Android platform tests pass).

---

## 3. Architecture & Security Invariant Checks

- `DependencyDirectionTest`: Passed with zero violations.
  - Zero imports of `java.*`, `javax.*`, `android.*` in `core/src/main/kotlin`.
  - Zero test doubles defined in `core/src/main/kotlin`.
  - Zero magic protocol literals.
  - Pure downward layer dependencies (Layer 0 through 6).

---

## 4. Hardware Testing Statement

In strict adherence to project constraints:
- **No physical Bluetooth radio, phone, or earbud hardware was accessed or required.**
- All validation ran using offline deterministic test doubles and isolated event sources.
- Physical device testing is deferred exclusively to Phase 52.
