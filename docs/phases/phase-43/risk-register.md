# Phase 43 — Risk Register: Community Protocol SDK

| Risk ID | Description | Severity | Likelihood | Mitigation Strategy | Status |
|---|---|---|---|---|---|
| **RSK-P43-001** | Untrusted community code attempting direct or raw Bluetooth socket control | High | Low | Community adapters implement serialization/parsing only; `DeviceAccessPolicy` gates all physical transport calls. | Mitigated |
| **RSK-P43-002** | Contributor claiming false hardware verification in metadata | High | Medium | Invariant enforced in `CommunityEvidenceRecord` and `CommunityCapabilityDeclaration` constructors and validated by `CommunityPackageValidator`. | Mitigated |
| **RSK-P43-003** | Malicious or infinite execution loops during package inspection | High | Low | Package validator inspects pure data structures (`CommunityMetadataPackage`) statically without executing adapter code. | Mitigated |
| **RSK-P43-004** | Breaking SDK API changes affecting external integrations | Medium | Medium | Versioned `SdkVersion` with semantic versioning enforcement rejecting unsupported major versions. | Mitigated |
| **RSK-P43-005** | Upward or sideways architectural leakage across `:core` | Medium | Low | Enforced mechanically by `DependencyDirectionTest` with `sdk` positioned at Layer 6. | Mitigated |
| **RSK-P43-006** | Magic bytes and hex literals creeping into adapter code | Low | Low | Required loading of assigned constants from JSON data resources (`AcmeFixtureData`). | Mitigated |
