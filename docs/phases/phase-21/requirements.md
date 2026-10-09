# Phase 21 — Unknown Device / Read-Only Mode: Requirements

**Status:** Authoritative for Phase 21 execution.
**Scope:** Device classification, centralized default-deny access policy,
read-only observation engine, capability-state discipline, lifecycle
integration. Unknown devices are useful to observe, never permitted to
control. No UI, no Phase 22, no physical hardware.
**Requirement ID scheme:** `OB-P21-REQ-001` … `OB-P21-REQ-028`.

## OB-P21-REQ-001 — Classification model
- **Description:** Typed `DeviceClassification`: UNKNOWN_DEVICE,
  PARTIALLY_IDENTIFIED, IDENTIFIED_UNSUPPORTED, AMBIGUOUS_IDENTITY,
  KNOWN_PROTOCOL_UNVERIFIED, KNOWN_PROTOCOL_SUPPORTED, KNOWN_DEVICE_SUPPORTED.
  Identity confidence, protocol support, and write authorization are separate fields.
- **Priority:** Must | **Verification:** `DeviceClassificationTest`.

## OB-P21-REQ-002 — Deterministic classification
- **Description:** Pure function of fingerprint + registry state. Same inputs → same classification.
- **Priority:** Must | **Verification:** `DeviceClassificationTest`.

## OB-P21-REQ-003 — Ambiguity preserved
- **Description:** Multiple plausible identities → AMBIGUOUS_IDENTITY, never first-candidate-wins.
- **Priority:** Must | **Verification:** `DeviceClassificationTest`.

## OB-P21-REQ-004 — Central access policy
- **Description:** `DeviceAccessPolicy.evaluate(...)` at the domain/control boundary. Default-deny.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-005 — Operation categories
- **Description:** Discovery, identity/connection/metadata observation, parsing, capability inspection, hardware read, hardware write, config reset, firmware update, raw transport write — each with explicit rules.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-006 — Default-deny for unknown
- **Description:** Unknown → reads via legitimate interfaces only; vendor reads need verified protocol; writes/reset/firmware/raw denied.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-007 — Per-operation evaluation
- **Description:** Observing one property never authorizes reading/writing another.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-008 — Fail-closed
- **Description:** Missing/ambiguous identity, missing protocol, version mismatch, unknown capability, stale evidence → deny.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-009 — Typed denials
- **Description:** Allowed/denied + reason code + classification + capability/operation IDs + missing evidence + read-only flag + re-evaluation hint. No sensitive payloads in messages.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-010 — Read-only observation engine
- **Description:** Observations with id, device ref, property, typed value, source, timestamp, freshness, confidence, transport/protocol, session, limitations. Unknown stays null/unknown.
- **Priority:** Must | **Verification:** `ObservationEngineTest`.

## OB-P21-REQ-011 — No fabricated defaults
- **Description:** Unknown battery ≠ 0%, unknown ANC ≠ NORMAL, unknown codec ≠ SBC. Never infer from display name.
- **Priority:** Must | **Verification:** `ObservationEngineTest`.

## OB-P21-REQ-012 — OS observation boundaries
- **Description:** Report what Android exposes; leave the rest unknown. No mic permission for non-mic observations. Paired ≠ carrying media.
- **Priority:** Must | **Verification:** Review.

## OB-P21-REQ-013 — Capability states
- **Description:** Unknown/unsupported/read-only/supported-unavailable/supported-available/verified-write/read-back-verified/persistence — distinct, preserved.
- **Priority:** Must | **Verification:** `CapabilityStateTest`.

## OB-P21-REQ-014 — Capability rules
- **Description:** 10 rules from §7 (unknown≠unsupported, parser≠write auth, synthetic≠capability, etc.).
- **Priority:** Must | **Verification:** `CapabilityStateTest`.

## OB-P21-REQ-015 — Lab integration
- **Description:** Lab schemas never bypass access policy; lab data never auto-executes; observations isolated from control sessions.
- **Priority:** Must | **Verification:** `LabIntegrationTest`.

## OB-P21-REQ-016 — Lifecycle transitions
- **Description:** Deterministic classification changes on new/stale evidence; re-evaluate affected operations; never auto-execute previously denied writes.
- **Priority:** Must | **Verification:** `LifecycleTest`.

## OB-P21-REQ-017 — Stale invalidation
- **Description:** Stale evidence never reused as current; incompatible in-flight ops cancelled.
- **Priority:** Must | **Verification:** `LifecycleTest`.

## OB-P21-REQ-018 — Multi-device isolation
- **Description:** Per-device classification and policy state; no cross-device leakage.
- **Priority:** Must | **Verification:** `ConcurrencyTest`.

## OB-P21-REQ-019 — Concurrency
- **Description:** Out-of-order evidence, connection races, concurrent observations, duplicate events handled deterministically.
- **Priority:** Must | **Verification:** `ConcurrencyTest`.

## OB-P21-REQ-020 — Diagnostics
- **Description:** Bounded, typed reason-code events (classification, ambiguity, denial, staleness). No credentials, payloads, or unnecessary addresses.
- **Priority:** Must | **Verification:** `DiagnosticsTest`.

## OB-P21-REQ-021 — Architecture
- **Description:** `core.access` at layer 5; reuses fingerprint, capability, protocol types; no sideways imports.
- **Priority:** Must | **Verification:** Architecture test.

## OB-P21-REQ-022 — No parallel systems
- **Description:** Extend existing models; don't build a second device manager or weaken protocol safety.
- **Priority:** Must | **Verification:** Review.

## OB-P21-REQ-023 — Documentation
- **Description:** 11 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P21-REQ-024 — Regression
- **Description:** Phases 5–20 compatible; unknown devices discoverable; no bypass via direct feature-engine calls.
- **Priority:** Must | **Verification:** Full test run.

## OB-P21-REQ-025 — No UI / Phase 22
- **Description:** No UI, Phase 22 not started.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P21-REQ-026 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P21-REQ-027 — Read permission ≠ write permission
- **Description:** Verified read never implicitly enables write.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.

## OB-P21-REQ-028 — Connection ≠ control authorization
- **Description:** Pairing/connection never authorizes proprietary control.
- **Priority:** Must | **Verification:** `AccessPolicyTest`.
