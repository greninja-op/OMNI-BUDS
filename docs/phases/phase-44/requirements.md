# Phase 44 — Requirements: Protocol Versioning & Compatibility Management

---

## 1. Functional Requirements

- **OB-P44-REQ-001 (Version Domain Separation):** The system shall strictly separate Protocol Version, Protocol Schema Version, Firmware Version, SDK Version, Integration Version, and Application Version into distinct types.
- **OB-P44-REQ-002 (Canonical Protocol Identity):** The system shall model protocol identity with protocol ID, namespace, version scheme, schema version, transport constraints, firmware constraints, and verification status.
- **OB-P44-REQ-003 (Multiple Protocol Schemes):** The system shall support SemVer, integer revisions, vendor-defined named revisions, and explicitly unknown versions without forcing artificial SemVer on non-SemVer protocols.
- **OB-P44-REQ-004 (Deterministic Compatibility Resolution):** The compatibility resolver shall evaluate identity, firmware, observed protocol revision, transport, and constraints, returning deterministic outcomes (`COMPATIBLE`, `COMPATIBLE_WITH_LIMITATIONS`, `INCOMPATIBLE`, `UNKNOWN_VERSION`, `AMBIGUOUS`, `INSUFFICIENT_EVIDENCE`, `UNSUPPORTED_SCHEMA`, `BLOCKED_BY_POLICY`).
- **OB-P44-REQ-005 (Safe Unknown & Incompatible Handling):** Incompatible or unknown protocol versions shall never authorize mutating operations. Safe read-only fallbacks are permitted only when justified by existing policy.
- **OB-P44-REQ-006 (No Arbitrary Winner):** When multiple protocol candidates match equally without disambiguating evidence, resolution shall result in `AMBIGUOUS` and select no candidate.
- **OB-P44-REQ-007 (Versioned Schema Evolution & Migration):** Protocol metadata definitions stored in databases or repositories shall carry schema versions, and schema migrations shall be transactional, idempotent, and preserve evidence provenance.
- **OB-P44-REQ-008 (Anti-Self-Promotion & Security):** Adapters and community packages shall not self-promote hardware verification levels. Unknown protocol versions must never bypass centralized authorization.
- **OB-P44-REQ-009 (Version-Dependent Message Contracts):** Versioned protocol message encoders and decoders shall isolate wire differences without guessing fields or breaking on malformed frames.
- **OB-P44-REQ-010 (Offline Deterministic Testing):** All unit and integration test suites shall run offline against synthetic fixtures and test doubles without connecting to physical devices.
