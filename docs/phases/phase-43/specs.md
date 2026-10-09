# Phase 43 — Specifications: Community Protocol SDK

**Status:** APPROVED  

---

## 1. Type Specifications

### 1.1 `SdkVersion`
- Immutable value class with `major: Int`, `minor: Int`, `patch: Int`.
- `major >= 1`, `minor >= 0`, `patch >= 0`.
- Standard `Comparable<SdkVersion>` implementation.
- Parses string format `"X.Y.Z"`.

### 1.2 `SdkOperationResult<out T>`
- `Success(val value: T)`
- `Unsupported(val reason: String)`
- `IncompatibleDevice(val reason: String)`
- `InsufficientEvidence(val reason: String)`
- `AuthorizationDenied(val reason: String)`
- `MalformedData(val detail: String)`
- `Timeout(val timeoutMillis: Long)`
- `Cancelled(val reason: String)`
- `TransportFailure(val detail: String)`

### 1.3 `CommunityEvidenceRecord`
- Holds mandatory fields: `contributorId`, `integrationId`, `integrationVersion`, `sdkVersion`, `protocolVersion`, `supportedModels`, `verifiedFirmware`, `evidenceSource`, `declaredVerificationLevel`, `knownLimitations`, `testFixtureReferences`, `lastReviewedDate`.
- Invariant: `declaredVerificationLevel <= VerificationLevel.LAB_TESTED`.

### 1.4 `CommunityCapabilityDeclaration`
- `featureId: FeatureId`
- `claimedState: CapabilityState` (cannot be `PERSISTENCE_VERIFIED`)
- `tier: OperationTier` (`READ_ONLY` or `MUTATING`)
- `requiredTransport: TransportKind` (cannot be `UNKNOWN`)
- `timeoutMillis: Long` (> 0)
- `dependencies: Set<FeatureId>`
- `conflicts: Set<FeatureId>`
- `notes: String`

### 1.5 `CommunityPackageValidator` Rules
- `SDK-VAL-001`: Blank adapterId rejected.
- `SDK-VAL-002`: AdapterId regex format `^[a-z0-9]+(\.[a-z0-9_-]+)+$` enforced.
- `SDK-VAL-003`: Blank displayName rejected.
- `SDK-VAL-004`: Invalid semver format rejected.
- `SDK-VAL-005`: Incompatible SDK major version rejected.
- `SDK-VAL-006`: Mismatched integrationId in evidence record rejected.
- `SDK-VAL-007`: Evidence level > `LAB_TESTED` rejected.
- `SDK-VAL-008`: Warning on empty supportedModels.
- `SDK-VAL-009`: Duplicate capability declaration rejected.
- `SDK-VAL-010`: Declared `PERSISTENCE_VERIFIED` state rejected.
- `SDK-VAL-011`: Circular self-dependency rejected.
- `SDK-VAL-012`: Self-conflict rejected.
- `SDK-VAL-013`: Duplicate operationId rejected.
- `SDK-VAL-014`: Operation targeting undeclared feature rejected.
