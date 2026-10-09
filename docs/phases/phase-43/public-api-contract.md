# Phase 43 — Public SDK Contract

**Package:** `com.omnibuds.core.sdk.api`

---

## 1. Overview

The Community Protocol SDK contract defines the public surface exposed to external contributors creating protocol integrations for compatible earbuds and headphones.

---

## 2. Core Interfaces and Data Types

### 2.1 `CommunityProtocolAdapter`
Extends `VendorAdapter`:
- `adapterId: String`: Unique namespaced identifier (`namespace.name`).
- `displayName: String`: Human-readable adapter title.
- `sdkVersion: SdkVersion`: Semantic version of SDK against which the adapter was compiled.
- `integrationVersion: String`: Version of this community contribution.
- `evidenceRecord: CommunityEvidenceRecord`: Author provenance, review dates, and evidence claims.
- `declaredCapabilities: List<CommunityCapabilityDeclaration>`: Declared features, claimed rungs, and operational constraints.
- `declaredOperations: List<CommunityOperationDefinition>`: Operations supported by this adapter.
- `match(fingerprint: DeviceFingerprint): MatchResult`: Match logic against observed device metadata.
- `parseInboundPayload(operationId: String, payload: ByteArray): SdkOperationResult<Map<String, String>>`: Parse raw wire packets into typed key-value observations.
- `encodeOutboundCommand(operationId: String, parameters: Map<String, String>): SdkOperationResult<ByteArray>`: Encode structured request parameters into wire bytes.

---

## 3. Operational Guarantees

- **No Direct Hardware Access:** The adapter never handles Bluetooth sockets, GATT handles, or Android platform types.
- **Fail-Closed Semantics:** Malformed payloads or unsupported commands return structured typed results (`SdkOperationResult.MalformedData`, `SdkOperationResult.Unsupported`).
