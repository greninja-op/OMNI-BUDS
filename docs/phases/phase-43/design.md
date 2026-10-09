# Phase 43 — Design: Community Protocol SDK

**Status:** APPROVED  
**Package:** `com.omnibuds.core.sdk`

---

## 1. Architectural Boundaries

The Community Protocol SDK provides a versioned boundary between external protocol contributions and OmniBuds core internals.

```
+-------------------------------------------------------------+
|              Community Protocol Integrations                |
|           (e.g., AcmeBudsCommunityAdapter)                 |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                   com.omnibuds.core.sdk                     |
|  +---------------------+  +-------------------------------+  |
|  |       sdk.api       |  |        sdk.validation         |  |
|  | - SdkVersion        |  | - CommunityMetadataPackage    |  |
|  | - SdkOperationResult|  | - CommunityPackageValidator   |  |
|  | - EvidenceRecord    |  | - ValidationReport            |  |
|  | - CapabilityDecl    |  +-------------------------------+  |
|  | - OperationDef      |  +-------------------------------+  |
|  | - ProtocolAdapter   |  |          sdk.testing          |  |
|  +---------------------+  | - ScriptedFakeTransport       |  |
|                           | - CommunityConformanceRunner  |  |
|                           +-------------------------------+  |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                     OmniBuds Core                           |
|  Layer 5: VendorAdapter, VendorRegistry                     |
|  Layer 4: ProtocolDefinition                                |
|  Layer 2: CapabilityState, DeviceFingerprint, FeatureId     |
|  Layer 1: TransportContract, TransportRequest/Response      |
|  Layer 0: OperationOutcome, OmniBudsError, VerificationLevel|
+-------------------------------------------------------------+
```

---

## 2. Key Design Decisions

1. **Adapter Extension Model:**
   `CommunityProtocolAdapter` implements `VendorAdapter`. This allows community adapters to register directly into `VendorRegistry` and resolve through `VendorResolver` without creating a duplicate registration or routing subsystem.

2. **Data-Driven Protocol Constants:**
   Per ADR-P0-003 and master section 52, hex literals representing protocol headers and company identifiers are forbidden in source code. Reference adapters load assigned constants from packaged JSON resource data files (`AcmeFixtureData`).

3. **Static Inspection Without Execution:**
   `CommunityPackageValidator` accepts a declarative `CommunityMetadataPackage`. It performs structural, lexical, and relationship sanity checks without invoking adapter methods or executing untrusted bytecode.

4. **Testing Without Hardware:**
   `ScriptedFakeTransport` implements `TransportContract` with explicit step queues (`SdkFakeStep.Respond`, `SdkFakeStep.Fail`, `SdkFakeStep.Disconnect`), providing deterministic verification of timeouts, disconnections, and command sequencing.
