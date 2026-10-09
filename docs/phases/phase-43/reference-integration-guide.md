# Phase 43 — Reference Integration Guide

---

## 1. Reference Implementation: Acme Buds

The reference implementation `AcmeBudsCommunityAdapter` is located at `com.omnibuds.core.sdk.examples.AcmeBudsCommunityAdapter`.

### Key Features Demonstrated:
1. **Metadata & Evidence:**
   - Defines adapter ID `community.acme.buds`.
   - Attaches `CommunityEvidenceRecord` pinned to `SdkVersion.CURRENT`.
   - Maximum verification tier `LAB_TESTED`.

2. **Data-Driven Protocol Constants:**
   - Protocol headers (1 for battery query, 2 for ANC command) and synthetic company ID (65534 / `0xFFFE`) are loaded from `omnibuds/sdk/acme/acme-fixture.json` via `AcmeFixtureData`.
   - Adheres strictly to the architectural rule forbidding hardcoded protocol hex literals in source code.

3. **Deterministic Device Matching:**
   - Evaluates `DeviceFingerprint.manufacturerData` for company ID 65534.
   - Ambiguous or unobserved devices yield `MatchResult.NotMatched`.

4. **Payload Encoding and Decoding:**
   - `encodeOutboundCommand("acme.set_anc", mapOf("mode" to "on"))` outputs structured packet bytes.
   - `parseInboundPayload("acme.set_anc", payload)` returns typed `SdkOperationResult.Success(mapOf("status" to "success"))`.
   - Empty or malformed payloads return `SdkOperationResult.MalformedData`.
