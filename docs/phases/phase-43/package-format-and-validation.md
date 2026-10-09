# Phase 43 — Package Format and Validation

---

## 1. Package Structure

Community protocol integrations submit metadata packaged as `CommunityMetadataPackage`:
- `adapterId`: Stable unique ID matching `^[a-z0-9]+(\.[a-z0-9_-]+)+$`.
- `displayName`: Human-readable integration title.
- `sdkVersion`: Semantic version string.
- `integrationVersion`: Contributor version string.
- `evidenceRecord`: Complete `CommunityEvidenceRecord`.
- `capabilities`: List of `CommunityCapabilityDeclaration`.
- `operations`: List of `CommunityOperationDefinition`.

---

## 2. Static Validation Pipeline

`CommunityPackageValidator.validate(pkg)` executes 6 sequential verification passes:

1. **Identifier Validation (`SDK-VAL-001`, `SDK-VAL-002`):** Non-blank, namespaced syntax.
2. **Display Name (`SDK-VAL-003`):** Non-blank.
3. **SDK Compatibility (`SDK-VAL-004`, `SDK-VAL-005`):** Valid semver, matching major version.
4. **Evidence & Provenance (`SDK-VAL-006` - `SDK-VAL-008`):** Matching integrationId, max level <= `LAB_TESTED`.
5. **Capability Integrity (`SDK-VAL-009` - `SDK-VAL-012`):** No duplicates, no self-promoted persistence, no self-dependencies, no self-conflicts.
6. **Operation Integrity (`SDK-VAL-013`, `SDK-VAL-014`):** No duplicate operation IDs, all operations map to declared features.
