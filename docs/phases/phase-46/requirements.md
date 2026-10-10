# Phase 46 — Requirements Specification: Kotlin Multiplatform Core & Platform Independence

## 1. Executive Summary

Phase 46 establishes a stable, reusable Kotlin Multiplatform (KMP) shared core for OmniBuds. The objective is to enable multi-platform support across Android and future desktop operating systems (Linux, macOS, Windows) without duplicating business logic, introducing platform leaks into the domain, or compromising the Bluetooth safety invariants established across Phases 0–45.

---

## 2. Core Product Principles & Invariants

1. **Zero Hardware Simulation**: OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device.
2. **Honest Capability Accounting**: Unknown values remain `UNKNOWN` and never default to `UNSUPPORTED` or fabricated defaults (e.g. battery 0% or fake version strings).
3. **Downward-Only Dependency Direction**: Architectural dependencies flow downward only (Layer 0 to Layer 6). The core domain must never import platform modules.
4. **Zero Platform Leaks in Core**: The shared core must contain zero imports of `android.*`, `androidx.*`, `com.omnibuds.android.*`, `java.*`, or `javax.*`.
5. **No Desktop Bluetooth in Phase 46**: The desktop Bluetooth layer is strictly deferred to Phase 47.
6. **No Physical Hardware Verification**: All tests must run in offline, headless, deterministic environments. Physical device verification is deferred to Phase 52.

---

## 3. Functional Requirements

### OB-P46-REQ-001: Platform Identity and Descriptor
- The shared core shall define a typed `PlatformType` enum distinguishing `ANDROID`, `LINUX`, `MACOS`, `WINDOWS`, `DESKTOP_GENERIC`, and `UNKNOWN`.
- The system shall provide an immutable `PlatformDescriptor` encapsulating `PlatformType`, OS version string, system architecture, candidate transports, and detailed Bluetooth platform capabilities.

### OB-P46-REQ-002: Stable Identifier Generation Seam
- The system shall define a `PlatformIdentifierSource` interface to generate non-blank, correlated operation identifiers (`operationId`) and random nonces.
- The shared core shall provide a `DeterministicIdentifierSource` for repeatable offline test execution.

### OB-P46-REQ-003: Platform Storage Seam
- The system shall establish an asynchronous, key-value storage port (`PlatformStoragePort`) to decouple persistence models from underlying OS storage mechanisms (Android SharedPreferences / DataStore vs. Desktop flat files / SQLite).
- The shared core shall include an `InMemoryStoragePort` satisfying the contract for headless and test environments.

### OB-P46-REQ-004: Platform Lifecycle Decoupling
- The system shall define a platform-independent `PlatformLifecycleState` (`FOREGROUND`, `BACKGROUND`, `SUSPENDED`, `TERMINATING`) and `PlatformLifecycleSource` contract.
- Platform adapters shall translate native OS lifecycle transitions into these portable signals without leaking activity or window objects into core.

### OB-P46-REQ-005: Diagnostic Sink Seam
- The system shall define `PlatformDiagnosticSink` to accept telemetry and log events from core logic.
- Implementations must enforce pre-emission redaction and fail-safe swallowing of platform logging errors.

### OB-P46-REQ-006: Platform Transport Factory Seam
- The system shall define `PlatformTransportFactory` and `PlatformConnectionSession` contracts, enabling future desktop transports (Phase 47) and Android transports to implement unified session boundaries.

---

## 4. Non-Functional Requirements

### OB-P46-NFR-001: Portability & Toolchain Discipline
- The shared core must compile cleanly with Kotlin 2.0.21 (`-jvm-target 17 -Werror`) without deprecation slips or unchecked casts.
- The Gradle version catalog must declare the `kotlin-multiplatform` plugin (`org.jetbrains.kotlin.multiplatform`).

### OB-P46-NFR-002: Concurrency & Thread Safety
- All shared interfaces and state flows must use pure Kotlin Coroutines and Flows (`kotlinx.coroutines`).
- Platform dispatchers must be decoupled; no Android `Dispatchers.Main` leaks into shared code.

### OB-P46-NFR-003: Deterministic Test Preservation
- 100% of existing unit tests (1655 core + 272 android = 1927 baseline) must continue to pass without modification or weakening.
- New KMP abstractions must have dedicated, deterministic unit tests.

### OB-P46-NFR-004: Zero Regressions
- No existing public API contract in `:core` or `:platform:android` shall be broken.
- Property additions (such as `platformType` on `BluetoothPlatformCapabilities`) must provide backwards-compatible default arguments.
