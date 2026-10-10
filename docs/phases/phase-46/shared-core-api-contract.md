# Phase 46 — Shared Core Public API Contracts

## 1. Domain Modeling Contracts

### 1.1 Device Identity & Fingerprinting
- **`DeviceIdentity`**: Immutable representation of an earbud/headphone device. Carries vendor name, model name, device identifier, and verification level.
- **`DeviceFingerprint`**: Normalizes MAC addresses and vendor IDs into deterministic identity keys without exposing raw hardware addresses.
- **`IdentityEngine`**: Pure evidence-based reconciliation of device identity rules. Never fabricates identity from unverified signals.

### 1.2 Protocol & Version Management
- **`ProtocolIdentity`**: Canonical protocol identifier, wire version, transport affinity, and verified firmware bounds.
- **`CompatibilityResolver`**: Deterministic resolution of candidate protocols given device identity and observed firmware.
- **`FirmwareVersion` & `FirmwareCompatibilityResolver`**: Sealed hierarchy of firmware revisions and evidence-backed gating of mutating commands.

### 1.3 Capability & State Management
- **`FeatureRegistry`**: Registry of supported hardware features (`FeatureId`).
- **`GlobalDeviceState`**: Immutable snapshot aggregating connection status, battery, active codecs, verified feature controls, and operation states.
- **`OperationOutcome<T>`**: Typed result container (`Success<T>`, `Failure`). Bounded errors with typed categories (`OmniBudsErrorCategory`).

---

## 2. Multiplatform Platform Contracts (`com.omnibuds.core.platform`)

1. **`PlatformType`**: Identifies OS environment (`ANDROID`, `LINUX`, `MACOS`, `WINDOWS`, `DESKTOP_GENERIC`, `UNKNOWN`).
2. **`PlatformDescriptor`**: Encapsulates OS name, version, architecture, candidate transports, and capability snapshot.
3. **`PlatformIdentifierSource`**: Supplies unique, correlated `operationId` strings and random nonces.
4. **`PlatformStoragePort`**: Key-value asynchronous store for settings and device records.
5. **`PlatformLifecycleSource`**: Cold flow and snapshot of application process lifecycle states.
6. **`PlatformDiagnosticSink`**: Diagnostic telemetry sink with pre-emission redaction.
7. **`PlatformTransportFactory`**: Platform seam for opening physical transport sessions without referencing Bluetooth sockets.
