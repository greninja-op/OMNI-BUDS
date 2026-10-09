# Phase 44 — Architecture Decision Records (ADRs)

---

## ADR-P44-001: Strong Typing for Disparate Version Domains
- **Context:** The codebase previously used plain `String?` for protocol version and firmware version, and `Int` for schema version and SDK version. This risked accidental interchange or inappropriate string comparison.
- **Decision:** Introduce dedicated typed classes: `ProtocolVersion` (with subclasses `Semantic`, `IntegerRevision`, `VendorDefined`, `Unknown`), `ProtocolSchemaVersion`, and strong constraint models.
- **Consequences:** Eliminates accidental conflation; enables compiler-checked handling of unknown versions and custom schemes.

---

## ADR-P44-002: Deterministic Incompatibility and No Arbitrary Winner
- **Context:** When multiple protocol implementations match a device fingerprint or when version evidence is conflicting/sparse, naive resolvers might select the newest version or the first registered candidate.
- **Decision:** Mandate that if multiple candidates match equally without disambiguating evidence, `CompatibilityStatus.AMBIGUOUS` is returned with `selected = null`. Never pick the newest version on faith.
- **Consequences:** Avoids bricking devices with speculative newer protocol packets.

---

## ADR-P44-003: Safe Non-Mutating Fallbacks
- **Context:** An unknown protocol version should not necessarily drop basic Bluetooth audio or battery observation if safe standard profiles exist.
- **Decision:** Unknown, ambiguous, or incompatible protocol resolutions strictly deny mutating operations (`canAuthorizeMutatingOperations == false`). Read-only telemetry is allowed only when standard, non-proprietary channels exist and policy permits.
- **Consequences:** Protects hardware integrity while preserving user visibility.

---

## ADR-P44-004: Version-Preserving Metadata Schema Evolution
- **Context:** Metadata describing protocol definitions evolves as new features or constraints are added.
- **Decision:** Version metadata records with `ProtocolSchemaVersion`. Migrations must be pure, transactional, and preserve evidence provenance IDs and review statuses without loss.
- **Consequences:** Ensures auditability and zero silent corruption of historical records.
