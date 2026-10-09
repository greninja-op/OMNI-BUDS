# Phase 43 — Community Contribution Guide

---

## 1. How to Contribute a Protocol Integration

External contributors can develop new protocol integrations by following these steps:

### Step 1: Implement `CommunityProtocolAdapter`
- Create a class implementing `CommunityProtocolAdapter`.
- Provide `adapterId`, `displayName`, `sdkVersion = SdkVersion.CURRENT`, `integrationVersion`.
- Define an honest `CommunityEvidenceRecord` detailing your research source, models, and limitations.

### Step 2: Declare Capabilities and Operations
- Declare capabilities using `FeatureId.ofVendor("<vendor>", "<feature>")`.
- State whether each capability is `READ_ONLY` or `MUTATING`.
- Do not claim `PERSISTENCE_VERIFIED` in metadata.

### Step 3: Implement Packet Serialization
- Implement `parseInboundPayload` and `encodeOutboundCommand`.
- Always handle malformed input gracefully by returning `SdkOperationResult.MalformedData`.

### Step 4: Validate Offline
- Run `CommunityPackageValidator.validate(pkg)` against your metadata package.
- Run `CommunityConformanceRunner.verifyAdapter(adapter, sampleFingerprint)`.
- Write unit tests exercising your adapter against `ScriptedFakeTransport`.

### Step 5: Submit for Review
- Submit your adapter along with offline test fixtures.
- The OmniBuds maintainers will audit your evidence and run regression test suites before admitting the adapter into the vendor registry.
