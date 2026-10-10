# Phase 45 — Initial Audit: Firmware Compatibility & Device Revision Management

## 1. Audit Context and Objective

OmniBuds operates under the core product principle:
> **OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device.**

In Phase 44, protocol versioning and compatibility resolution were implemented to handle protocol wire revisions, transport constraints, and protocol schema evolution. However, firmware handling remained preliminary:
- `CompatibilityResolver` accepted `observedFirmware: String?` and filtered against `candidate.verifiedFirmware: Set<String>?`.
- In `VendorIntegrationContract`, `FirmwareCompatibilityEvaluator` simply tested string inclusion in a set (`observed in verified`).
- In `FirmwareInfo`, version fields were held as unparsed `String?` values with a `VerificationLevel`.
- Across several vendor adapters (e.g. Sony, Bose, Apple, Samsung), devices in the same product family or model line were often assumed to have identical capabilities regardless of the firmware revision running on the earbud.
- No typed firmware versioning scheme existed (numeric, semantic, build/date-based, opaque/hash-like).
- No observation provenance model tracked freshness, observation source (device query, cached metadata, user-entered, fixture), or observation invalidation.
- Stale or changed firmware reports did not cleanly trigger re-evaluation of protocol selection, capability availability, or cancellation of in-flight mutating operations.

This audit establishes the baseline across the codebase prior to Phase 45 implementation.

## 2. Review of Existing Firmware Architecture

### 2.1 Firmware Representation
- `com.omnibuds.core.device.FirmwareInfo`:
  Contains `firmwareVersion: String?`, `hardwareRevision: String?`, `protocolVersion: String?`, and `verification: VerificationLevel`.
  *Strengths*: Clearly establishes that null means "not read", forbids default fabricated values (e.g. "0.0.0"), and keeps firmware out of `DeviceFingerprint.identityKey` (so updating firmware does not treat the earbud as a new identity).
  *Limitations*: String-based without structural parsing; lacks timestamps, observation sources (provenance), freshness windows, and validation states.

### 2.2 Protocol Versioning Integration (Phase 44)
- `com.omnibuds.core.protocol.version.ProtocolIdentity`:
  Carries `verifiedFirmware: Set<String>? = null`.
- `com.omnibuds.core.protocol.version.CompatibilityResolver`:
  Resolves protocols deterministically. Checks `observedFirmware: String?`. If non-null, filters candidates where `candidate.verifiedFirmware == null || observedFirmware in candidate.verifiedFirmware`.
  *Limitations*: Exact string equality only; no support for firmware version ranges, build numbers, denylists, known buggy firmware revisions, or firmware-specific capability masks.

### 2.3 Vendor Integration & Capability Systems
- `com.omnibuds.core.vendor.VendorIntegrationContract`:
  Has `FirmwareCompatibilityEvaluator` returning `VERIFIED`, `UNKNOWN_FIRMWARE`, `INCOMPATIBLE`.
- `com.omnibuds.core.capability.FeatureRegistry`:
  Capabilities are registered per feature ID and model, but firmware-conditional capabilities were handled ad-hoc or assumed available across all firmware revisions of a model.
- `com.omnibuds.core.state.GlobalDeviceState`:
  Centralizes capability state, but lacks an automated mechanism to invalidate firmware-dependent capability claims when the observed firmware revision changes or becomes stale.

### 2.4 Operation Authorization Pipeline
- Central authorization pipeline:
  `IDENTIFY → OBSERVE FIRMWARE → RESOLVE PROTOCOL → CHECK FIRMWARE COMPATIBILITY → DISCOVER CAPABILITY → PLAN → AUTHORIZE → EXECUTE → RECONCILE → VERIFY`.
  *Observation*: If firmware is unknown, mutating operations must be blocked unless explicitly safe and policy-permitted. Ambiguous or stale firmware must not permit execution of version-sensitive or firmware-sensitive commands.

## 3. Assumptions and Deficiencies Identified

1. **Family-Level Equivalence Assumption**: Prior code occasionally treated all firmware versions of a product model (e.g. WF-1000XM4) as identical in capability support. In reality, firmware updates frequently introduce, alter, or break protocol commands (e.g. multipoint, speak-to-chat, codec switching).
2. **Untyped Firmware Versions**: String equality fails when firmware versions are SemVer (`2.0.1`), build numbers (`4A400`), date codes (`20231015`), or prefixed strings (`v1.4.2`).
3. **Lack of Provenance Tracking**: Firmware observations lacked timestamps, source tracking (device GATT read vs. user-provided vs. cache), and validation against malicious/overflow strings.
4. **No Invalidation on Firmware Shift**: When a device re-identifies with a different firmware version, dependent protocol and capability resolutions were not proactively invalidated or flushed.
5. **Missing Explicit Compatibility Outcomes**: Outcomes like `STALE_FIRMWARE_OBSERVATION`, `INVALID_FIRMWARE`, and `FIRMWARE_RANGE_MISMATCH` were collapsed into generic incompatible or unknown outcomes.

## 4. Phase 45 Architecture Strategy

1. **Typed Firmware Models**:
   - `FirmwareVersion`: Sealed hierarchy (`Semantic`, `BuildNumber`, `DateBased`, `Opaque`, `Unknown`).
   - `FirmwareScheme`: Enum defining parsing and comparison behavior.
   - `FirmwareObservation`: Encapsulates `deviceId`, raw & normalized versions, `FirmwareObservationSource`, `timestamp`, `freshnessMs`, `verificationLevel`.
2. **Firmware Compatibility Rule System**:
   - `FirmwareCompatibilityRule`: Rule ID, model scope, firmware constraint (`Exact`, `Set`, `Range`, `IncompatibleSet`), protocol scope, outcome, evidence reference.
   - `FirmwareConstraint`: Structured constraint supporting exact, allowlist, range (when ordered), denylists.
3. **Firmware-Aware Compatibility Resolver**:
   - Deterministic evaluation factoring in model scope, observation freshness, rule conflicts, and strict policy gating.
4. **Firmware-Dependent Capability Management**:
   - Scoped capability descriptors with required firmware constraints.
   - Capability state invalidator for firmware change events.
5. **Operation Safety & Central Authorization**:
   - Firmware compatibility verification integrated directly into operation pre-conditions.
   - Mutating operations blocked when firmware is unverified, stale, or incompatible.
6. **Persistence & Migration**:
   - Versioned schema for firmware compatibility rules and historical observations.
7. **Regression and Test Suite**:
   - Full suite across parsing, constraints, resolution, capability invalidation, operation gating, and security.

All changes will be tested offline without real Bluetooth hardware.
