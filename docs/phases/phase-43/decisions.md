# Phase 43 — Architecture Decisions: Community Protocol SDK

---

## ADR-P43-001: Separation of Community SDK into Layer 6
- **Context:** The Community Protocol SDK needs to reference `VendorAdapter` (Layer 5), `ProtocolDefinition` (Layer 4), `CapabilityState` (Layer 2), `TransportContract` (Layer 1), and `OperationOutcome` (Layer 0).
- **Decision:** Place the `sdk` package at Layer 6 in `:core`. Dependencies point strictly downward. Internal execution engines (such as `feature`, `battery`, `access`, `globalstate`) are not imported sideways.
- **Consequences:** Clean separation between the community contract and internal execution machinery.

---

## ADR-P43-002: Static Package Validation Without Code Execution
- **Context:** Community-contributed packages must be inspected before admission to verify metadata, claimed rungs, and dependency graphs.
- **Decision:** `CommunityPackageValidator` operates exclusively on declarative metadata structures (`CommunityMetadataPackage`). It never executes adapter bytecode during validation.
- **Consequences:** Eliminates code execution vulnerabilities during contribution scanning.

---

## ADR-P43-003: Anti-Self-Promotion Invariant
- **Context:** Untrusted contributors might claim `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED` within metadata to bypass host verification gates.
- **Decision:** Both `CommunityEvidenceRecord` and `CommunityCapabilityDeclaration` reject declarations with verification tiers above `LAB_TESTED` or states equal to `PERSISTENCE_VERIFIED`.
- **Consequences:** Only the OmniBuds host application running real verification flows (deferred to Phase 52) can promote a capability to hardware-verified status.

---

## ADR-P43-004: Loading Protocol Constants from Packaged JSON
- **Context:** Architecture rule `coreContainsNoHardCodedProtocolLiterals` forbids hex literals (opcodes, company IDs) in production Kotlin code.
- **Decision:** Synthetic reference adapters load protocol constants and company identifiers at runtime from packaged JSON resources (`AcmeFixtureData`).
- **Consequences:** Full compliance with ADR-P0-003 and master section 52.
