# Phase 46 — Kotlin Multiplatform Target Support Matrix

## 1. Supported Platform Targets

OmniBuds targets portable execution across mobile and desktop host operating systems:

| Target Identifier | Platform Type | Operating Systems | Status in Phase 46 | Execution Mechanism |
|---|---|---|---|---|
| `android` | Mobile | Android API 26 (8.0) to API 35 (15) | **Fully Verified** | `:platform:android` (JVM 17 / ART) |
| `desktopJvm` | Desktop | Linux, macOS, Windows | **Seams Ready** | `:core` JVM 17 bytecode + platform interfaces; no desktop implementations yet (Phase 47–48) |
| `linuxX64` | Native / Embedded | Linux (glibc) | **Deferred** | Planned for native BlueZ daemon |
| `macosArm64 / macosX64`| Desktop | macOS (Apple Silicon & Intel) | **Deferred** | Planned for CoreBluetooth daemon |
| `windowsX64` | Desktop | Windows 10 / 11 | **Deferred** | Planned for WinRT Bluetooth daemon |

---

## 2. Source Set Topology

> **Honesty note (Phase 46 actual state):** No `commonMain` / `androidMain` / `desktopMain`
> source sets were created in this phase, and the `kotlin-multiplatform` Gradle plugin is
> declared in `gradle/libs.versions.toml` but **not applied** to any module. The diagram
> below shows the *intended* future topology, not the current build configuration.
>
> Rationale (see `decisions.md`): the repository builds with standalone `kotlinc`
> (the Gradle daemon is non-functional in this environment), so applying the KMP plugin
> would create an unverifiable configuration claim. Renaming `src/main/kotlin` to
> `src/commonMain/kotlin` would also break 20+ architecture scope tests that scan
> relative paths. The `:core` module is already 100% platform-independent Kotlin
> (mechanically enforced by `DependencyDirectionTest`: no `android.*`, `androidx.*`,
> `java.*`, or `javax.*` imports), compiled to JVM 17 bytecode that runs unchanged on
> desktop JVMs. Full KMP source-set configuration is deferred until the Gradle
> toolchain is functional.

```
                  ┌─────────────────┐
                  │   commonMain    │  (INTENDED — not yet configured)
                  └────────┬────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
    ┌─────────────────┐         ┌─────────────────┐
    │   androidMain   │         │   desktopMain   │
    │ (:platform:android)       │ (Future Phase 47)│
    └─────────────────┘         └─────────────────┘
```

**What Phase 46 actually delivered toward this topology:**
- Platform-abstraction seams in `:core` (`PlatformType`, `PlatformDescriptor`,
  `PlatformIdentifierSource`, `PlatformLifecycleSource`, `PlatformStoragePort`,
  `PlatformTransportFactory`, `PlatformDiagnosticSink`) — pure Kotlin, zero
  platform imports.
- Android implementations of those seams in `:platform:android`.
- `BluetoothPlatformCapabilities.platformType` populated by the Android provider.
- The `kotlin-multiplatform` plugin version registered for future use.

---

## 3. Toolchain & Runtime Constraints

- **Kotlin Version**: `2.0.21`
- **JVM Target**: JVM 17
- **Coroutines Version**: `1.9.0`
- **Current Offline Build Execution**:
  - The repository's offline sandbox build uses standalone `kotlinc 2.0.21` compiling `:core` for JVM 17.
  - Desktop JVM targets share 100% bytecode compatibility with JVM 17.
  - Native Kotlin/Native compilation targets are deferred until desktop platform modules are introduced in Phases 47–48.
