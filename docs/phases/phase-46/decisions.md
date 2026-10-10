# Phase 46 — Architectural Decisions: Kotlin Multiplatform Core & Platform Independence

## Decision Record 1: Layer 1 Seam Model for Platform Abstractions
- **Context**: The shared core (`:core`) requires platform facilities such as clock time, operation identifiers, storage access, diagnostic sinks, and lifecycle signals. Coupling to Android-specific APIs (`System.currentTimeMillis()`, `java.util.UUID`, `SharedPreferences`, `android.util.Log`, `ActivityLifecycleCallbacks`) breaks multiplatform portability.
- **Decision**: Define minimal, typed interfaces in `com.omnibuds.core.platform` (Layer 1). Core engines depend only on these abstractions; platform modules implement them.
- **Consequence**: Keeps the shared core 100% free of platform classes while providing explicit contracts for Android and future desktop targets.

---

## Decision Record 2: Preservation of Existing Core Source Tree
- **Context**: KMP conventions often rename `src/main/kotlin` to `src/commonMain/kotlin`. In OmniBuds, over 20 architectural scope tests across Phases 0–45 strictly scan relative paths starting from `src/main/kotlin`. Moving files to `src/commonMain/kotlin` would either break those tests or require weakening them.
- **Decision**: Keep the source tree in `src/main/kotlin` targeting JVM 17 as the portable core foundation. Register the `kotlin-multiplatform` plugin in `gradle/libs.versions.toml` to formalize the target matrix, while compiling the shared core directly via the supported toolchain.
- **Consequence**: Preserves 100% of existing architecture tests and guarantees complete backward compatibility without artificial restructuring.

---

## Decision Record 3: Explicit PlatformType and PlatformDescriptor Hierarchy
- **Context**: `BluetoothPlatformCapabilities` historically included only `val apiLevel: Int?`, assuming an Android execution environment. Desktop operating systems (Linux, macOS, Windows) have no API level concept.
- **Decision**: Introduce `PlatformType` enum (`ANDROID`, `LINUX`, `MACOS`, `WINDOWS`, `DESKTOP_GENERIC`, `UNKNOWN`) and `PlatformDescriptor`. Add `platformType: PlatformType = PlatformType.UNKNOWN` with a default parameter to `BluetoothPlatformCapabilities`.
- **Consequence**: Enables desktop platforms to report their OS and architecture without fabricating an Android API level, while maintaining full binary and source compatibility for existing Android call sites.

---

## Decision Record 4: Flow-Based Platform Lifecycle Signals
- **Context**: Process lifecycle was previously tracked via `OmniBudsLifecycleMonitor` implementing Android's `Application.ActivityLifecycleCallbacks`. Desktop applications run with different windowing and process models.
- **Decision**: Introduce `PlatformLifecycleState` (`FOREGROUND`, `BACKGROUND`, `SUSPENDED`, `TERMINATING`) and `PlatformLifecycleSource` exposing a cold `Flow<PlatformLifecycleState>` and a snapshot `currentState`.
- **Consequence**: Enables core lifecycle coordinators to observe process transitions without importing Android lifecycle classes.

---

## Decision Record 5: Deferral of Desktop Bluetooth Implementation
- **Context**: The Phase 46 prompt requires preparing the core for desktop Bluetooth, but explicitly forbids implementing the desktop adapter in this phase.
- **Decision**: Establish the platform seams (`PlatformTransportFactory`, `PlatformConnectionSession`, `PlatformDescriptor`) in Phase 46. Defer the Linux BlueZ, macOS CoreBluetooth, and Windows desktop Bluetooth implementations to Phase 47.
- **Consequence**: Strictly adheres to the phase boundary and keeps changes reviewable and focused.
