# Phase 52 — Specifications: Schemas, Gate Rules & Evidence Contracts

## 1. Test-Case Schema

Every test case executed or recorded during Phase 52 conforms to the following schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "TestCaseRecord",
  "type": "object",
  "required": [
    "testId",
    "requirementId",
    "platform",
    "classification",
    "preconditions",
    "procedure",
    "expectedResult",
    "actualResult",
    "evidenceSource",
    "status"
  ],
  "properties": {
    "testId": { "type": "string", "pattern": "^TC-52-[0-9]{3}$" },
    "requirementId": { "type": "string", "pattern": "^REQ-52-[0-9]{3}$" },
    "platform": { "type": "string", "enum": ["core", "android", "desktop", "all"] },
    "deviceOrProfile": { "type": "string" },
    "firmwareOrOsVersion": { "type": "string" },
    "classification": {
      "type": "string",
      "enum": [
        "UNIT_TESTED",
        "INTEGRATION_TESTED",
        "UI_TESTED",
        "PLATFORM_TESTED",
        "SIMULATED",
        "HARDWARE_VERIFIED",
        "PERSISTENCE_VERIFIED",
        "BLOCKED",
        "UNSUPPORTED",
        "NOT_TESTED"
      ]
    },
    "preconditions": { "type": "string" },
    "procedure": { "type": "string" },
    "expectedResult": { "type": "string" },
    "actualResult": { "type": "string" },
    "evidenceSource": { "type": "string" },
    "defectReference": { "type": ["string", "null"] },
    "status": {
      "type": "string",
      "enum": ["PASSED", "FAILED", "SKIPPED", "BLOCKED", "CANCELLED", "INVALID", "INFRASTRUCTURE_ERROR"]
    }
  }
}
```

---

## 2. Hardware Verification & Evidence Record Schema

Physical hardware verification records must capture exact device identities and real measurements:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "HardwareVerificationRecord",
  "type": "object",
  "required": [
    "recordId",
    "deviceModel",
    "hardwareEvidenceLevel",
    "hostOperatingSystem",
    "applicationRevision",
    "capabilityTested",
    "verificationTrail",
    "readBackEvidence",
    "persistenceScope",
    "verificationVerdict"
  ],
  "properties": {
    "recordId": { "type": "string" },
    "deviceModel": { "type": "string" },
    "hardwareRevision": { "type": ["string", "null"] },
    "firmwareVersion": { "type": ["string", "null"] },
    "hostOperatingSystem": { "type": "string" },
    "applicationRevision": { "type": "string" },
    "capabilityTested": { "type": "string" },
    "hardwareEvidenceLevel": {
      "type": "string",
      "enum": ["INFERRED", "IMPLEMENTED", "LAB_TESTED", "HARDWARE_VERIFIED", "PERSISTENCE_VERIFIED"]
    },
    "verificationTrail": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Trail of steps executed: DISCOVER, IDENTIFY, READ, UNDERSTAND, VALIDATE, WRITE, READ_BACK, DISCONNECT, RECONNECT, VERIFY"
    },
    "readBackEvidence": { "type": "string" },
    "persistenceScope": {
      "type": "string",
      "enum": ["NONE", "LOCAL_SESSION", "RECONNECT", "POWER_CYCLE", "CROSS_APP_RESTART"]
    },
    "verificationVerdict": {
      "type": "string",
      "enum": ["VERIFIED", "NOT_VERIFIED", "UNSUPPORTED", "BLOCKED_NO_ENVIRONMENT"]
    }
  }
}
```

---

## 3. Defect Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "DefectRecord",
  "type": "object",
  "required": [
    "defectId",
    "severity",
    "affectedPlatform",
    "affectedModule",
    "summary",
    "reproductionSteps",
    "expectedBehavior",
    "actualBehavior",
    "evidence",
    "status"
  ],
  "properties": {
    "defectId": { "type": "string", "pattern": "^DEF-52-[0-9]{3}$" },
    "severity": { "type": "string", "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW"] },
    "affectedPlatform": { "type": "string", "enum": ["core", "android", "desktop", "all"] },
    "affectedModule": { "type": "string" },
    "summary": { "type": "string" },
    "reproductionSteps": { "type": "string" },
    "expectedBehavior": { "type": "string" },
    "actualBehavior": { "type": "string" },
    "evidence": { "type": "string" },
    "rootCause": { "type": ["string", "null"] },
    "remediation": { "type": ["string", "null"] },
    "regressionTest": { "type": ["string", "null"] },
    "status": {
      "type": "string",
      "enum": ["OPEN", "INVESTIGATING", "RESOLVED", "VERIFIED", "ACCEPTED_LIMITATION", "WONT_FIX"]
    }
  }
}
```

---

## 4. Release Manifest Specification (v1 Schema)

The generated `release-manifest.json` conforms to schema v1:
- `schemaVersion`: Integer (1).
- `productName`: "OmniBuds".
- `applicationVersion`: "1.0.0".
- `versionCode`: 1000000.
- `gitRevision`: Commit hash (7-char short or full).
- `buildTimestamp`: ISO-8601 UTC timestamp.
- `artifacts`: Array of package metadata entries, each containing `productName`, `applicationVersion`, `platform`, `architecture`, `filename`, `format`, `sizeBytes`, `sha256Checksum`, `signingStatus`, and `validationStatus`.
- `releaseGates`: Key-value object mapping each mandatory gate to its evaluated status (`PASSED`, `BLOCKED_NO_KEYS`, `NOT_VERIFIED`, etc.).

---

## 5. Gate Evaluation Rules

A gate is evaluated strictly according to its objective proof criteria:
- **Rule 1 (Proof Required)**: A gate may only be marked `PASSED` if authoritative machine output or cryptographic proof demonstrates completion.
- **Rule 2 (No Conflation)**: Automated test execution on mocks/fakes never satisfies a hardware gate.
- **Rule 3 (Missing Keys)**: In the absence of private production keys, the signing gate must be marked `BLOCKED_NO_KEYS`. Unsigned artifacts must be explicitly denoted.
- **Rule 4 (Missing Environment)**: When hardware or OS runners are missing, the gate must be evaluated as `NOT_VERIFIED (BLOCKED_NO_ENVIRONMENT)`.

---

## 6. Release Outcome Definitions

1. **GO**:
   - Every mandatory software, security, packaging, and hardware gate has passed with complete physical proof.
   - Requires explicit distribution authorization.
2. **CONDITIONAL GO**:
   - All software regression, platform compilation, artifact packaging, integrity checksums, security default-deny, and redaction gates have passed (100%).
   - Hardware-dependent verification is honestly classified as `NOT VERIFIED` due to environment constraints.
   - Artifacts are approved as a verifiable Release Candidate (RC1) for staging / lab deployment.
3. **NO-GO**:
   - Any automated software test fails.
   - Any critical/high security vulnerability remains unresolved.
   - An artifact checksum or format is corrupted or missing.
   - Unverified hardware claims are falsely promoted to production status.
