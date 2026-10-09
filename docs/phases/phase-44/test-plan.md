# Phase 44 — Test Plan: Protocol Versioning & Compatibility

---

## 1. Test Suite Structure

Tests are organized under `com.omnibuds.core.protocol.version`:

### 1. Version Parsing & Ordering Tests (`ProtocolVersionTest`)
- Semantic version parsing, edge cases, boundaries (negative, missing parts).
- Integer revision parsing and validation.
- Vendor-defined token parsing and non-comparability.
- Unknown version sentinel behavior.
- Comparison rules: numeric vs lexicographical ordering (e.g. `1.10.0` > `1.9.0`, rev `10` > rev `9`).
- Cross-scheme comparison behavior (deterministic non-throw, order by scheme discriminator).

### 2. Constraint Evaluation Tests (`VersionConstraintTest`)
- Exact matching on identical version.
- Semantic range inclusive boundaries.
- Integer revision range matching.
- Enumerated set membership.
- Rejection of mismatched version schemes.
- Rejection of unknown versions across strict constraints.

### 3. Compatibility Resolver Tests (`CompatibilityResolverTest`)
- Single matching candidate returns `COMPATIBLE`.
- Version constraint mismatch returns `INCOMPATIBLE`.
- Missing device version returns `UNKNOWN_VERSION`.
- Transports mismatch returns `UNSUPPORTED` or `INCOMPATIBLE`.
- Multiple equal candidates without disambiguator returns `AMBIGUOUS` (no arbitrary winner).
- Weak device name match does not infer specific protocol version.
- Unknown / incompatible / ambiguous outcomes block mutating operations.

### 4. Implementation Registry Tests (`ProtocolVersionRegistryTest`)
- Multi-revision registration under same vendor namespace.
- Rejection of duplicate registration identifiers.
- Rejection of conflicting version constraints.
- Correct retrieval of candidates given device fingerprint and observed version.

### 5. Schema Evolution & Migration Tests (`ProtocolSchemaMigrationTest`)
- Migration from Schema v1 to v2 preserving evidence and metadata.
- Rejection of unsupported schema versions.
- Transactional rollback on corrupted migration step.
- Idempotent migration reruns.

### 6. Versioned Message Codec Tests (`VersionedMessageCodecTest`)
- Version 1 vs Version 2 packet encoding and decoding.
- Detection of malformed, truncated, or unknown opcodes.
- Isolation of wire formats across versions.

### 7. Security & Authorization Safeguards (`ProtocolVersionSecurityTest`)
- Unknown version cannot authorize write / mutating operations.
- Ambiguous match cannot select default adapter.
- Community adapter cannot self-promote to `HARDWARE_VERIFIED`.
- Compatibility check failure cannot silently fall back to another vendor.

### 8. Architectural Integrity (`DependencyDirectionTest`)
- Core layer 4 compliance.
- Zero Android dependencies in `:core`.
- Zero magic hex literals.
