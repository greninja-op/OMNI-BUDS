# Phase 45 — Specifications: Firmware Compatibility Models & Interfaces

## 1. Type Specifications

### 1.1 `FirmwareVersion`
- Defined in `com.omnibuds.core.firmware.FirmwareVersion`.
- Sealed interface extending `Comparable<FirmwareVersion>`.
- Members:
  - `val rawValue: String`
  - `val scheme: FirmwareScheme`
- Implementations: `Semantic`, `BuildNumber`, `DateBased`, `AlphanumericBuild`, `Opaque`, `Unknown`.

### 1.2 `FirmwareConstraint`
- Sealed interface in `com.omnibuds.core.firmware.FirmwareConstraint`.
- Method: `fun isSatisfiedBy(version: FirmwareVersion): Boolean`.
- Subtypes:
  - `Any`: matches all versions.
  - `Exact(expected: FirmwareVersion)`: matches exact version or case-insensitive raw value.
  - `Allowlist(allowed: Set<FirmwareVersion>)`: matches any element in allowlist.
  - `Range(minInclusive: FirmwareVersion?, maxInclusive: FirmwareVersion?)`: matches if within bounds for same scheme.
  - `Denylist(blocked: Set<FirmwareVersion>)`: rejects any element in blocked set.
  - `AtLeast(min: FirmwareVersion)`: matches if `version >= min` within same scheme.

### 1.3 `FirmwareObservation`
- Data class in `com.omnibuds.core.firmware.FirmwareObservation`.
- Fields:
  - `deviceId: String`
  - `rawValue: String?`
  - `version: FirmwareVersion`
  - `source: FirmwareObservationSource`
  - `observedAtMs: Long`
  - `verificationLevel: VerificationLevel`
  - `hardwareRevision: String?`
  - `protocolScope: String?`
  - `validationState: FirmwareValidationState`
  - `limitations: List<String>`
- Methods: `isFresh(currentTimeMs: Long, maxAgeMs: Long): Boolean`, `val isTrustworthyForMutations: Boolean`.

### 1.4 `FirmwareCompatibilityResolver`
- Class in `com.omnibuds.core.firmware.FirmwareCompatibilityResolver`.
- Primary method:
  ```kotlin
  fun resolve(
      candidates: List<ProtocolIdentity>,
      fingerprint: DeviceFingerprint,
      identification: IdentificationResult,
      observedVersion: ProtocolVersion,
      observation: FirmwareObservation,
      availableTransports: Set<TransportKind>,
      deviceModelId: String? = null,
      targetScope: String? = null,
      currentTimeMs: Long = observation.observedAtMs,
  ): FirmwareCompatibilityResult
  ```

### 1.5 `FirmwareOperationGate`
- Object in `com.omnibuds.core.firmware.FirmwareOperationGate`.
- Method:
  ```kotlin
  fun authorizeOperation(
      isMutating: Boolean,
      featureId: FeatureId,
      compatibilityResult: FirmwareCompatibilityResult,
      capabilityState: CapabilityState,
      operationFirmwareConstraint: FirmwareConstraint? = null,
      observedVersion: FirmwareVersion = FirmwareVersion.Unknown,
  ): FirmwareAuthorizationDecision
  ```
