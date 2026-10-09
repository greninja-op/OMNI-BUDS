# Phase 43 — Evidence and Provenance Model

---

## 1. Provenance Requirements

Every community contribution must declare transparent authorship and evidence grounding in `CommunityEvidenceRecord`.

### 1.1 Field Requirements
- `contributorId`: Unique contributor identifier or handle.
- `integrationId`: Must match `adapterId` exactly.
- `integrationVersion`: Semantic version of the contribution.
- `sdkVersion`: Pinned SDK contract version.
- `protocolVersion`: Vendor protocol version described.
- `supportedModels`: Explicit list of supported device models.
- `verifiedFirmware`: Set of verified firmware versions or null if firmware-independent.
- `evidenceSource`: Citations to official documentation, reverse-engineering captures, or synthetic test fixtures.
- `declaredVerificationLevel`: Maximum permitted value is `VerificationLevel.LAB_TESTED`.
- `knownLimitations`: Explicit list of unsupported or unverified features.
- `testFixtureReferences`: Paths to offline recorded test fixtures.
- `lastReviewedDate`: ISO date (`YYYY-MM-DD`).

---

## 2. Invariant Rules

- **Zero Self-Promotion:** An integration passing mock unit tests is `LAB_TESTED`. It cannot claim `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`.
- **Honest Absence:** If a feature cannot be verified or controlled on standard Android Bluetooth stacks, it must be declared `UNSUPPORTED` or omitted.
