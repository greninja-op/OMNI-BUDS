# Phase 43 — Test Plan: Community Protocol SDK

**Status:** APPROVED  

---

## 1. Test Categories

### 1.1 API Contract & Versioning Tests
- `SdkVersion`: parsing valid and invalid strings, major/minor/patch comparison.
- `CommunityEvidenceRecord`: anti-self-promotion validation rejecting `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`.
- `CommunityCapabilityDeclaration`: rejection of `PERSISTENCE_VERIFIED` and invalid tiers.

### 1.2 Package Validation Tests
- Acceptance of fully formed, valid packages.
- Detection of invalid adapter identifiers, blank display names, incompatible SDK versions.
- Detection of duplicate capability declarations and circular self-dependencies.
- Detection of undeclared features referenced by operations.

### 1.3 Conformance Runner & Reference Adapter Tests
- Conformance validation across adapter properties, non-throwing match behavior, operation-feature consistency.
- Fingerprint matching on synthetic company ID 65534 (`0xFFFE`) and rejection of unmatched devices.
- Request serialization and response parsing for mutating ANC commands and read-only battery queries.
- Safe structured failure handling on empty or malformed byte payloads.

### 1.4 Transport Testkit Tests
- Deterministic execution of `ScriptedFakeTransport` steps: `Respond`, `Fail`, `Disconnect`.
- Verifying request recording and fail-closed behavior on script exhaustion and after disconnection.

### 1.5 Architecture & Regression Tests
- Mechanical verification via `DependencyDirectionTest`:
  - Layer 6 registration of `sdk` area.
  - Zero imports of Android frameworks in core.
  - Zero JVM-only imports in core main sources.
  - Absence of hardcoded protocol hex literals in source code.
  - Downward-only dependencies.
- Full regression suite across core and platform modules.
