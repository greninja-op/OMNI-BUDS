# Phase 43 — Requirements: Community Protocol SDK

**Status:** APPROVED  
**Architecture Area:** `com.omnibuds.core.sdk` (Layer 6)  
**Execution Mode:** Deterministic, offline, simulated fixtures, zero physical device connections.

---

## 1. Functional Requirements

### OB-P43-REQ-001: Stable Public SDK Contract
The SDK shall define typed Kotlin interfaces and value classes under `com.omnibuds.core.sdk.api` to represent external community protocol integrations without exposing internal OmniBuds engine details.

### OB-P43-REQ-002: Versioning & Compatibility Declarations
All community integrations shall declare an explicit `SdkVersion` and `integrationVersion`. Semantic versioning rules shall deterministically reject incompatible major SDK versions (`major != SdkVersion.CURRENT.major`).

### OB-P43-REQ-003: Provenance & Evidence Tracking
Every integration shall provide a complete `CommunityEvidenceRecord` stating contributor identifier, protocol version, evidence source, last reviewed date, and test fixture references.

### OB-P43-REQ-004: Anti-Self-Promotion Boundary
The SDK contracts and package validator shall strictly forbid community integrations from self-declaring `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED` states in metadata. Maximum self-declared evidence level is `LAB_TESTED`.

### OB-P43-REQ-005: Static Package Validation
The SDK shall provide an offline, static package validator (`CommunityPackageValidator`) that inspects metadata, identifiers, capability declarations, and dependencies without executing submitted user code.

### OB-P43-REQ-006: Developer Testing Toolkit
The SDK shall provide deterministic offline test facilities (`ScriptedFakeTransport`, `CommunityConformanceRunner`) supporting request/response assertions, timeout simulation, failure injection, and link teardown testing without physical Bluetooth.

### OB-P43-REQ-007: Reference Community Integration
The SDK shall include a clean, deterministic reference adapter (`AcmeBudsCommunityAdapter`) demonstrating capability declarations, matching, packet parsing, and error mapping using synthetic fixtures.

---

## 2. Security and Architectural Requirements

### OB-P43-SEC-001: No Bypass of Centralized Authorization
All community adapter actions shall be gated by the host application's `DeviceAccessPolicy`. No adapter shall execute raw Bluetooth commands directly.

### OB-P43-SEC-002: No Dynamic Code Loading
Community integrations are statically compiled and reviewed Kotlin components. Dynamic bytecode downloading and execution over network or filesystem is permanently prohibited.

### OB-P43-ARCH-001: Strict Downward Layering
The SDK area (`com.omnibuds.core.sdk`) sits at Layer 6, depending downward on vendor contracts (Layer 5), protocol (Layer 4), capability (Layer 2), device (Layer 2), security (Layer 1), transport (Layer 1), state (Layer 0), and common (Layer 0). Internal execution engines are never imported sideways.
